package com.wordonline.server.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How many frames per second a game loop runs when the session does not ask for its own rate.
 * Property: {@code game.tick-rate}. The shared servers stay at 20; a locally hosted server can run
 * faster because every timer in the simulation counts seconds, not frames.
 */
@ConfigurationProperties(prefix = "game")
public record GameTickProperties(Integer tickRate) {

    public static final int DEFAULT_TICK_RATE = 20;

    public GameTickProperties {
        tickRate = tickRate == null ? DEFAULT_TICK_RATE : validate(tickRate);
    }

    public static int validate(int tickRate) {
        if (tickRate <= 0) {
            throw new IllegalArgumentException("tickRate must be positive: " + tickRate);
        }
        return tickRate;
    }
}
