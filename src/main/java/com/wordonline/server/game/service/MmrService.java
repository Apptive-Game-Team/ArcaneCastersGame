package com.wordonline.server.game.service;

import com.wordonline.server.auth.repository.UserRepository;
import com.wordonline.server.game.dto.result.ResultMmrDto;
import com.wordonline.server.game.dto.result.ResultType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class MmrService {

    private static final double K = 20.0;
    private static final short DEFAULT_MMR = 1000;

    private final UserRepository userRepository;

    private static double expectedScore(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    private static short computeNewRating(short oldRating, short opponentRating, double score) {
        double expected = expectedScore(oldRating, opponentRating);
        double updated = oldRating + K * (score - expected);
        return (short) Math.round(updated);
    }

    @Transactional(readOnly = true)
    public short fetchRating(long participantId) {
        return resolveRatingRef(participantId).rating();
    }

    @Transactional(readOnly = true)
    public RatingRef resolveRatingRef(long participantId) {
        short rating = userRepository.getMmr(participantId).orElse(DEFAULT_MMR);
        return new RatingRef(participantId, participantId, participantId < 0, rating);
    }

    public ResultMmrDto updateMatchResult(long userIdA, long userIdB, ResultType outcomeA,
                                          boolean rateHumansAgainstBots) {
        RatingRef ratingRefA = resolveRatingRef(userIdA);
        RatingRef ratingRefB = resolveRatingRef(userIdB);
        ResultMmrDto result = computeMatchResult(ratingRefA, ratingRefB, outcomeA, rateHumansAgainstBots);
        saveMatchResult(ratingRefA, ratingRefB, result);
        return result;
    }

    /**
     * The new ratings for a finished match, computed in memory from the ratings both sides had.
     *
     * <p>Static so it never goes through the transactional proxy: the loop thread calls it at match
     * end, and a transaction there would take a database connection for no query.
     *
     * @param rateHumansAgainstBots when one side is a bot: {@code true} moves both ratings (a ranked
     *                              match the lobby filled with a bot); {@code false} moves only the
     *                              bot's and keeps the human's (practice).
     */
    public static ResultMmrDto computeMatchResult(RatingRef ratingRefA, RatingRef ratingRefB, ResultType outcomeA,
                                                  boolean rateHumansAgainstBots) {
        short ratingA = ratingRefA.rating();
        short ratingB = ratingRefB.rating();

        double scoreA = switch (outcomeA) {
            case Win -> 1.0;
            case Draw -> 0.5;
            case Lose -> 0.0;
        };
        double scoreB = 1.0 - scoreA;

        short computedA = computeNewRating(ratingA, ratingB, scoreA);
        short computedB = computeNewRating(ratingB, ratingA, scoreB);
        boolean botMatch = ratingRefA.bot() || ratingRefB.bot();
        boolean freezeHumans = botMatch && !rateHumansAgainstBots;
        short newA = freezeHumans && !ratingRefA.bot() ? ratingA : computedA;
        short newB = freezeHumans && !ratingRefB.bot() ? ratingB : computedB;

        return new ResultMmrDto(ratingA, ratingB, newA, newB);
    }

    /**
     * Stores a result {@link #computeMatchResult} produced from these same two references.
     *
     * <p>A human's rating is written as the computed value: a player is in one session at a time,
     * so the rating read when the session started is still the stored one. A bot can play several
     * sessions at once, so its rating is moved by the difference instead; writing the computed
     * value would let the last of two overlapping matches erase the first one's change.
     *
     * <p>A human rating the result left unchanged is not written, which is how a practice match
     * keeps the human's rating.
     */
    public void saveMatchResult(RatingRef ratingRefA, RatingRef ratingRefB, ResultMmrDto result) {
        saveSide(ratingRefA, result.newLeftMmr());
        saveSide(ratingRefB, result.newRightMmr());
        log.trace("Updated match result for {}: {}, {}: {}", ratingRefA.participantId(), result.newLeftMmr(),
                ratingRefB.participantId(), result.newRightMmr());
    }

    private void saveSide(RatingRef ratingRef, short mmr) {
        if (ratingRef.bot()) {
            userRepository.addMmr(ratingRef.storageId(), (short) (mmr - ratingRef.rating()), DEFAULT_MMR);
        } else if (mmr != ratingRef.rating()) {
            userRepository.setMmr(ratingRef.storageId(), mmr);
        }
    }

    public record RatingRef(long participantId, long storageId, boolean bot, short rating) {
    }
}
