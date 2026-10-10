package com.wordonline.server.game.service;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
 * on the first frame and then once every half second of game time. These hold the loop and the
 * sync system to the same period at any tick rate.
 */
class SnapshotBuildFrequencyTest {

    private final GameContext gameContext = mock(GameContext.class, RETURNS_DEEP_STUBS);
    private final AtomicInteger snapshotsBuilt = new AtomicInteger();

    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void buildsTheSnapshotOnTheFirstFrameAndEveryHalfSecond(int tickRate) {
        when(gameContext.getTickRate()).thenReturn(tickRate);
        WordOnlineLoop loop = loop();

        // 1.5 seconds of game time
        for (int frameNum = 1; frameNum <= tickRate * 3 / 2; frameNum++) {
            when(gameContext.getFrameNum()).thenReturn(frameNum);
            loop.update();
        }

        assertThat(snapshotsBuilt.get()).isEqualTo(1 + 3);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void buildsNoSnapshotBetweenTheFirstFrameAndHalfASecond(int tickRate) {
        when(gameContext.getTickRate()).thenReturn(tickRate);
        WordOnlineLoop loop = loop();

        for (int frameNum = 2; frameNum < tickRate / 2; frameNum++) {
            when(gameContext.getFrameNum()).thenReturn(frameNum);
            loop.update();
        }

        assertThat(snapshotsBuilt.get()).isZero();
    }

    @Test
    void syncsEveryHalfSecondOfGameTime() {
        assertThat(GameLoop.syncIntervalFrames(20)).isEqualTo(10);
        assertThat(GameLoop.syncIntervalFrames(60)).isEqualTo(30);
        assertThat(GameLoop.syncIntervalFrames(120)).isEqualTo(60);
        assertThat(GameLoop.isSyncFrame(1, 60)).isTrue();
        assertThat(GameLoop.isSyncFrame(29, 60)).isFalse();
        assertThat(GameLoop.isSyncFrame(30, 60)).isTrue();
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
