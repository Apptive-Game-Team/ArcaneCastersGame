package com.wordonline.server.local;

import java.util.List;

import com.wordonline.server.game.config.GameTickProperties;
import com.wordonline.server.game.domain.SessionType;

/**
 * Body of {@code POST /api/local/sessions}: the lobby's {@code POST /api/local-sessions} response,
 * passed through by the client unchanged, plus the tick rate the client chose. Fields this server
 * does not need ({@code leftUser}, {@code rightUser}, {@code issuedAt}) are accepted and ignored.
 *
 * <p>Plain types rather than enums: a malformed body has to come back as 400, and the global
 * exception handler turns body parsing failures into 500.
 */
public record LocalSessionRequest(
        String sessionId,
        String type,
        Long scenarioId,
        Long leftUserId,
        Long rightUserId,
        List<Long> leftDeckCardIds,
        List<Long> ownedMagicIds,
        Double noviceProgress,
        String staticVersion,
        Integer tickRate
) {

    static final int MAXIMUM_SESSION_ID_LENGTH = 64;
    private static final double MINIMUM_NOVICE_PROGRESS = 0.5;
    private static final double MAXIMUM_NOVICE_PROGRESS = 1.0;

    /** @throws IllegalArgumentException with a message naming the offending field */
    Validated validate(int maximumTickRate) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > MAXIMUM_SESSION_ID_LENGTH) {
            throw new IllegalArgumentException("sessionId must be 1 to " + MAXIMUM_SESSION_ID_LENGTH + " characters");
        }
        SessionType sessionType;
        if ("PRACTICE".equals(type)) {
            sessionType = SessionType.Practice;
        } else if ("PVE".equals(type)) {
            sessionType = SessionType.PVE;
        } else {
            throw new IllegalArgumentException("type must be PRACTICE or PVE");
        }
        if (leftUserId == null || leftUserId < 0) {
            throw new IllegalArgumentException("leftUserId must be a player id");
        }
        if (sessionType == SessionType.Practice && (rightUserId == null || rightUserId >= 0)) {
            throw new IllegalArgumentException("a PRACTICE session needs a bot rightUserId");
        }
        if (sessionType == SessionType.PVE && scenarioId == null) {
            throw new IllegalArgumentException("a PVE session needs scenarioId");
        }
        if (leftDeckCardIds == null || leftDeckCardIds.isEmpty() || leftDeckCardIds.contains(null)) {
            throw new IllegalArgumentException("leftDeckCardIds must list the deck's magic ids");
        }
        List<Long> owned = ownedMagicIds == null ? List.of() : ownedMagicIds;
        if (owned.contains(null)) {
            throw new IllegalArgumentException("ownedMagicIds must not contain null");
        }
        int rate = tickRate == null ? GameTickProperties.DEFAULT_TICK_RATE : tickRate;
        if (rate <= 0 || rate > maximumTickRate) {
            throw new IllegalArgumentException("tickRate must be between 1 and " + maximumTickRate);
        }
        double progress = noviceProgress == null ? MAXIMUM_NOVICE_PROGRESS
                : Math.max(MINIMUM_NOVICE_PROGRESS, Math.min(MAXIMUM_NOVICE_PROGRESS, noviceProgress));
        return new Validated(sessionId, sessionType, scenarioId, leftUserId,
                sessionType == SessionType.PVE ? -1L : rightUserId,
                List.copyOf(leftDeckCardIds), List.copyOf(owned), progress, staticVersion, rate);
    }

    record Validated(
            String sessionId,
            SessionType sessionType,
            Long scenarioId,
            long leftUserId,
            long rightUserId,
            List<Long> leftDeckCardIds,
            List<Long> ownedMagicIds,
            double noviceProgress,
            String staticVersion,
            int tickRate
    ) {
    }
}
