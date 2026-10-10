package com.wordonline.server.game.service.pve;

import com.wordonline.server.game.dto.pve.PveStateDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The PVE state channels of one match: channel name to its latest value and seq. Channel names
 * are plain strings, so a new channel needs no change here. Not thread-safe; the game loop thread
 * owns it.
 */
public class PveStateStore {

    public static final String BGM = "bgm";

    private final Map<String, PveStateDto> states = new HashMap<>();
    private int lastSeq;

    /**
     * Sets {@code channel} to {@code value} and returns the new state with the next seq. Returns
     * empty, without raising seq, when the channel already holds that value.
     */
    public Optional<PveStateDto> set(String channel, String value) {
        Objects.requireNonNull(channel, "channel");
        PveStateDto current = states.get(channel);
        if (current != null && Objects.equals(current.value(), value)) {
            return Optional.empty();
        }
        PveStateDto next = new PveStateDto(channel, value, ++lastSeq);
        states.put(channel, next);
        return Optional.of(next);
    }

    /** Every channel set so far, ordered by seq. */
    public List<PveStateDto> snapshot() {
        List<PveStateDto> result = new ArrayList<>(states.values());
        result.sort(Comparator.comparingInt(PveStateDto::seq));
        return result;
    }
}
