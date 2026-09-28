package com.wordonline.server.bot.dto;

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
        BotTemperament temperament
) {
}
