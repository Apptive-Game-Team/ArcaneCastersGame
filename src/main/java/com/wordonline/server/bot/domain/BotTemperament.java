package com.wordonline.server.bot.domain;

/**
 * How a bot behaves on the emote channel. The database stores only the name; which emote each
 * situation produces, and how often the bot bothers at all, is the table in
 * {@link com.wordonline.server.game.domain.bot.BotEmoteDirector}.
 */
public enum BotTemperament {
    /** Friendly. Greets, laughs when ahead, is startled when behind, and never taunts. */
    WARM,
    /** Taunts when ahead and rarely lets a lead pass without saying so. */
    SMUG,
    /** Cries when behind and is startled easily. */
    TIMID,
    /** Greets occasionally and otherwise says nothing. */
    STOIC
}
