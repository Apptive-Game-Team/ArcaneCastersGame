package com.wordonline.server.session.dto;

import com.wordonline.server.game.domain.SessionType;

public record SessionDto(
        String sessionId,
        Long uid1,
        Long uid2,
        SessionType sessionType,
        Long scenarioId,
        java.util.List<Long> leftDeckCardIds,
        java.util.List<Long> rightDeckCardIds
) {
    public SessionDto(String sessionId, Long uid1, Long uid2, SessionType sessionType, Long scenarioId) {
        this(sessionId, uid1, uid2, sessionType, scenarioId, null, null);
    }

}
