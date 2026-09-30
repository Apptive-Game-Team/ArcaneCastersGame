package com.wordonline.server.udp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.auth.config.JwtProvider;
import com.wordonline.server.auth.domain.PrincipalDetails;
import com.wordonline.server.game.channel.CompositeFrameChannel;
import com.wordonline.server.game.channel.FrameChannel;
import com.wordonline.server.game.channel.UdpFrameChannel;
import com.wordonline.server.game.controller.InputController;
import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.dto.CardInfoDto;
import com.wordonline.server.game.dto.frame.GameEventDto;
import com.wordonline.server.game.dto.frame.ObjectsInfoDto;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.FrameInfoDto;
import com.wordonline.server.game.dto.input.InputRequestDto;
import com.wordonline.server.session.service.SessionService;

/** Drives a real {@link UdpServer} over the loopback interface with a plain socket as the client. */
class UdpServerTest {

    private static final String SESSION_ID = "session-1";
    private static final long USER_ID = 11L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final InputController inputController = mock(InputController.class);
    private final SessionObject session = mock(SessionObject.class);
    private final UdpPeerRegistry registry = new UdpPeerRegistry();

    private UdpServer server;
    private DatagramSocket client;

    @BeforeEach
    void setUp() throws Exception {
        UdpProperties properties = new UdpProperties(true, 0, 1200, null, 20L, 3);
        server = new UdpServer(properties, jwtProvider, sessionService, inputController, registry, objectMapper);
        server.start();

        client = new DatagramSocket();
        client.setSoTimeout(1000);

        when(jwtProvider.getAuthentication("good-token")).thenReturn(
                new UsernamePasswordAuthenticationToken(new PrincipalDetails(USER_ID, List.of()), ""));
        when(jwtProvider.getAuthentication("bad-token")).thenThrow(new IllegalStateException("bad signature"));
        when(sessionService.getSessionObject(SESSION_ID)).thenReturn(session);
        when(session.getUserSide(USER_ID)).thenReturn(Master.LeftPlayer);
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.stop();
    }

    @Test
    void answersAValidHelloWithAConnectionId() throws Exception {
        UdpPacket ack = hello("good-token", SESSION_ID, USER_ID);

        assertThat(ack.type()).isEqualTo(UdpPacketType.HELLO_ACK);
        assertThat(ack.connectionId()).isNotZero();
        assertThat(registry.byUser(SESSION_ID, USER_ID).connectionId()).isEqualTo(ack.connectionId());
    }

