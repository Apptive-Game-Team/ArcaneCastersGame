package com.wordonline.server.game.domain.map;

import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wordonline.server.game.config.GameMapProperties;
import com.wordonline.server.game.domain.SessionType;

/**
 * Picks the arena of a match. PVE keeps {@link GameMap#DEFAULT} always, because PVE scenarios
 * place objects at fixed positions on the right side of the open arena. Every other session type
 * follows {@link GameMapProperties}: a forced map, or a fair coin per match when random.
 */
@Component
public class GameMapSelector {

    private final GameMapProperties properties;
    private final RandomGenerator random;

    @Autowired
    public GameMapSelector(GameMapProperties properties) {
        this(properties, RandomGenerator.getDefault());
    }

    /** The random source is a parameter so that a test can fix it. */
    public GameMapSelector(GameMapProperties properties, RandomGenerator random) {
        this.properties = properties;
        this.random = random;
    }

    public GameMap choose(SessionType sessionType) {
        if (sessionType == SessionType.PVE) {
            return GameMap.DEFAULT;
        }
        return switch (properties.selection()) {
            case DEFAULT -> GameMap.DEFAULT;
            case RIVER -> GameMap.RIVER;
            case RANDOM -> random.nextBoolean() ? GameMap.RIVER : GameMap.DEFAULT;
        };
    }
}
