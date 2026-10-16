package com.wordonline.server.bot.llm;

import java.util.Locale;

import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.object.Vector3;

/**
 * The play key a provider picked. Mapped back to a cast only through {@link LlmCandidates}, so an
 * unknown key or a {@code wait} never turns into a cast.
 *
 * @param reason     the model's own short reason, or null (jev gives none)
 * @param confidence the provider's confidence in the choice, or null
 * @param aimKey     where the chosen card should land, from the play's aims, or null
 */
public record LlmAnswer(String playKey, String reason, Double confidence, String aimKey) {

    public LlmAnswer(String playKey, String reason, Double confidence) {
        this(playKey, reason, confidence, null);
    }

    /** Longest reason the bot-thought DTO is given. */
    public static final int MAX_REASON_LENGTH = 120;

    /** The cast this answer names, or null when it names none or names a play that was not offered. */
    public BotBrain.InputDecision toDecision(LlmCandidates candidates, String ruleId) {
        if (playKey == null || LlmCandidates.WAIT.equals(playKey)) {
            return null;
        }
        LlmCandidates.Play play = candidates.plays().get(playKey);
        if (play == null) {
            return null;
        }
        String text = reason != null && !reason.isBlank()
                ? reason.strip()
                : (confidence == null ? "" : String.format(Locale.ROOT, "(%.2f) ", confidence)) + play.description();
        return new BotBrain.InputDecision(play.magicId(), new Vector3(play.targetFor(aimKey)), ruleId, truncate(text));
    }

    static String truncate(String text) {
        String singleLine = text.replaceAll("\\s+", " ");
        return singleLine.length() <= MAX_REASON_LENGTH ? singleLine : singleLine.substring(0, MAX_REASON_LENGTH);
    }
}
