package com.wordonline.server.udp;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.wordonline.server.auth.domain.PrincipalDetails;

/**
 * Who is connected over UDP. A peer is found two ways: by the connection id its packets carry, and
 * by (session, user) when the game loop wants to send to it.
 */
@Component
public class UdpPeerRegistry {

    private final SecureRandom random = new SecureRandom();
    private final Map<Long, UdpPeer> byConnection = new ConcurrentHashMap<>();
    private final Map<String, UdpPeer> byUser = new ConcurrentHashMap<>();

    /**
     * Registers the peer, or returns the one that already holds this address so a repeated hello is
     * answered with the same connection id. A hello from a new address replaces the old peer: the
     * client came back from a fresh socket.
     */
    public synchronized UdpPeer bind(String sessionId, long userId, PrincipalDetails principal,
                                     InetSocketAddress address, long nowNanos) {
        String key = key(sessionId, userId);
        UdpPeer existing = byUser.get(key);
        if (existing != null && existing.address().equals(address)) {
            existing.touch(nowNanos);
            return existing;
        }
        if (existing != null) {
            byConnection.remove(existing.connectionId());
        }
        long connectionId;
        do {
            connectionId = random.nextLong();
        } while (connectionId == 0 || byConnection.containsKey(connectionId));

        UdpPeer peer = new UdpPeer(connectionId, sessionId, userId, principal, address, nowNanos);
        byConnection.put(connectionId, peer);
        byUser.put(key, peer);
        return peer;
    }

    public UdpPeer byConnection(long connectionId) {
        return byConnection.get(connectionId);
    }

    public UdpPeer byUser(String sessionId, long userId) {
        return byUser.get(key(sessionId, userId));
    }

    public synchronized void remove(UdpPeer peer) {
        byConnection.remove(peer.connectionId());
        byUser.remove(key(peer.sessionId(), peer.userId()), peer);
    }

    /** The client moved to STOMP; whatever was in flight over UDP no longer matters. */
    public synchronized void removeUser(String sessionId, long userId) {
        UdpPeer peer = byUser.get(key(sessionId, userId));
        if (peer != null) {
            remove(peer);
        }
    }

    public Collection<UdpPeer> peers() {
        return new ArrayList<>(byConnection.values());
    }

    public List<UdpPeer> idleSince(long thresholdNanos) {
        List<UdpPeer> idle = new ArrayList<>();
        for (UdpPeer peer : byConnection.values()) {
            if (peer.lastSeenNanos() - thresholdNanos < 0) {
                idle.add(peer);
            }
        }
        return idle;
    }

    private static String key(String sessionId, long userId) {
        return sessionId + "/" + userId;
    }
}
