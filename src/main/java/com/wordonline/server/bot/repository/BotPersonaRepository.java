package com.wordonline.server.bot.repository;

import com.wordonline.server.bot.domain.BotBrainType;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.bot.dto.BotPersonaRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BotPersonaRepository {

    private static final String FIND_ALL = """
            SELECT user_id, name, tier, thinking_time_ms, reaction_interval_frames,
                   counter_aggression, enabled, hospitality, temperament, brain_type, llm_model
            FROM bot_personas
            ORDER BY user_id;
            """;

    /**
     * The persona rule shared with the lobby: enabled, not hospitality, and either the rule brain
     * or an LLM brain whose provider has a key row that is not currently unavailable.
     */
    private static final String FIND_MATCHABLE = """
            SELECT bp.user_id, bp.name, bp.tier, bp.thinking_time_ms, bp.reaction_interval_frames,
                   bp.counter_aggression, bp.enabled, bp.hospitality, bp.temperament,
                   bp.brain_type, bp.llm_model
            FROM bot_personas bp
            WHERE bp.enabled
              AND NOT bp.hospitality
              AND (bp.brain_type = 'RULE'
                   OR EXISTS (SELECT 1 FROM llm_api_keys k
                              WHERE k.provider = bp.brain_type
                                AND (k.unavailable_until IS NULL OR k.unavailable_until <= CURRENT_TIMESTAMP)))
            ORDER BY bp.user_id;
            """;

    private static final String FIND_MMR_BY_USER_IDS = """
            SELECT id, mmr
            FROM users
            WHERE id IN (:userIds);
            """;

    private static final String FIND_BY_ID = """
            SELECT user_id, name, tier, thinking_time_ms, reaction_interval_frames,
                   counter_aggression, enabled, hospitality, temperament, brain_type, llm_model
            FROM bot_personas
            WHERE user_id = :userId;
            """;

    private static final String INSERT = """
            INSERT INTO bot_personas(user_id, name, tier, thinking_time_ms, reaction_interval_frames,
                                     counter_aggression, enabled, hospitality, temperament,
                                     brain_type, llm_model)
            VALUES(:userId, :name, :tier::bot_tier, :thinkingTimeMs, :reactionIntervalFrames,
                   :counterAggression, :enabled, :hospitality, :temperament::bot_temperament,
                   :brainType, :llmModel);
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
                temperament = :temperament::bot_temperament,
                brain_type = :brainType,
                llm_model = :llmModel
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

    public List<BotPersona> findMatchable() {
        return jdbcClient.sql(FIND_MATCHABLE)
                .query(this::map)
                .list();
    }

    /** Mmr per user id for the ids that have a {@code users} row; the others are left out. */
    public Map<Long, Integer> findMmrByUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> mmrByUserId = new HashMap<>();
        jdbcClient.sql(FIND_MMR_BY_USER_IDS)
                .param("userIds", userIds)
                .query((rs, rowNum) -> {
                    mmrByUserId.put(rs.getLong("id"), rs.getInt("mmr"));
                    return null;
                })
                .list();
        return mmrByUserId;
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
                        : requestDto.temperament().name())
                .param("brainType", requestDto.brainType() == null
                        ? BotBrainType.RULE.name()
                        : requestDto.brainType().name())
                .param("llmModel", BotPersona.normalizeLlmModel(requestDto.llmModel()));
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
                BotTemperament.valueOf(rs.getString("temperament")),
                BotBrainType.fromDatabase(rs.getString("brain_type")),
                BotPersona.normalizeLlmModel(rs.getString("llm_model"))
        );
    }
}
