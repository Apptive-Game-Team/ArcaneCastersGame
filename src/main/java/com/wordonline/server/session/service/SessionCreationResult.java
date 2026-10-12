package com.wordonline.server.session.service;

import com.wordonline.server.game.domain.map.GameMap;

/**
 * @param mapType the map of the session; {@code null} only for a repeated attempt whose session has
 *                already ended and left the registry
 */
public record SessionCreationResult(
        String attemptId,
        String sessionId,
        boolean ready,
        GameMap mapType
) {
}
