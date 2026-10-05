package com.wordonline.server.game.dto.pve;

import java.util.List;

/**
 * One announcement of a PVE script event. {@code seq} counts the events that had lines in this
 * match, starting at 1, so a client can tell which ones it already has.
 */
public record PveScriptEventDto(
        String key,
        int speakerObjectId,
        List<String> lines,
        int seq
) {
}
