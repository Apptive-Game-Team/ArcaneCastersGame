package com.wordonline.server.game.service;

import com.wordonline.server.game.repository.ParameterRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ParameterService {

    private final ParameterRepository parameterRepository;

    protected ParameterService(ParameterRepository parameterRepository) {
        this.parameterRepository = parameterRepository;
    }

    /**
     * Every parameter row, read in one query when the server starts and keyed
     * {@code "<object>.<parameter>"} as {@link ParameterRepository#snapshot()} returns it. While it is
     * loaded, a lookup never touches the database: a key that is absent here has no row.
     *
     * <p>{@link #invalidateCache()} swaps in a freshly read map rather than clearing this one, so a
     * game loop thread reading mid-reload sees either the old values or the new ones, never an
     * empty map. It is {@code null} only when loading failed; lookups then fall back to
     * {@link #parameterCaches}.
     */
    private volatile Map<String, Double> loadedParameters;

    /**
     * Fallback for when {@link #loadedParameters} could not be read. Both hits and misses are
     * cached, the miss as an {@link Optional#empty()}.
     * A parameter the database has no row for is a normal outcome for callers such as
     * {@code BotSpellStats} and the prefab initializers, and caching only hits left every one of
     * those lookups doing a blocking JDBC round trip on the game loop thread, forever, inside a
     * 50ms frame budget.
     *
     * <p>The object key is lower-cased before it reaches either the cache or the query, because
     * callers disagree: some pass {@code card.name()} and some {@code card.name().toLowerCase()}.
     * The query has always lower-cased its bind value, so without normalising here the same row
     * ends up cached twice under two keys.
     */
    private final Map<String, Map<String, Optional<Double>>> parameterCaches = new ConcurrentHashMap<>();

    @PostConstruct
    void loadAll() {
        try {
            loadedParameters = parameterRepository.snapshot();
            log.info("[Parameter:Loaded] {} values", loadedParameters.size());
        } catch (RuntimeException e) {
            // Starting with per-row lookups is slower but still plays; refusing to start is not.
            loadedParameters = null;
            log.warn("[Parameter:Loading] could not read all parameters, falling back to per-row lookups", e);
        }
    }

    public void invalidateCache() {
        parameterCaches.clear();
        loadAll();
    }

    public double getValue(String gameObject, String parameterName) {
        return findValue(gameObject, parameterName)
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found: " + gameObject + ", " + parameterName));
    }

    public double getValueOrDefault(String gameObject, String parameterName, double defaultValue) {
        return findValue(gameObject, parameterName).orElse(defaultValue);
    }

    private Optional<Double> findValue(String gameObject, String parameterName) {
        String objectKey = gameObject.toLowerCase();

        Map<String, Double> loaded = loadedParameters;
        if (loaded != null) {
            return Optional.ofNullable(loaded.get(objectKey + "." + parameterName));
        }

        Map<String, Optional<Double>> objectParameters = parameterCaches.get(objectKey);
        if (objectParameters != null) {
            Optional<Double> cached = objectParameters.get(parameterName);
            if (cached != null) {
                return cached;
            }
        }

        Optional<Double> valueFromDb = parameterRepository.getParameterValue(objectKey, parameterName);
        parameterCaches
                .computeIfAbsent(objectKey, key -> new ConcurrentHashMap<>())
                .put(parameterName, valueFromDb);
        return valueFromDb;
    }
}
