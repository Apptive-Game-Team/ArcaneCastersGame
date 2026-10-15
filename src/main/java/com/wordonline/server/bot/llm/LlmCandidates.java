package com.wordonline.server.bot.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.wordonline.server.game.domain.object.Vector3;

/**
 * The closed menu one LLM request chooses from, built by code from a bot snapshot.
 *
 * <p>Each option is a complete play: one magic and the point it lands on, keyed {@code p0..pN}.
 * The model never writes a magic id or a coordinate. It picks a key, and the key maps back to what
 * code put behind it, so the worst a wrong answer can do is pick a legal but poor play. {@link #WAIT}
 * is offered only when {@code waitDescription} is set.
 *
 * @param waitDescription what waiting buys this turn, or null when waiting is not offered
 * @param state           compact JSON-ready description of the board, for the provider's state field
 */
public record LlmCandidates(
        Map<String, Play> plays,
        String waitDescription,
        Map<String, Object> state
) {
    public static final String WAIT = "wait";

    public LlmCandidates {
        plays = Collections.unmodifiableMap(new LinkedHashMap<>(plays));
        state = Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    /** One magic cast at one point, described in words the model reads without the code. */
    public record Play(long magicId, String magicName, int manaCost, Vector3 target, String description) {
    }

    public boolean offersWait() {
        return waitDescription != null;
    }

    /** False when there is no play at all, in which case no request is sent. */
    public boolean hasPlays() {
        return !plays.isEmpty();
    }

    /** Play key to description, {@link #WAIT} last when offered. */
    public Map<String, String> criteria() {
        Map<String, String> criteria = new LinkedHashMap<>();
        plays.forEach((key, play) -> criteria.put(key, play.description()));
        if (offersWait()) {
            criteria.put(WAIT, waitDescription);
        }
        return criteria;
    }

    /** The description behind a key, {@link #WAIT} included, or null for an unknown key. */
    public String describe(String key) {
        if (WAIT.equals(key)) {
            return waitDescription;
        }
        Play play = key == null ? null : plays.get(key);
        return play == null ? null : play.description();
    }
}