    @Test
    void repeatedHelloGetsTheSameConnectionId() throws Exception {
        long first = hello("good-token", SESSION_ID, USER_ID).connectionId();
        long second = hello("good-token", SESSION_ID, USER_ID).connectionId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void refusesABadToken() throws Exception {
        UdpPacket reply = hello("bad-token", SESSION_ID, USER_ID);

        assertThat(reply.type()).isEqualTo(UdpPacketType.HELLO_REJECT);
        assertThat(new String(reply.body(), StandardCharsets.UTF_8)).isEqualTo("auth");
        assertThat(registry.peers()).isEmpty();
    }

    @Test
    void refusesATokenThatBelongsToAnotherUser() throws Exception {
        UdpPacket reply = hello("good-token", SESSION_ID, 999L);

        assertThat(reply.type()).isEqualTo(UdpPacketType.HELLO_REJECT);
    }

    @Test
    void refusesAnUnknownSession() throws Exception {
        UdpPacket reply = hello("good-token", "no-such-session", USER_ID);

        assertThat(reply.type()).isEqualTo(UdpPacketType.HELLO_REJECT);
        assertThat(new String(reply.body(), StandardCharsets.UTF_8)).isEqualTo("session");
    }

    @Test
    void refusesAUserWhoIsNotInTheSession() throws Exception {
        when(session.getUserSide(USER_ID)).thenReturn(null);

        assertThat(hello("good-token", SESSION_ID, USER_ID).type()).isEqualTo(UdpPacketType.HELLO_REJECT);
    }

    @Test
    void acksAReliableInputAndHandsItToTheGameOnce() throws Exception {
        long connectionId = hello("good-token", SESSION_ID, USER_ID).connectionId();
        byte[] body = "{\"type\":\"useMagic\",\"magicId\":3}".getBytes(StandardCharsets.UTF_8);

        send(new UdpPacket(UdpPacketType.INPUT, UdpPacket.FLAG_RELIABLE, 1, connectionId, body));
        UdpPacket firstAck = receive();
        send(new UdpPacket(UdpPacketType.INPUT, UdpPacket.FLAG_RELIABLE, 1, connectionId, body));
        UdpPacket secondAck = receive();

        assertThat(firstAck.type()).isEqualTo(UdpPacketType.INPUT_ACK);
        assertThat(firstAck.seq()).isEqualTo(1);
        assertThat(secondAck.type()).isEqualTo(UdpPacketType.INPUT_ACK);
        // The repeat is acknowledged again (the first ack may have been lost) but not replayed.
        waitUntilHandled(1);
        verify(inputController, times(1)).handleInput(eq(SESSION_ID), eq(USER_ID), any(InputRequestDto.class), any());
    }

    @Test
    void ignoresInputFromAnUnknownConnectionOrAnotherAddress() throws Exception {
        long connectionId = hello("good-token", SESSION_ID, USER_ID).connectionId();
        byte[] body = "{\"type\":\"ping\"}".getBytes(StandardCharsets.UTF_8);

        send(new UdpPacket(UdpPacketType.INPUT, 0, 1, connectionId + 1, body));
        try (DatagramSocket other = new DatagramSocket()) {
            byte[] wire = new UdpPacket(UdpPacketType.INPUT, 0, 2, connectionId, body).encode();
            other.send(new DatagramPacket(wire, wire.length, InetAddress.getLoopbackAddress(), server.localPort()));
        }
        Thread.sleep(150);

        verify(inputController, never()).handleInput(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }

    @Test
    void byeForgetsThePeer() throws Exception {
        long connectionId = hello("good-token", SESSION_ID, USER_ID).connectionId();

        send(new UdpPacket(UdpPacketType.BYE, 0, 1, connectionId, new byte[0]));
        Thread.sleep(150);

        assertThat(registry.byUser(SESSION_ID, USER_ID)).isNull();
    }

    @Test
    void sendsAFrameAsOneUnreliableDatagram() throws Exception {
        UdpPeer peer = connect();
        UdpFrameChannel channel = new UdpFrameChannel(SESSION_ID, registry, server, objectMapper);

        assertThat(channel.trySend(USER_ID, frameWith(1))).isTrue();
        UdpPacket packet = receive();

        assertThat(packet.type()).isEqualTo(UdpPacketType.FRAME);
        assertThat(packet.isReliable()).isFalse();
        assertThat(packet.isFragment()).isFalse();
        assertThat(packet.connectionId()).isEqualTo(peer.connectionId());
        assertThat(peer.pending()).isEmpty();
    }

    @Test
    void splitsALargeFrameIntoFragmentsThatRebuildTheOriginal() throws Exception {
        server.stop();
        server = new UdpServer(new UdpProperties(true, 0, 200, null, 20L, 3),
                jwtProvider, sessionService, inputController, registry, objectMapper);
        server.start();
        connect();
        UdpFrameChannel channel = new UdpFrameChannel(SESSION_ID, registry, server, objectMapper);
        // This server caps datagrams at 200 bytes, so 60 events cannot fit in one packet.
        FrameInfoDto frame = frameWith(60);
        byte[] expected = objectMapper.writeValueAsBytes(frame);

        channel.send(USER_ID, frame);

        Map<Integer, byte[]> chunks = new TreeMap<>();
        int base = -1;
        int count = 0;
        do {
            UdpPacket packet = receive();
            assertThat(packet.type()).isEqualTo(UdpPacketType.FRAME);
            assertThat(packet.isFragment()).isTrue();
            assertThat(packet.encode().length).isLessThanOrEqualTo(200);
            int index = packet.body()[0] & 0xFF;
            count = packet.body()[1] & 0xFF;
            base = packet.seq() - index;
            byte[] chunk = new byte[packet.body().length - UdpPacket.FRAGMENT_PREFIX_SIZE];
            System.arraycopy(packet.body(), UdpPacket.FRAGMENT_PREFIX_SIZE, chunk, 0, chunk.length);
            chunks.put(index, chunk);
        } while (chunks.size() < count);

        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        chunks.values().forEach(joined::writeBytes);
        assertThat(count).isGreaterThan(1);
        assertThat(chunks.keySet()).containsExactlyElementsOf(range(count));
        assertThat(joined.toByteArray()).isEqualTo(expected);
        assertThat(base).isEqualTo(1);
    }

    @Test
    void resendsAReliableMessageUntilItIsAcked() throws Exception {
        UdpPeer peer = connect();
        UdpFrameChannel channel = new UdpFrameChannel(SESSION_ID, registry, server, objectMapper);

        channel.send(USER_ID, Map.of("type", "result"));
        UdpPacket first = receive();
        Thread.sleep(40);
        server.resendUnacked();
        UdpPacket again = receive();

        assertThat(first.type()).isEqualTo(UdpPacketType.MESSAGE);
        assertThat(first.isReliable()).isTrue();
        assertThat(again.seq()).isEqualTo(first.seq());

        send(new UdpPacket(UdpPacketType.MESSAGE_ACK, 0, first.seq(), peer.connectionId(), new byte[0]));
        Thread.sleep(150);

        assertThat(peer.pending()).isEmpty();
    }

    @Test
    void givesUpOnAMessageAfterTheResendBudget() throws Exception {
        UdpPeer peer = connect();
        UdpFrameChannel channel = new UdpFrameChannel(SESSION_ID, registry, server, objectMapper);

        channel.send(USER_ID, Map.of("type", "result"));
        for (int i = 0; i < 6; i++) {
            Thread.sleep(30);
            server.resendUnacked();
        }

        assertThat(peer.pending()).isEmpty();
    }

    @Test
    void compositeFallsBackToStompForAPlayerThatIsNotOnUdp() {
        FrameChannel stomp = mock(FrameChannel.class);
        UdpFrameChannel udp = new UdpFrameChannel(SESSION_ID, registry, server, objectMapper);
        CompositeFrameChannel composite = new CompositeFrameChannel(stomp, udp);

        composite.send(USER_ID, "payload");
        composite.broadcast("spectators");

        verify(stomp).send(USER_ID, "payload");
        verify(stomp).broadcast("spectators");
    }

    @Test
    void compositeSkipsStompForAPlayerOnUdp() throws Exception {
        connect();
        FrameChannel stomp = mock(FrameChannel.class);
        CompositeFrameChannel composite = new CompositeFrameChannel(stomp,
                new UdpFrameChannel(SESSION_ID, registry, server, objectMapper));

        composite.send(USER_ID, Map.of("type", "result"));

        assertThat(receive().type()).isEqualTo(UdpPacketType.MESSAGE);
        verify(stomp, never()).send(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void evictingAUserStopsUdpDelivery() throws Exception {
        connect();
        UdpPeerEvictor evictor = new UdpPeerEvictor(registry);
        registry.removeUser(SESSION_ID, USER_ID);

        assertThat(new UdpFrameChannel(SESSION_ID, registry, server, objectMapper)
                .trySend(USER_ID, Map.of("type", "result"))).isFalse();
        assertThat(evictor).isNotNull();
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private UdpPeer connect() throws Exception {
        hello("good-token", SESSION_ID, USER_ID);
        return registry.byUser(SESSION_ID, USER_ID);
    }

    private UdpPacket hello(String token, String sessionId, long userId) throws Exception {
        String json = objectMapper.writeValueAsString(
                Map.of("token", token, "sessionId", sessionId, "userId", userId));
        send(new UdpPacket(UdpPacketType.HELLO, 0, 1, 0L, json.getBytes(StandardCharsets.UTF_8)));
        return receive();
    }

    private void send(UdpPacket packet) throws Exception {
        byte[] wire = packet.encode();
        client.send(new DatagramPacket(wire, wire.length, InetAddress.getLoopbackAddress(), server.localPort()));
    }

    private UdpPacket receive() throws Exception {
        byte[] buffer = new byte[2048];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        try {
            client.receive(datagram);
        } catch (SocketTimeoutException e) {
            throw new AssertionError("the server sent nothing within a second", e);
        }
        return UdpPacket.decode(buffer, datagram.getLength()).orElseThrow();
    }

    private void waitUntilHandled(int expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try {
                verify(inputController, times(expected)).handleInput(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any());
                return;
            } catch (AssertionError e) {
                Thread.sleep(20);
            }
        }
    }

    private static FrameInfoDto frameWith(int events) {
        List<GameEventDto> list = new ArrayList<>();
        for (int i = 0; i < events; i++) {
            list.add(GameEventDto.hit(i, i + 1));
        }
        return new FrameInfoDto(30, new CardInfoDto(), new ObjectsInfoDto(),
                new GameSessionData(mock(PlayerData.class), mock(PlayerData.class)), list);
    }

    private static List<Integer> range(int count) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(i);
        }
        return list;
    }
}
