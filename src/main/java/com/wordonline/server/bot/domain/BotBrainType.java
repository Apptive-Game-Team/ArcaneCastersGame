package com.wordonline.server.bot.domain;

/**
 * Which decision maker plays a bot persona.
 *
 * <p>{@code RULE} is the rule-based {@code BotBrain}. {@code JEV} and {@code OPENROUTER} ask that
 * provider which magic in hand to cast and where, and fall back to the rule brain when the provider
 * cannot answer.
 */
public enum BotBrainType {
    RULE,
    JEV,
    OPENROUTER;

    /**
     * Reads the stored column. A blank or unknown value plays as {@code RULE}, so a row written by a
     * newer admin build never stops a bot from playing.
     */
    public static BotBrainType fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            return RULE;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RULE;
        }
    }

    public boolean usesLlm() {
        return this != RULE;
    }
}
