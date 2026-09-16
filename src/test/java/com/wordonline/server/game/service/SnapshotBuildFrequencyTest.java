package com.wordonline.server.game.service;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import com.wordonline.server.game.service.system.BotAgentSystem;
import com.wordonline.server.game.service.system.ComponentUpdateSystem;
import com.wordonline.server.game.service.system.FeverTimeSystem;
import com.wordonline.server.game.service.system.GameActionSystem;
import com.wordonline.server.game.service.system.GameObjectAddRemoteSystem;
import com.wordonline.server.game.service.system.GameObjectStateInitialSystem;
import com.wordonline.server.game.service.system.PhysicSystem;
import com.wordonline.server.game.service.system.SyncFrameDataSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The snapshot is a full walk of every game object, and SyncFrameDataSystem is its only reader,
 * on one frame in ten. These hold the loop and the sync system to the same period.
 */
class SnapshotBuildFrequencyTest {

    private final GameContext gameContext = mock(GameContext.class, RETURNS_DEEP_STUBS);
    private final AtomicInteger snapshotsBuilt = new AtomicInteger();

    @Test
    void buildsTheSnapshotOnlyOnSyncFrames() {
        WordOnlineLoop loop = loop();

        for (int frameNum = 1; frameNum <= GameLoop.SYNC_FRAME_INTERVAL * 3; frameNum++) {
            when(gameContext.getFrameNum()).thenReturn(frameNum);
            loop.update();
        }

        assertThat(snapshotsBuilt.get()).isEqualTo(3);
    }

    @Test
    void buildsNoSnapshotOnTheFramesBetweenTwoSyncFrames() {
        WordOnlineLoop loop = loop();

        for (int frameNum = 1; frameNum < GameLoop.SYNC_FRAME_INTERVAL; frameNum++) {
            when(gameContext.getFrameNum()).thenReturn(frameNum);
            loop.update();
        }

        assertThat(snapshotsBuilt.get()).isZero();
    }

    @Test
    void agreesWithTheFrameNumbersTheSyncSystemSendsASnapshotOn() {
        for (int frameNum = 0; frameNum < 100; frameNum++) {
            assertThat(GameLoop.isSyncFrame(frameNum)).isEqualTo(frameNum % GameLoop.SYNC_FRAME_INTERVAL == 0);
        }
    }

    private WordOnlineLoop loop() {
        return new WordOnlineLoop(mock(MmrService.class), mock(UserService.class), gameContext,
                mock(Parameters.class), mock(SyncFrameDataSystem.class), mock(GameActionSystem.class),
                mock(BotAgentSystem.class),
                mock(FeverTimeSystem.class), mock(GameObjectStateInitialSystem.class),
                mock(ComponentUpdateSystem.class), mock(PhysicSystem.class),
                mock(GameObjectAddRemoteSystem.class), mock(DatabaseMagicParser.class),
                mock(BotPersonaService.class), mock(BotCounterEvaluator.class)) {
            @Override
            protected void buildSnapshot() {
                snapshotsBuilt.incrementAndGet();
            }
        };
    }
}
