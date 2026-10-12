package com.wordonline.server.game.domain.map;

import java.util.Random;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wordonline.server.game.config.GameMapProperties;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.repository.PveScenarioRepository;

/**
 * Decides the map of a match, once, before the session object exists. PVE reads the map of the
 * stage the scenario belongs to ({@code stages.map_type}), {@link GameMap#GRASSLAND} when
 * the scenario has no stage. Every other session type follows {@link GameMapProperties}: a
 * forced map, or a fair coin per match between {@code GRASSLAND} and {@code RIVER} when random.
 */
@Component
public class GameMapSelector {

    private final GameMapProperties properties;
    private final RandomGenerator random;
    private final PveScenarioRepository pveScenarioRepository;

    /**
     * {@link Random} lives in {@code java.base}. {@code RandomGenerator.getDefault()} needs the
     * {@code jdk.random} module, which the {@code eclipse-temurin:21-jre} runtime image leaves out.
     */
    @Autowired
    public GameMapSelector(GameMapProperties properties, PveScenarioRepository pveScenarioRepository) {
        this(properties, new Random(), pveScenarioRepository);
    }

    /** The random source is a parameter so that a test can fix it. */
    public GameMapSelector(GameMapProperties properties, RandomGenerator random,
                           PveScenarioRepository pveScenarioRepository) {
        this.properties = properties;
        this.random = random;
        this.pveScenarioRepository = pveScenarioRepository;
    }

    public GameMap choose(SessionType sessionType, Long scenarioId) {
        if (sessionType == SessionType.PVE) {
            return scenarioId == null
                    ? GameMap.GRASSLAND
                    : pveScenarioRepository.findMapType(scenarioId).orElse(GameMap.GRASSLAND);
        }
        if (sessionType == SessionType.Playground) {
            // The developer playground clears everything but the players and the wall when it resets, so
            // river objects would vanish while the river terrain stayed. It is a test bench, not a match.
            return GameMap.GRASSLAND;
        }
        return switch (properties.selection()) {
            case GRASSLAND -> GameMap.GRASSLAND;
            case RIVER -> GameMap.RIVER;
            case RANDOM -> random.nextBoolean() ? GameMap.RIVER : GameMap.GRASSLAND;
        };
    }
}
