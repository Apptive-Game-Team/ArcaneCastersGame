package com.wordonline.server.game.dto.frame;

// One thing that happened during a frame. The type tells the client how to read the ids.
public record GameEventDto(String type, int actorId, int targetId) {

    public static GameEventDto hit(int attackerId, int targetId) {
        return new GameEventDto("hit", attackerId, targetId);
    }

    // The trap has no target when it discharges with nobody left in range, so targetId is unused.
    public static GameEventDto shock(int actorId) {
        return new GameEventDto("shock", actorId, 0);
    }
}
