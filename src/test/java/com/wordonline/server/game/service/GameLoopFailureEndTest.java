package com.wordonline.server.game.service;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.dto.result.ResultMmrDto;
import com.wordonline.server.game.dto.result.ResultType;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameLoopFailureEndTest {

    private final MmrService mmrService = mock(MmrService.class);
    private final UserService userService = mock(UserService.class);
    private final GameContext gameContext = mock(GameContext.class);
    private final ResultChecker resultChecker = mock(ResultChecker.class);
    private final SessionObject sessionObject = mock(SessionObject.class);

    private GameLoop loopThatThrowsAfter(Runnable beforeThrow) {
        when(gameContext.getResultChecker()).thenReturn(resultChecker);
        when(gameContext.getTickRate()).thenReturn(20);
        when(sessionObject.getLeftUserId()).thenReturn(1L);
        when(sessionObject.getRightUserId()).thenReturn(2L);
        when(sessionObject.getSessionType()).thenReturn(SessionType.PVP);
        when(mmrService.updateMatchResult(anyLong(), anyLong(), any(ResultType.class)))
                .thenReturn(new ResultMmrDto((short) 0, (short) 0, (short) 0, (short) 0));

        GameLoop loop = new GameLoop(mmrService, userService, gameContext, null) {
            @Override
            void update() {
                beforeThrow.run();
                throw new IllegalStateException("boom");
            }
        };
        loop.sessionObject = sessionObject;
        return loop;
    }

    @Test
    void broadcastsResultAndMarksBothUsersOnlineWhenUpdateThrows() {
        GameLoop loop = loopThatThrowsAfter(() -> {
        });

        loop.run();

        verify(resultChecker).broadcastResult(any());
        verify(userService).markOnline(1L);
        verify(userService).markOnline(2L);
    }

    @Test
    void doesNotEndTwiceWhenFrameThrowsAfterGameAlreadyEnded() {
        GameLoop[] holder = new GameLoop[1];
        holder[0] = loopThatThrowsAfter(() -> holder[0].handleGameEnd());

        holder[0].run();

        verify(resultChecker, times(1)).broadcastResult(any());
        verify(userService, times(1)).markOnline(1L);
    }
}
