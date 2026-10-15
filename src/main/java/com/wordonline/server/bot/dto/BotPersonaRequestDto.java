package com.wordonline.server.bot.dto;

import com.wordonline.server.bot.domain.BotBrainType;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;

public record BotPersonaRequestDto(
        long userId,
        String name,
        BotTier tier,
        int thinkingTimeMs,
        int reactionIntervalFrames,
        double counterAggression,
        Boolean enabled,
        Boolean hospitality,
        BotTemperament temperament,
        BotBrainType brainType,
        String llmModel
) {
    /** A request from a client that does not send the brain choice keeps the rule brain. */
    public BotPersonaRequestDto(long userId, String name, BotTier tier, int thinkingTimeMs,
                                int reactionIntervalFrames, double counterAggression, Boolean enabled,
                                Boolean hospitality, BotTemperament temperament) {
        this(userId, name, tier, thinkingTimeMs, reactionIntervalFrames, counterAggression, enabled,
                hospitality, temperament, null, null);
    }
}
