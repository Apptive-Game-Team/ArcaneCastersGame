package com.wordonline.server.udp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/**
 * One UDP datagram. Big-endian, 16 byte header followed by the body:
 * <pre>
 *  0      1      2      3      4..7     8..15
 * magic  type  flags  rsv    seq(u32)  connectionId(u64)
 * </pre>
 * A packet with {@link #FLAG_FRAGMENT} carries {@code idx(u8) count(u8)} in front of its chunk.
 * The fragments of one payload take consecutive seqs, so a fragment's group is {@code seq - idx}.
 */
public record UdpPacket(UdpPacketType type, int flags, int seq, long connectionId, byte[] body) {

    public static final byte MAGIC = (byte) 0xAC;
    public static final int HEADER_SIZE = 16;
    public static final int FRAGMENT_PREFIX_SIZE = 2;

    /** The receiver acknowledges this packet's seq and drops repeats. */
    public static final int FLAG_RELIABLE = 0x01;
    /** The body starts with {@code idx, count} and holds one chunk of a larger payload. */
    public static final int FLAG_FRAGMENT = 0x02;

    public boolean isReliable() {
        return (flags & FLAG_RELIABLE) != 0;
    }

    public boolean isFragment() {
        return (flags & FLAG_FRAGMENT) != 0;
    }

    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + body.length);
        buffer.put(MAGIC);
        buffer.put((byte) type.code());
        buffer.put((byte) flags);
        buffer.put((byte) 0);
        buffer.putInt(seq);
        buffer.putLong(connectionId);
        buffer.put(body);
        return buffer.array();
    }

    /** Empty for anything that is not one of ours: wrong magic, too short, unknown type. */
    public static Optional<UdpPacket> decode(byte[] data, int length) {
        if (length < HEADER_SIZE || data[0] != MAGIC) {
            return Optional.empty();
        }
        UdpPacketType type = UdpPacketType.fromCode(data[1] & 0xFF);
        if (type == null) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        buffer.position(2);
        int flags = buffer.get() & 0xFF;
        buffer.get();
        int seq = buffer.getInt();
        long connectionId = buffer.getLong();
        byte[] body = Arrays.copyOfRange(data, HEADER_SIZE, length);
        return Optional.of(new UdpPacket(type, flags, seq, connectionId, body));
    }
}
