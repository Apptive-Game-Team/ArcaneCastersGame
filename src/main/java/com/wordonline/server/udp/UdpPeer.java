package com.wordonline.server.udp;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.wordonline.server.auth.domain.PrincipalDetails;

/** One client that completed the UDP handshake: who it is, where it is, and what is in flight to it. */
public class UdpPeer {

    /** A reliable datagram waiting for its ack. */
    static final class Pending {
        final byte[] datagram;
        volatile long sentAtNanos;
        volatile int attempts;

        Pending(byte[] datagram, long sentAtNanos) {
            this.datagram = datagram;
            this.sentAtNanos = sentAtNanos;
        }
    }

    private final long connectionId;
    private final String sessionId;
    private final long userId;
    private final PrincipalDetails principal;
    private volatile InetSocketAddress address;
    private volatile long lastSeenNanos;

    // Seqs start at 1 so that 0 never names a packet.
    private final AtomicInteger frameSeq = new AtomicInteger(1);
    private final AtomicInteger messageSeq = new AtomicInteger(1);
    private final ReliableWindow inputWindow = new ReliableWindow();
    private final ConcurrentHashMap<Integer, Pending> pending = new ConcurrentHashMap<>();

    UdpPeer(long connectionId, String sessionId, long userId, PrincipalDetails principal,
            InetSocketAddress address, long nowNanos) {
        this.connectionId = connectionId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.principal = principal;
        this.address = address;
        this.lastSeenNanos = nowNanos;
    }

    public long connectionId() {
        return connectionId;
    }

    public String sessionId() {
        return sessionId;
    }

    public long userId() {
        return userId;
    }

    public PrincipalDetails principal() {
        return principal;
    }

    public InetSocketAddress address() {
        return address;
    }

    long lastSeenNanos() {
        return lastSeenNanos;
    }

    void touch(long nowNanos) {
        lastSeenNanos = nowNanos;
    }

    /** Reserves {@code count} consecutive seqs for one payload and returns the first. */
    int nextSeq(UdpPacketType type, int count) {
        return (type == UdpPacketType.FRAME ? frameSeq : messageSeq).getAndAdd(count);
    }

    boolean acceptInput(int seq) {
        return inputWindow.accept(seq);
    }

    ConcurrentHashMap<Integer, Pending> pending() {
        return pending;
    }
}
