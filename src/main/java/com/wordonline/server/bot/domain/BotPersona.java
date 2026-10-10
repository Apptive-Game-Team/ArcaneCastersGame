package com.wordonline.server.bot.domain;

import com.wordonline.server.game.service.GameLoop;

public record BotPersona(
        long userId,
        String name,
        BotTier tier,
        int thinkingTimeMs,
        int reactionIntervalFrames,
        double counterAggression,
        boolean enabled,
        boolean hospitality,
        BotTemperament temperament
) {
    public static final BotPersona DEFAULT = new BotPersona(
            0,
            "Default Bot",
            BotTier.BEGINNER,
            250,
            8,
            0.25,
            true,
            false,
            BotTemperament.WARM
    );

    /**
     * The temperament the emote director reads. A bot with no row of its own still plays, so the
     * default is spelled out here rather than left null for every caller to guess at; it matches
     * the database column's own default.
     */
    public BotTemperament normalizedTemperament() {
        return temperament == null ? BotTemperament.WARM : temperament;
    }

    public int normalizedReactionIntervalFrames() {
        return Math.max(1, reactionIntervalFrames);
    }

    /**
     * How often the bot looks at the board, in seconds. reaction_interval_frames is stored in
     * 20-FPS frames, so the same persona reacts at the same pace whatever the session's tick rate.
     */
    public float reactionIntervalSeconds() {
        return normalizedReactionIntervalFrames() * GameLoop.LEGACY_FRAME_SECONDS;
    }

    public int normalizedThinkingTimeMs() {
        return Math.max(0, thinkingTimeMs);
    }

    /**
     * Negative values are meaningful, not a mistake to clamp away. The sign chooses which
     * direction of the matchup the bot is scoring: a positive persona prefers the play that
     * beats what is on the field, a negative one prefers the play that field answers best.
     * Only the hospitality bot uses the negative half - it has to lose to the units already
     * standing there while still putting a real unit down.
     */
    public double normalizedCounterAggression() {
        return Math.max(-1.0, Math.min(1.0, counterAggression));
    }
}
