package com.wordonline.server.session.dto;

import com.wordonline.server.game.config.GameTickProperties;
import com.wordonline.server.game.domain.SessionType;

public record SessionDto(
        String sessionId,
        Long uid1,
        Long uid2,
        SessionType sessionType,
        Long scenarioId,
        java.util.List<Long> leftDeckCardIds,
        java.util.List<Long> rightDeckCardIds,
        // Frames per second for this session's loop; null runs at game.tick-rate.
        Integer tickRate
) {
    public SessionDto {
        if (tickRate != null) {
            GameTickProperties.validate(tickRate);
        }
    }

    public SessionDto(String sessionId, Long uid1, Long uid2, SessionType sessionType, Long scenarioId) {
        this(sessionId, uid1, uid2, sessionType, scenarioId, null, null);
    }

    public SessionDto(String sessionId, Long uid1, Long uid2, SessionType sessionType, Long scenarioId,
                      java.util.List<Long> leftDeckCardIds, java.util.List<Long> rightDeckCardIds) {
        this(sessionId, uid1, uid2, sessionType, scenarioId, leftDeckCardIds, rightDeckCardIds, null);
    }

}
