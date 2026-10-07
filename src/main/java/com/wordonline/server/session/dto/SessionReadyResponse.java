package com.wordonline.server.session.dto;

import com.wordonline.server.game.domain.map.GameMap;

/**
 * @param instanceId boot generation of the process that owns the created session. The lobby
 *                   stores it on the ticket and later compares it with the id published on
 *                   the {@code servers} row; a mismatch means this process restarted and the
 *                   session no longer exists.
 * @param mapType    map of the session: {@code GRASSLAND}, {@code RIVER}, {@code FORTRESS},
 *                   {@code GATE} or {@code FOREST}. Decided before the session was created and
 *                   the same value the loop spawns terrain from. {@code null} only when no session
 *                   was created (the server was not active).
 */
public record SessionReadyResponse(
        String attemptId,
        String sessionId,
        boolean ready,
        String serverUrl,
        String webSocketUrl,
        String instanceId,
        GameMap mapType
) {
}
