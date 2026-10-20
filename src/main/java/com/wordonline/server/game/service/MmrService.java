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

    public ResultMmrDto updateMatchResult(long userIdA, long userIdB, ResultType outcomeA) {
        RatingRef ratingRefA = resolveRatingRef(userIdA);
        RatingRef ratingRefB = resolveRatingRef(userIdB);
        ResultMmrDto result = computeMatchResult(ratingRefA, ratingRefB, outcomeA);
        saveMatchResult(ratingRefA, ratingRefB, result);
        return result;
    }

    /**
     * The new ratings for a finished match, computed in memory from the ratings both sides had.
     *
     * <p>Static so it never goes through the transactional proxy: the loop thread calls it at match
     * end, and a transaction there would take a database connection for no query.
     *
     * <p>In a match against a bot only the bot's rating moves; the human keeps theirs.
     */
    public static ResultMmrDto computeMatchResult(RatingRef ratingRefA, RatingRef ratingRefB, ResultType outcomeA) {
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
        short newA = botMatch && !ratingRefA.bot() ? ratingA : computedA;
        short newB = botMatch && !ratingRefB.bot() ? ratingB : computedB;

        return new ResultMmrDto(ratingA, ratingB, newA, newB);
    }

    /**
     * Stores a result {@link #computeMatchResult} produced from these same two references.
     *
     * <p>A human's rating is written as the computed value: a player is in one session at a time,
     * so the rating read when the session started is still the stored one. A bot can play several
     * sessions at once, so its rating is moved by the difference instead; writing the computed
     * value would let the last of two overlapping matches erase the first one's change.
     */
    public void saveMatchResult(RatingRef ratingRefA, RatingRef ratingRefB, ResultMmrDto result) {
        boolean botMatch = ratingRefA.bot() || ratingRefB.bot();
        if (botMatch) {
            saveBotRating(ratingRefA, result.newLeftMmr());
            saveBotRating(ratingRefB, result.newRightMmr());
        } else {
            saveRating(ratingRefA, result.newLeftMmr());
            saveRating(ratingRefB, result.newRightMmr());
        }
        log.trace("Updated match result for {}: {}, {}: {}", ratingRefA.participantId(), result.newLeftMmr(),
                ratingRefB.participantId(), result.newRightMmr());
    }

    private void saveRating(RatingRef ratingRef, short mmr) {
        userRepository.setMmr(ratingRef.storageId(), mmr);
    }

    private void saveBotRating(RatingRef ratingRef, short mmr) {
        if (ratingRef.bot()) {
            userRepository.addMmr(ratingRef.storageId(), (short) (mmr - ratingRef.rating()), DEFAULT_MMR);
        }
    }

    public record RatingRef(long participantId, long storageId, boolean bot, short rating) {
    }
}
