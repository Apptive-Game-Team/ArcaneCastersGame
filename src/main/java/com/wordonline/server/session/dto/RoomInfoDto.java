package com.wordonline.server.session.dto;

import java.time.Instant;

import com.wordonline.server.game.domain.map.GameMap;

public record RoomInfoDto(
        String sessionId,
        Long leftUserId,
        Long rightUserId,
        String serverUrl,
        Instant createdAt,
        GameMap mapType
) {

}
