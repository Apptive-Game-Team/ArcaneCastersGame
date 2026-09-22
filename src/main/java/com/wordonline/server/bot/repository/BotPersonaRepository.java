package com.wordonline.server.bot.repository;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.bot.dto.BotPersonaRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BotPersonaRepository {

    private static final String FIND_ALL = """
            SELECT user_id, name, tier, thinking_time_ms, reaction_interval_frames,
                   counter_aggression, enabled, hospitality, temperament
            FROM bot_personas
            ORDER BY user_id;
            """;

    private static final String FIND_BY_ID = """
            SELECT user_id, name, tier, thinking_time_ms, reaction_interval_frames,
                   counter_aggression, enabled, hospitality, temperament
            FROM bot_personas
            WHERE user_id = :userId;
            """;

    private static final String INSERT = """
            INSERT INTO bot_personas(user_id, name, tier, thinking_time_ms, reaction_interval_frames,
                                     counter_aggression, enabled, hospitality, temperament)
            VALUES(:userId, :name, :tier::bot_tier, :thinkingTimeMs, :reactionIntervalFrames,
                   :counterAggression, :enabled, :hospitality, :temperament::bot_temperament);
            """;

    private static final String UPDATE = """
            UPDATE bot_personas
            SET name = :name,
                tier = :tier::bot_tier,
                thinking_time_ms = :thinkingTimeMs,
                reaction_interval_frames = :reactionIntervalFrames,
                counter_aggression = :counterAggression,
                enabled = :enabled,
                hospitality = :hospitality,
                temperament = :temperament::bot_temperament
            WHERE user_id = :userId;
            """;
    private static final String DELETE = """
            DELETE FROM bot_personas
            WHERE user_id = :userId;
            """;

    private final JdbcClient jdbcClient;

    public List<BotPersona> findAll() {
        return jdbcClient.sql(FIND_ALL)
                .query(this::map)
                .list();
    }

    public Optional<BotPersona> findByUserId(long userId) {
        return jdbcClient.sql(FIND_BY_ID)
                .param("userId", userId)
                .query(this::map)
                .optional();
    }

    public BotPersona create(BotPersonaRequestDto requestDto) {
        bind(jdbcClient.sql(INSERT), requestDto)
                .update();
        return findByUserId(requestDto.userId())
                .orElseThrow(() -> new IllegalStateException("Created bot persona not found."));
    }

    public int update(long userId, BotPersonaRequestDto requestDto) {
        return bind(jdbcClient.sql(UPDATE).param("userId", userId), requestDto).update();
    }

    public int delete(long userId) {
        return jdbcClient.sql(DELETE).param("userId", userId).update();
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, BotPersonaRequestDto requestDto) {
        return spec
                .param("userId", requestDto.userId())
                .param("name", requestDto.name())
                .param("tier", requestDto.tier().name())
                .param("thinkingTimeMs", requestDto.thinkingTimeMs())
                .param("reactionIntervalFrames", requestDto.reactionIntervalFrames())
                .param("counterAggression", requestDto.counterAggression())
                .param("enabled", requestDto.enabled() == null || requestDto.enabled())
                .param("hospitality", requestDto.hospitality() != null && requestDto.hospitality())
                .param("temperament", requestDto.temperament() == null
                        ? BotTemperament.WARM.name()
                        : requestDto.temperament().name());
    }

    private BotPersona map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BotPersona(
                rs.getLong("user_id"),
                rs.getString("name"),
                BotTier.valueOf(rs.getString("tier")),
                rs.getInt("thinking_time_ms"),
                rs.getInt("reaction_interval_frames"),
                rs.getDouble("counter_aggression"),
                rs.getBoolean("enabled"),
                rs.getBoolean("hospitality"),
                BotTemperament.valueOf(rs.getString("temperament"))
        );
    }
}
