package com.wordonline.server.bot.llm;

import java.util.Locale;

import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.object.Vector3;

/**
 * The option keys a provider picked. Mapped back to a cast only through {@link LlmCandidates}, so
 * an unknown key, a {@code wait}, or a missing target never turns into a cast.
 *
 * @param reason     the model's own short reason, or null (jev gives none)
 * @param confidence the provider's confidence in the magic choice, or null
 */
public record LlmAnswer(String magicKey, String targetKey, String reason, Double confidence) {

    /** Longest reason the bot-thought DTO is given. */
    public static final int MAX_REASON_LENGTH = 120;

    /** The cast this answer names, or null when it names none or names an option that was not offered. */
    public BotBrain.InputDecision toDecision(LlmCandidates candidates, String ruleId) {
        if (magicKey == null || LlmCandidates.WAIT.equals(magicKey)) {
            return null;
        }
        LlmCandidates.MagicOption magic = candidates.magics().get(magicKey);
        LlmCandidates.TargetOption target = targetKey == null ? null : candidates.targets().get(targetKey);
        if (magic == null || target == null) {
            return null;
        }
        String text = reason != null && !reason.isBlank()
                ? reason.strip()
                : magic.name() + " -> " + target.description()
                        + (confidence == null ? "" : String.format(Locale.ROOT, " (confidence %.2f)", confidence));
        return new BotBrain.InputDecision(magic.magicId(), new Vector3(target.position()), ruleId, truncate(text));
    }

    static String truncate(String text) {
        String singleLine = text.replaceAll("\\s+", " ");
        return singleLine.length() <= MAX_REASON_LENGTH ? singleLine : singleLine.substring(0, MAX_REASON_LENGTH);
    }
}
