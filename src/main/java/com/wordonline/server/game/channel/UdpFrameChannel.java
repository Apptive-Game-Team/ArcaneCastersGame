package com.wordonline.server.game.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordonline.server.game.dto.frame.FrameInfoDto;
import com.wordonline.server.game.dto.sync.SyncInfoDto;
import com.wordonline.server.udp.UdpPeer;
import com.wordonline.server.udp.UdpPeerRegistry;
import com.wordonline.server.udp.UdpServer;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link FrameChannel} over the UDP game channel. Only players who completed the UDP handshake are
 * reachable; {@link #trySend} says whether this channel took the payload so a caller can fall back
 * to another one.
 *
 * <p>Frames go out unreliably, since the next sync frame repairs a lost one. Every other payload
 * (the result, an input response, an emote, a PVE script event) goes out reliably: losing the result
 * would leave the client waiting in the game scene.
 */
@Slf4j
public class UdpFrameChannel implements FrameChannel {

    private final String sessionId;
    private final UdpPeerRegistry registry;
    private final UdpServer server;
    private final ObjectMapper objectMapper;

    public UdpFrameChannel(String sessionId, UdpPeerRegistry registry, UdpServer server, ObjectMapper objectMapper) {
        this.sessionId = sessionId;
        this.registry = registry;
        this.server = server;
        this.objectMapper = objectMapper;
    }

    /** @return {@code true} when the user is connected over UDP and the payload was handed to the socket. */
    public boolean trySend(long userId, Object data) {
        UdpPeer peer = registry.byUser(sessionId, userId);
        if (peer == null) {
            return false;
        }
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            log.error("[UDP] could not serialize {}: {}", data.getClass().getSimpleName(), e.getMessage());
            return true;
        }
        if (data instanceof FrameInfoDto || data instanceof SyncInfoDto) {
            server.sendFrame(peer, payload);
        } else {
            server.sendMessage(peer, payload);
        }
        return true;
    }

    @Override
    public void send(long userId, Object data) {
        trySend(userId, data);
    }

    /** Spectators watch over STOMP; there is nobody to broadcast to on this channel. */
    @Override
    public void broadcast(Object data) {
    }
}
