package com.wordonline.server.game.channel;

/**
 * Where a session's frame payloads leave the server. The game loop only knows this interface, so a
 * transport (STOMP over WebSocket today, UDP next) can be swapped or combined without touching it.
 *
 * <p>Both methods are called on the game loop thread every frame, so an implementation must not
 * block and must not throw for a recipient that has already gone away.
 */
public interface FrameChannel {

    /** Sends {@code data} to one player. Bots (negative ids) are filtered out by the caller. */
    void send(long userId, Object data);

    /** Sends {@code data} to everyone watching the session (spectators, userId 0). */
    void broadcast(Object data);
}
