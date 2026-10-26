package com.wordonline.server.auth.repository;

import com.wordonline.server.auth.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private static final String GET_SELECTED_DECK_ID_BY_ID = """
            SELECT selected_deck_id
            FROM users
            WHERE id = :userId;
            """;

    private static final String UPDATE_STATUS = """
        UPDATE users
           SET status = :status
         WHERE id = :id;
        """;

    private static final String GET_MMR = """
            SELECT mmr
            FROM users
            WHERE id = :userId;
            """;

    private static final String SET_MMR = """
            UPDATE users
            SET mmr = :mmr
            WHERE id = :userId;
            """;

    private static final String ADD_MMR = """
            UPDATE users
            SET mmr = COALESCE(mmr, :fallbackMmr) + :delta
            WHERE id = :userId;
            """;

    private static final String INCREMENT_TOTAL_WINS = """
            update users
            set total_wins = coalesce(total_wins, 0) + 1
            where id = :userId;
            """;

    private static final String GET_NOVICE_PROGRESS = """
            select novice_progress
            from users
            where id = :userId;
            """;

    private static final String ADVANCE_NOVICE_PROGRESS = """
            update users
            set novice_progress = least(1.0, novice_progress + :step)
            where id = :userId
              and novice_progress < 1.0;
            """;

    private final JdbcClient jdbcClient;

    public Optional<Long> getSelectedDeckId(long userId) {
        return jdbcClient.sql(GET_SELECTED_DECK_ID_BY_ID)
                .param("userId", userId)
                .query(Long.class)
                .optional();
    }

    public Optional<Short> getMmr(long userId) {
        return jdbcClient.sql(GET_MMR)
                .param("userId", userId)
                .query(Short.class)
                .optional();
    }

    public void setMmr(long userId, short mmr) {
        jdbcClient.sql(SET_MMR)
                .param("userId", userId)
                .param("mmr", mmr)
                .update();
    }

    // Moves the stored rating by delta in one statement, so two overlapping matches of the same
    // participant both count. fallbackMmr stands in for a rating that was never set.
    public void addMmr(long userId, short delta, short fallbackMmr) {
        jdbcClient.sql(ADD_MMR)
                .param("userId", userId)
                .param("delta", delta)
                .param("fallbackMmr", fallbackMmr)
                .update();
    }

    public void updateStatus(long userId, UserStatus status) {
        jdbcClient.sql(UPDATE_STATUS)
                .param("id", userId)
                .param("status", status.name())
                .update();
    }

    public void incrementTotalWins(long userId) {
        jdbcClient.sql(INCREMENT_TOTAL_WINS)
                .param("userId", userId)
                .update();
    }

    /**
     * How far the player is through the tutorial, from 0.5 to 1.0. Missing rows answer 1.0: only a
     * real account that has not finished it gets an opponent holding back.
     */
    public double getNoviceProgress(long userId) {
        return jdbcClient.sql(GET_NOVICE_PROGRESS)
                .param("userId", userId)
                .query(Double.class)
                .optional()
                .orElse(1.0);
    }

    /**
     * Moves the player along the tutorial, never past the end and never for someone already there.
     *
     * @return true when this call moved them
     */
    public boolean advanceNoviceProgress(long userId, double step) {
        return jdbcClient.sql(ADVANCE_NOVICE_PROGRESS)
                .param("userId", userId)
                .param("step", step)
                .update() > 0;
    }
}
