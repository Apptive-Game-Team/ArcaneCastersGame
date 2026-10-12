package com.wordonline.server.debug.dto;

import com.wordonline.server.game.domain.map.GameMap;

public record DebugGameResponseDto(
        String sessionId,
        GameMap mapType
) {

}
