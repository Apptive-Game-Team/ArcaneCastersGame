package com.wordonline.server.udp;

/** Packet kinds of the UDP game channel. The wire code is the byte in the header. */
public enum UdpPacketType {
    /** client to server: JSON {token, sessionId, userId}. Repeated until answered. */
    HELLO(1),
    /** server to client: the connection id in the header is the key for every later packet. */
    HELLO_ACK(2),
    /** server to client: the hello was refused (bad token, unknown session). Body is a short reason. */
    HELLO_REJECT(3),
    /** client to server: an InputRequestDto as JSON. */
    INPUT(4),
    /** server to client: the INPUT with this seq arrived. */
    INPUT_ACK(5),
    /** server to client: FrameInfoDto / SyncInfoDto JSON. Unreliable; stale ones are dropped. */
    FRAME(6),
    /** server to client: any other payload (result, input response, emote, PVE script). Reliable. */
    MESSAGE(7),
    /** client to server: the MESSAGE with this seq arrived. */
    MESSAGE_ACK(8),
    /** either direction: the peer is leaving. */
    BYE(9);

    private final int code;

    UdpPacketType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static UdpPacketType fromCode(int code) {
        for (UdpPacketType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
