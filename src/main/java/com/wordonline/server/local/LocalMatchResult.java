package com.wordonline.server.local;

import java.util.List;

import com.wordonline.server.statistic.domain.GameOutcome;

/**
 * The {@code matchResult} line. Everything but {@code type} and {@code sessionId} is the body the
 * client forwards unchanged to the lobby's local session result endpoint, so the field names here
 * are a contract with the lobby (root plan, local hosting contracts, section 2).
 *
 * <p>{@code winUserId} and {@code lossUserId} are null unless {@code outcome} is WIN, as in
 * {@link com.wordonline.server.statistic.dto.GameResultDto}.
 */
public record LocalMatchResult(
        String type,
        String sessionId,
        GameOutcome outcome,
        Long winUserId,
        Long lossUserId,
        long durationMillis,
        int frameCount,
        int tickRate,
        String localServerVersion,
        List<Entry> decks,
        List<Entry> magics
) {
    public LocalMatchResult(String sessionId, GameOutcome outcome, Long winUserId, Long lossUserId,
                            long durationMillis, int frameCount, int tickRate, String localServerVersion,
                            List<Entry> decks, List<Entry> magics) {
        this("matchResult", sessionId, outcome, winUserId, lossUserId, durationMillis, frameCount, tickRate,
                localServerVersion, decks, magics);
    }

    /** One deck row (copies of a magic in the deck) or one cast row (times a magic was cast). */
    public record Entry(long userId, long magicId, int count) {
    }
}
