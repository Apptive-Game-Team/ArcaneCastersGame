package com.wordonline.server.game.repository;

import com.wordonline.server.game.domain.object.prefab.PrefabType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tag lookups are memoised because they sit inside the bot's scoring loop: BotBrain scores every
 * affordable recipe against every visible enemy 2.5 times a second per bot, and each pair used to
 * cost two SQL round trips. Tag data is reference data edited by admins, so the cache is cleared
 * through {@link #invalidateCache()} the same way ParameterService and DatabaseMagicParser expose
 * theirs, rather than being given a time to live.
 */
@Repository
@RequiredArgsConstructor
public class TagRepository {

    private static final String FIND_MAGIC_TAGS = """
            SELECT t.name
            FROM magic_tags mt
            JOIN tags t ON t.id = mt.tag_id
            WHERE mt.magic_id = :magicId;
            """;

    private static final String FIND_GAME_OBJECT_TAGS = """
            SELECT t.name
            FROM game_object_tags got
            JOIN game_objects go ON go.id = got.game_object_id
            JOIN tags t ON t.id = got.tag_id
            WHERE go.name = :gameObjectName;
            """;

    private static final String COUNTER_WEIGHT = """
            SELECT COALESCE(SUM(tcr.weight), 0)
            FROM tag_counter_rules tcr
            JOIN tags attacker ON attacker.id = tcr.attacker_tag_id
            JOIN tags target ON target.id = tcr.target_tag_id
            WHERE attacker.name IN (:attackerTags)
              AND target.name IN (:targetTags);
            """;

    private final JdbcClient jdbcClient;

    private volatile Map<Long, Set<String>> magicTagsCache = new ConcurrentHashMap<>();
    private volatile Map<PrefabType, Set<String>> prefabTagsCache = new ConcurrentHashMap<>();
    private volatile Map<String, Set<String>> gameObjectTagsCache = new ConcurrentHashMap<>();
    private volatile Map<CounterWeightKey, Double> counterWeightCache = new ConcurrentHashMap<>();

    /**
     * Reads every key the bots have already asked for again, then swaps the new maps in. Clearing
     * instead left the next bot think to refill them one round trip at a time. The new maps are
     * filled before any is swapped, so a failed reload keeps all the old values.
     */
    public void invalidateCache() {
        Map<Long, Set<String>> magicTags = new ConcurrentHashMap<>();
        magicTagsCache.keySet().forEach(magicId -> magicTags.put(magicId, loadMagicTags(magicId)));

        Map<String, Set<String>> gameObjectTags = new ConcurrentHashMap<>();
        gameObjectTagsCache.keySet().forEach(name -> gameObjectTags.put(name, loadGameObjectTags(name)));
        Map<PrefabType, Set<String>> prefabTags = new ConcurrentHashMap<>();
        prefabTagsCache.keySet().forEach(type -> prefabTags.put(type,
                gameObjectTags.computeIfAbsent(toGameObjectName(type), this::loadGameObjectTags)));

        Map<CounterWeightKey, Double> counterWeights = new ConcurrentHashMap<>();
        counterWeightCache.keySet().forEach(key -> counterWeights.put(key,
                loadCounterWeight(key.attackerTags(), key.targetTags())));

        magicTagsCache = magicTags;
        gameObjectTagsCache = gameObjectTags;
        prefabTagsCache = prefabTags;
        counterWeightCache = counterWeights;
    }

    public Set<String> getMagicTags(long magicId) {
        return magicTagsCache.computeIfAbsent(magicId, this::loadMagicTags);
    }

    public Set<String> getGameObjectTags(PrefabType prefabType) {
        // Keyed by the enum as well as by the name, so the repeated lookup also skips rebuilding
        // the snake_case game object name.
        return prefabTagsCache.computeIfAbsent(prefabType, type -> getGameObjectTags(toGameObjectName(type)));
    }

    public Set<String> getGameObjectTags(String gameObjectName) {
        return gameObjectTagsCache.computeIfAbsent(gameObjectName, this::loadGameObjectTags);
    }

    /**
     * The weight is a pure function of the two tag sets: the SQL sums the rules matching any
     * attacker tag against any target tag, so order and duplicates in the arguments do not change
     * the result and set equality is the right cache key.
     */
    public double getCounterWeight(Collection<String> attackerTags, Collection<String> targetTags) {
        if (attackerTags.isEmpty() || targetTags.isEmpty()) {
            return 0.0;
        }
        CounterWeightKey key = new CounterWeightKey(Set.copyOf(attackerTags), Set.copyOf(targetTags));
        return counterWeightCache.computeIfAbsent(
                key, k -> loadCounterWeight(k.attackerTags(), k.targetTags()));
    }

    protected Set<String> loadMagicTags(long magicId) {
        return Set.copyOf(jdbcClient.sql(FIND_MAGIC_TAGS)
                .param("magicId", magicId)
                .query(String.class)
                .list());
    }

    protected Set<String> loadGameObjectTags(String gameObjectName) {
        return Set.copyOf(jdbcClient.sql(FIND_GAME_OBJECT_TAGS)
                .param("gameObjectName", gameObjectName)
                .query(String.class)
                .list());
    }

    protected double loadCounterWeight(Collection<String> attackerTags, Collection<String> targetTags) {
        return jdbcClient.sql(COUNTER_WEIGHT)
                .param("attackerTags", List.copyOf(attackerTags))
                .param("targetTags", List.copyOf(targetTags))
                .query(Double.class)
                .optional()
                .orElse(0.0);
    }

    /**
     * The row name a prefab type is stored under. game_objects and magics share one name space, so
     * this is also how a prefab is matched to the magic that creates it.
     */
    public static String toGameObjectName(PrefabType prefabType) {
        String name = prefabType.name();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    private record CounterWeightKey(Set<String> attackerTags, Set<String> targetTags) {
    }
}
