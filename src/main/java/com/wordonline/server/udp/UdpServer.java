package com.wordonline.server.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.auth.config.JwtProvider;
import com.wordonline.server.auth.domain.PrincipalDetails;
import com.wordonline.server.game.controller.InputController;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.dto.input.InputRequestDto;
import com.wordonline.server.session.service.SessionService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * The UDP side of the game channel. Clients that cannot use WebSocket (everything but WebGL) hand
 * their token over in a HELLO, then send inputs and receive frames on the same socket.
 *
 * <p>Inputs reach the game through {@link InputController#handleInput}, the same method a STOMP
 * SEND ends in, so authorization and queueing onto the loop thread are shared with WebSocket.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "game.udp", name = "enabled", havingValue = "true")
public class UdpServer {

    private record HelloRequest(String token, String sessionId, Long userId) {}

    private final UdpProperties properties;
    private final JwtProvider jwtProvider;
    private final SessionService sessionService;
    private final InputController inputController;
    private final UdpPeerRegistry registry;
    private final ObjectMapper objectMapper;

    private volatile DatagramSocket socket;
    private Thread receiver;

    public UdpServer(UdpProperties properties, JwtProvider jwtProvider, SessionService sessionService,
                     InputController inputController, UdpPeerRegistry registry, ObjectMapper objectMapper) {
        this.properties = properties;
        this.jwtProvider = jwtProvider;
        this.sessionService = sessionService;
        this.inputController = inputController;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() throws IOException {
        socket = new DatagramSocket(properties.port());
        receiver = new Thread(this::receiveLoop, "udp-game-receiver");
        receiver.setDaemon(true);
        receiver.start();
        log.info("[UDP] listening on {}", socket.getLocalPort());
    }

    @PreDestroy
    public void stop() {
        DatagramSocket current = socket;
        if (current != null) {
            current.close();
        }
    }

    public int localPort() {
        return socket.getLocalPort();
    }

    // ─── receive ─────────────────────────────────────────────────────────

    private void receiveLoop() {
        byte[] buffer = new byte[2048];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        DatagramSocket current = socket;
        while (!current.isClosed()) {
            try {
                datagram.setLength(buffer.length);
                current.receive(datagram);
                InetSocketAddress from = (InetSocketAddress) datagram.getSocketAddress();
                UdpPacket.decode(buffer, datagram.getLength()).ifPresent(packet -> handle(packet, from));
            } catch (IOException e) {
                if (!current.isClosed()) {
                    log.warn("[UDP] receive failed: {}", e.getMessage());
                }
            } catch (RuntimeException e) {
                // One bad datagram must not end the loop.
                log.warn("[UDP] dropped a packet: {}", e.toString());
            }
        }
    }

    void handle(UdpPacket packet, InetSocketAddress from) {
        switch (packet.type()) {
            case HELLO -> onHello(packet, from);
            case INPUT -> onInput(packet, from);
            case MESSAGE_ACK -> onMessageAck(packet, from);
            case BYE -> onBye(packet, from);
            default -> { }
        }
    }

    private void onHello(UdpPacket packet, InetSocketAddress from) {
        HelloRequest hello;
        try {
            hello = objectMapper.readValue(packet.body(), HelloRequest.class);
        } catch (IOException e) {
            reject(packet, from, "malformed");
            return;
        }
        if (hello.token() == null || hello.sessionId() == null || hello.userId() == null) {
            reject(packet, from, "malformed");
            return;
        }

        PrincipalDetails principal;
        try {
            Authentication authentication = jwtProvider.getAuthentication(hello.token());
            principal = (PrincipalDetails) authentication.getPrincipal();
        } catch (RuntimeException e) {
            reject(packet, from, "auth");
            return;
        }
        if (principal.getUid() == null || principal.getUid() != hello.userId().longValue()) {
            reject(packet, from, "auth");
            return;
        }

        SessionObject session = sessionService.getSessionObject(hello.sessionId());
        if (session == null || session.getUserSide(hello.userId()) == null) {
            reject(packet, from, "session");
            return;
        }

        UdpPeer peer = registry.bind(hello.sessionId(), hello.userId(), principal, from, System.nanoTime());
        transmit(from, new UdpPacket(UdpPacketType.HELLO_ACK, 0, packet.seq(), peer.connectionId(), new byte[0]).encode());
    }

    private void reject(UdpPacket hello, InetSocketAddress to, String reason) {
        log.debug("[UDP] hello from {} refused: {}", to, reason);
        byte[] body = reason.getBytes(StandardCharsets.UTF_8);
        transmit(to, new UdpPacket(UdpPacketType.HELLO_REJECT, 0, hello.seq(), 0L, body).encode());
    }

    private void onInput(UdpPacket packet, InetSocketAddress from) {
        UdpPeer peer = knownPeer(packet, from);
        if (peer == null) {
            return;
        }
        if (packet.isReliable()) {
            // Ack first and every time: a repeat means our earlier ack was lost.
            transmit(from, new UdpPacket(UdpPacketType.INPUT_ACK, 0, packet.seq(), peer.connectionId(), new byte[0]).encode());
            if (!peer.acceptInput(packet.seq())) {
                return;
            }
        }
        try {
            InputRequestDto input = objectMapper.readValue(packet.body(), InputRequestDto.class);
            inputController.handleInput(peer.sessionId(), peer.userId(), input, peer.principal());
        } catch (IOException e) {
            log.warn("[UDP] malformed input from user {}: {}", peer.userId(), e.getMessage());
        } catch (RuntimeException e) {
            // Same outcomes as over STOMP: an unauthorized input is refused, the rest is logged.
            log.warn("[UDP] input from user {} refused: {}", peer.userId(), e.toString());
        }
    }

    private void onMessageAck(UdpPacket packet, InetSocketAddress from) {
        UdpPeer peer = knownPeer(packet, from);
        if (peer != null) {
            peer.pending().remove(packet.seq());
        }
    }

    private void onBye(UdpPacket packet, InetSocketAddress from) {
        UdpPeer peer = knownPeer(packet, from);
        if (peer != null) {
            registry.remove(peer);
        }
    }

    /** The peer this packet claims to be from, or null when the id or the source address do not fit. */
    private UdpPeer knownPeer(UdpPacket packet, InetSocketAddress from) {
        UdpPeer peer = registry.byConnection(packet.connectionId());
        if (peer == null || !peer.address().equals(from)) {
            return null;
        }
        peer.touch(System.nanoTime());
        return peer;
    }

    // ─── send ────────────────────────────────────────────────────────────

    /** Sends a FrameInfo/SyncInfo. Not retransmitted: the next sync frame repairs any gap. */
    public void sendFrame(UdpPeer peer, byte[] payload) {
        sendPayload(peer, UdpPacketType.FRAME, false, payload);
    }

    /** Sends anything that must arrive; kept and resent until the client acks it. */
    public void sendMessage(UdpPeer peer, byte[] payload) {
        sendPayload(peer, UdpPacketType.MESSAGE, true, payload);
    }

    private void sendPayload(UdpPeer peer, UdpPacketType type, boolean reliable, byte[] payload) {
        int capacity = properties.maxDatagramBytes() - UdpPacket.HEADER_SIZE;
        int baseFlags = reliable ? UdpPacket.FLAG_RELIABLE : 0;

        if (payload.length <= capacity) {
            emit(peer, new UdpPacket(type, baseFlags, peer.nextSeq(type, 1), peer.connectionId(), payload));
            return;
        }

        int chunkSize = capacity - UdpPacket.FRAGMENT_PREFIX_SIZE;
        int count = (payload.length + chunkSize - 1) / chunkSize;
        if (count > 255) {
            log.error("[UDP] payload of {} bytes is too large to fragment, dropped", payload.length);
            return;
        }
        int base = peer.nextSeq(type, count);
        for (int index = 0; index < count; index++) {
            int from = index * chunkSize;
            int to = Math.min(payload.length, from + chunkSize);
            byte[] body = new byte[UdpPacket.FRAGMENT_PREFIX_SIZE + (to - from)];
            body[0] = (byte) index;
            body[1] = (byte) count;
            System.arraycopy(payload, from, body, UdpPacket.FRAGMENT_PREFIX_SIZE, to - from);
            emit(peer, new UdpPacket(type, baseFlags | UdpPacket.FLAG_FRAGMENT, base + index, peer.connectionId(), body));
        }
    }

    private void emit(UdpPeer peer, UdpPacket packet) {
        byte[] datagram = packet.encode();
        if (packet.isReliable()) {
            peer.pending().put(packet.seq(), new UdpPeer.Pending(datagram, System.nanoTime()));
        }
        transmit(peer.address(), datagram);
    }

    private void transmit(InetSocketAddress to, byte[] datagram) {
        DatagramSocket current = socket;
        if (current == null || current.isClosed()) {
            return;
        }
        try {
            current.send(new DatagramPacket(datagram, datagram.length, to));
        } catch (IOException e) {
            log.debug("[UDP] send to {} failed: {}", to, e.getMessage());
        }
    }

    // ─── upkeep ──────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 50)
    void resendUnacked() {
        long now = System.nanoTime();
        long waitNanos = TimeUnit.MILLISECONDS.toNanos(properties.retransmitMillis());
        for (UdpPeer peer : registry.peers()) {
            peer.pending().forEach((seq, pending) -> {
                if (now - pending.sentAtNanos < waitNanos) {
                    return;
                }
                if (pending.attempts >= properties.maxRetransmits()) {
                    peer.pending().remove(seq, pending);
                    log.warn("[UDP] gave up on seq {} to user {} after {} resends", seq, peer.userId(), pending.attempts);
                    return;
                }
                pending.attempts++;
                pending.sentAtNanos = now;
                transmit(peer.address(), pending.datagram);
            });
        }
    }

    @Scheduled(fixedDelay = 5000)
    void dropIdlePeers() {
        long threshold = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(properties.idleTimeoutMillis());
        for (UdpPeer peer : registry.idleSince(threshold)) {
            registry.remove(peer);
        }
    }
}
