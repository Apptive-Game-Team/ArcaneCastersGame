package com.wordonline.server.bot.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.wordonline.server.game.domain.object.Vector3;

/**
 * The closed menu one LLM request chooses from, built by code from a bot snapshot.
 *
 * <p>The model never writes a magic id or a coordinate. It picks an option key, and the key maps
 * back to what code put behind it, so the worst a wrong answer can do is pick a legal but poor
 * option. {@code magics} holds only affordable magics in hand; {@link #WAIT} is offered on top.
 *
 * @param state compact JSON-ready description of the board, for the provider's state field
 */
public record LlmCandidates(
        Map<String, MagicOption> magics,
        Map<String, TargetOption> targets,
        Map<String, Object> state
) {
    public static final String WAIT = "wait";
    static final String WAIT_DESCRIPTION = "Cast nothing now and save mana for a later turn.";

    public LlmCandidates {
        magics = Collections.unmodifiableMap(new LinkedHashMap<>(magics));
        targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
        state = Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    public record MagicOption(long magicId, String name, int manaCost, String description) {
    }

    public record TargetOption(Vector3 position, String description) {
    }

    /** False when the only thing the bot could do is wait, in which case no request is sent. */
    public boolean hasCastableMagic() {
        return !magics.isEmpty() && !targets.isEmpty();
    }

    /** Magic option key to description, {@link #WAIT} last. */
    public Map<String, String> magicCriteria() {
        Map<String, String> criteria = new LinkedHashMap<>();
        magics.forEach((key, option) -> criteria.put(key, option.description()));
        criteria.put(WAIT, WAIT_DESCRIPTION);
        return criteria;
    }

    public Map<String, String> targetCriteria() {
        Map<String, String> criteria = new LinkedHashMap<>();
        targets.forEach((key, option) -> criteria.put(key, option.description()));
        return criteria;
    }
}
