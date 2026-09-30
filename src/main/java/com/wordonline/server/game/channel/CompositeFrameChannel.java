package com.wordonline.server.game.channel;

/**
 * Sends to a player over UDP when they are connected that way and over STOMP otherwise, so one
 * session can hold WebGL clients (WebSocket) and native clients (UDP) side by side.
 */
public class CompositeFrameChannel implements FrameChannel {

    private final FrameChannel stomp;
    private final UdpFrameChannel udp;

    public CompositeFrameChannel(FrameChannel stomp, UdpFrameChannel udp) {
        this.stomp = stomp;
        this.udp = udp;
    }

    @Override
    public void send(long userId, Object data) {
        if (!udp.trySend(userId, data)) {
            stomp.send(userId, data);
        }
    }

    @Override
    public void broadcast(Object data) {
        stomp.broadcast(data);
    }
}
