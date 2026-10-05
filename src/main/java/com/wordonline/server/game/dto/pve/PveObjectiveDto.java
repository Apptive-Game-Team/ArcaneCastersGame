package com.wordonline.server.game.dto.pve;

import com.wordonline.server.game.domain.pve.PveWinCondition;

/** Tells the client how a PVE match is won; sent to both users whenever a value changes. */
public record PveObjectiveDto(
        String type,
        PveWinCondition winCondition,
        int surviveSeconds,
        int remainingSeconds,
        int objectivesTotal,
        int objectivesRemaining
) {
    public static final String TYPE = "pveObjective";

    public PveObjectiveDto(PveWinCondition winCondition,
                           int surviveSeconds,
                           int remainingSeconds,
                           int objectivesTotal,
                           int objectivesRemaining) {
        this(TYPE, winCondition, surviveSeconds, remainingSeconds, objectivesTotal, objectivesRemaining);
    }
}
