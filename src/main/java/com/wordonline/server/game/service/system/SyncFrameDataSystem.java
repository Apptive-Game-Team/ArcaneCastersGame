package com.wordonline.server.game.service.system;

import java.util.List;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.wordonline.server.game.dto.frame.FrameInfoDto;
import com.wordonline.server.game.dto.frame.SnapshotResponseDto;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class SyncFrameDataSystem extends FrameDataSystem {

    @Override
    public void earlyUpdate(GameContext gameContext) {
        super.earlyUpdate(gameContext);
    }

    @Override
    public void lateUpdate(GameContext gameContext) {
        if (!GameLoop.isSyncFrame(gameContext.getFrameNum(), gameContext.getTickRate())) {
            super.lateUpdate(gameContext);
        } else {
            SnapshotResponseDto leftSnapshotResponseDto = gameContext.getGameLoop().getLastSnapshot(gameContext.getSessionObject().getLeftUserId());
            SnapshotResponseDto rightSnapshotResponseDto = gameContext.getGameLoop().getLastSnapshot(gameContext.getSessionObject().getRightUserId());

            gameContext.getSessionObject().sendFrameInfo(
                    gameContext.getSessionObject().getLeftUserId(),
                    getLeftFrameInfoDto().toSyncDto(leftSnapshotResponseDto, gameContext.getTickRate())
            );
            gameContext.getSessionObject().sendFrameInfo(
                    gameContext.getSessionObject().getRightUserId(),
                    getRightFrameInfoDto().toSyncDto(rightSnapshotResponseDto, gameContext.getTickRate())
            );

            // Broadcast sync info to spectators (userId = 0)
            // Use left player's snapshot as the canonical state for spectators, minus the hand:
            // SnapshotResponseDto carries myCards, so sending the left player's snapshot as it
            // stands hands every spectator that player's cards. createBroadcastDto zeroes
            // updatedMana for the same reason.
            if (getBroadcastFrameInfoDto() != null) {
                gameContext.getSessionObject().broadcastFrameInfo(
                        getBroadcastFrameInfoDto().toSyncDto(withoutHand(leftSnapshotResponseDto), gameContext.getTickRate())
                );
            }
        }
    }

    private static SnapshotResponseDto withoutHand(SnapshotResponseDto snapshotResponseDto) {
        return new SnapshotResponseDto(snapshotResponseDto.frame(), snapshotResponseDto.objects(), List.of());
    }
}
