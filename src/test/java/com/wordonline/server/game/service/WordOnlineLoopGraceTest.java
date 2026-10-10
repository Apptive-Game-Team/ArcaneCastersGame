package com.wordonline.server.game.service;

import com.wordonline.server.bot.service.BotPersonaService;
import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.result.ResultDto;
import com.wordonline.server.game.dto.result.ResultMmrDto;
import com.wordonline.server.game.dto.result.ResultType;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import com.wordonline.server.game.service.system.BotAgentSystem;
import com.wordonline.server.game.service.system.ComponentUpdateSystem;
import com.wordonline.server.game.service.system.FeverTimeSystem;
import com.wordonline.server.game.service.system.GameActionSystem;
import com.wordonline.server.game.service.system.GameObjectAddRemoteSystem;
import com.wordonline.server.game.service.system.GameObjectStateInitialSystem;
import com.wordonline.server.game.service.system.PhysicSystem;
import com.wordonline.server.game.service.system.SyncFrameDataSystem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WordOnlineLoopGraceTest {

    private static final int TICK_RATE = 20;

    private final GameContext gameContext = mock(GameContext.class);
    private final GameTimer gameTimer = mock(GameTimer.class);
    private final SessionObject sessionObject = mock(SessionObject.class);
    private final MmrService mmrService = mock(MmrService.class);
    private final BotAgentSystem botSystem = mock(BotAgentSystem.class);
    private final SyncFrameDataSystem frameDataSystem = mock(SyncFrameDataSystem.class);
    private final ResultChecker resultChecker = new ResultChecker(sessionObject);

    @Test
    void broadcastsOnceAfterTwentyFramesAndKeepsTheFirstLoser() {
        WordOnlineLoop loop = loop();
        resultChecker.setLoser(Master.LeftPlayer);

        update(loop, 1);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any(ResultDto.class));
        verify(botSystem, never()).update(gameContext);

        resultChecker.setLoser(Master.RightPlayer);
        for (int frameNum = 2; frameNum <= TICK_RATE; frameNum++) {
            update(loop, frameNum);
        }
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any(ResultDto.class));

        update(loop, TICK_RATE + 1);
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(ResultDto.class));
        update(loop, TICK_RATE + 2);
        assertThat(resultChecker.getLoser()).isEqualTo(Master.LeftPlayer);
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(ResultDto.class));
        verify(mmrService, times(1)).updateMatchResult(1L, 2L, ResultType.Lose);
        verify(frameDataSystem, times(TICK_RATE + 2)).lateUpdate(gameContext);
    }

    @Test
    void timedOutMatchResolvesOnlyOnceAndUsesTheSameGracePeriod() {
        PlayerData leftPlayerData = new PlayerData(null);
        PlayerData rightPlayerData = new PlayerData(null);
        leftPlayerData.hp = 10;
        rightPlayerData.hp = 20;
        when(gameContext.getGameSessionData())
                .thenReturn(new GameSessionData(leftPlayerData, rightPlayerData));
        when(gameTimer.isEnd()).thenReturn(true);
        WordOnlineLoop loop = loop();
        clearInvocations(gameContext, gameTimer);

        update(loop, 1);
        assertThat(resultChecker.getLoser()).isEqualTo(Master.LeftPlayer);
        verify(sessionObject, never()).sendFrameInfo(anyLong(), any(ResultDto.class));

        leftPlayerData.hp = 30;
        for (int frameNum = 2; frameNum <= TICK_RATE + 1; frameNum++) {
            update(loop, frameNum);
        }

        assertThat(resultChecker.getLoser()).isEqualTo(Master.LeftPlayer);
        verify(gameTimer, times(1)).isEnd();
        verify(sessionObject, times(2)).sendFrameInfo(anyLong(), any(ResultDto.class));
    }

    private WordOnlineLoop loop() {
        when(gameContext.getResultChecker()).thenReturn(resultChecker);
        when(gameContext.getTickRate()).thenReturn(TICK_RATE);
        when(gameContext.getGameTimer()).thenReturn(gameTimer);
        when(sessionObject.getLeftUserId()).thenReturn(1L);
        when(sessionObject.getRightUserId()).thenReturn(2L);
        when(sessionObject.getSessionType()).thenReturn(SessionType.PVP);
        when(mmrService.updateMatchResult(anyLong(), anyLong(), any(ResultType.class)))
                .thenReturn(new ResultMmrDto((short) 0, (short) 0, (short) 0, (short) 0));

        WordOnlineLoop loop = new WordOnlineLoop(mmrService, mock(UserService.class), gameContext,
                mock(Parameters.class), frameDataSystem, mock(GameActionSystem.class), botSystem,
                mock(FeverTimeSystem.class), mock(GameObjectStateInitialSystem.class),
                mock(ComponentUpdateSystem.class), mock(PhysicSystem.class),
                mock(GameObjectAddRemoteSystem.class), mock(DatabaseMagicParser.class),
                mock(BotPersonaService.class), mock(BotCounterEvaluator.class)) {
            @Override
            protected void buildSnapshot() {
            }
        };
        loop.sessionObject = sessionObject;
        return loop;
    }

    private void update(WordOnlineLoop loop, int frameNum) {
        when(gameContext.getFrameNum()).thenReturn(frameNum);
        loop.update();
    }
}
