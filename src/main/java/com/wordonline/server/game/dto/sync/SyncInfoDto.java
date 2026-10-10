package com.wordonline.server.game.dto.sync;

import java.util.List;

import com.wordonline.server.game.dto.frame.FrameInfoDto;
import com.wordonline.server.game.dto.frame.GameEventDto;
import com.wordonline.server.game.dto.frame.SnapshotResponseDto;
import com.wordonline.server.game.dto.frame.projectile.ProjectileDto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SyncInfoDto {
    private final String type = "sync";
    private int remainingTime;
    private int updatedMana;
    private int leftPlayerHp;
    private int rightPlayerHp;
    private final SnapshotResponseDto snapshotResponseDto;
    private final List<ProjectileDto> projectileDtos;
    // Sync replaces the frame message every half second, so it has to carry that frame's events.
    private final List<GameEventDto> events;
    // Frames per second the session runs at. The client sizes its interpolation from it and treats
    // a sync without the field, from an older server, as 20.
    private final int tickRate;

    public SyncInfoDto(FrameInfoDto frameInfoDto, SnapshotResponseDto snapshotResponseDto, int tickRate) {
        this(frameInfoDto.getRemainingTime(), frameInfoDto.getUpdatedMana(), frameInfoDto.getLeftPlayerHp(),
                frameInfoDto.getRightPlayerHp(), snapshotResponseDto, frameInfoDto.getObjects().projectile(),
                frameInfoDto.getEvents(), tickRate);
    }
}
