package com.wordonline.server.game.dto.frame;

import java.util.List;

public record SnapshotResponseDto(
        int frame,
        List<SnapshotObjectDto> objects,
        List<Long> myCards
) {}
