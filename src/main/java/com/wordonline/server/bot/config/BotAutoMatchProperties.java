package com.wordonline.server.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the self-play bot games the server keeps running on its own.
 *
 * <p>{@link #targetGames()} is the number of sessions the scheduler tops the server back up to
 * on every check; the default of 1 reproduces the original "one bot game while idle" behaviour.
 * Raise it to generate a synthetic load for profiling.
 *
 * <p>The check interval itself stays in {@code @Scheduled(fixedDelayString = ...)} because that
 * annotation argument has to be a string literal.
 */
@ConfigurationProperties(prefix = "bot.auto-match")
public record BotAutoMatchProperties(
        Boolean enabled,
        Integer targetGames,
        Source source) {

    /**
     * Who creates the bot-versus-bot games. {@code LOBBY}: the lobby feeds bot tickets through
     * matchmaking and this server creates nothing on its own. {@code GAME_SERVER}: the scheduler
     * on this server creates them, for capacity tests and local runs without a lobby.
     */
    public enum Source {
        LOBBY,
        GAME_SERVER
    }

    private static final boolean FALLBACK_ENABLED = true;
    private static final int FALLBACK_TARGET_GAMES = 1;

    public BotAutoMatchProperties {
        enabled = enabled == null ? FALLBACK_ENABLED : enabled;
        targetGames = targetGames == null ? FALLBACK_TARGET_GAMES : targetGames;
        source = source == null ? Source.LOBBY : source;
    }
}
