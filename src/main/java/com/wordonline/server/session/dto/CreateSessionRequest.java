package com.wordonline.server.session.dto;

import com.wordonline.server.game.domain.SessionType;

public record CreateSessionRequest(
        String attemptId,
        String sessionId,
        Long uid1,
        Long uid2,
        SessionType sessionType,
        Long scenarioId,
        java.util.List<Long> leftDeckCardIds,
        java.util.List<Long> rightDeckCardIds
) {
    public CreateSessionRequest(String attemptId, String sessionId, Long uid1, Long uid2,
                                SessionType sessionType, Long scenarioId) {
        this(attemptId, sessionId, uid1, uid2, sessionType, scenarioId, null, null);
    }

    public SessionDto toSessionDto() {
        return new SessionDto(sessionId, uid1, uid2, sessionType, scenarioId, leftDeckCardIds, rightDeckCardIds);
    }
}
