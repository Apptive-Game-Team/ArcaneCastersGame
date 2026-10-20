package com.wordonline.server.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.map.GameMap;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.result.ResultMmrDto;

/**
 * The match result reaches the players before any database write, with the ratings the session
 * read when it started, and the writes run on the teardown executor rather than the loop thread.
 */
class GameLoopGameEndTest {

    private static final long LEFT_ID = 11L;
    private static final long RIGHT_ID = 22L;
    private static final long BOT_ID = -7L;

    private final MmrService mmrService = mock(MmrService.class);
    private final UserService userService = mock(UserService.class);
    private final GameContext gameContext = mock(GameContext.class);
    private final ResultChecker resultChecker = mock(ResultChecker.class);
    private final SessionObject sessionObject = mock(SessionObject.class);
    private final List<Runnable> queuedTeardown = new ArrayList<>();

    @Test
    void broadcastsTheComputedRatingsBeforeAnyWriteAndWritesThemLater() {
        GameLoop loop = startedLoop(SessionType.PVP, 1200, 1000);
        when(resultChecker.getLoser()).thenReturn(Master.RightPlayer);

        loop.handleGameEnd();

        // 1200 beats 1000: the expected score is 0.7597, so the winner gains round(20 * 0.2403) = 5.
        ResultMmrDto expected = new ResultMmrDto((short) 1200, (short) 1000, (short) 1205, (short) 995);
        verify(resultChecker).broadcastResult(expected);
        verify(mmrService, never()).saveMatchResult(any(), any(), any());
        verify(userService, never()).markOnline(anyLong());
        assertThat(loop.getEndPersistence()).isNotDone();

        runQueuedTeardown();

        InOrder order = inOrder(mmrService, userService);
        order.verify(mmrService).saveMatchResult(rating(LEFT_ID, 1200), rating(RIGHT_ID, 1000), expected);
        order.verify(userService).markOnline(LEFT_ID);
        order.verify(userService).markOnline(RIGHT_ID);
        assertThat(loop.getEndPersistence()).isDone();
    }

    @Test
    void readsBothRatingsWhenTheSessionStartsAndNeverAtTheEnd() {
        GameLoop loop = startedLoop(SessionType.Practice, 1000, 1000);
        verify(mmrService).resolveRatingRef(LEFT_ID);
        verify(mmrService).resolveRatingRef(RIGHT_ID);
        when(resultChecker.getLoser()).thenReturn(null);

        loop.handleGameEnd();
        runQueuedTeardown();

        verify(mmrService, times(1)).resolveRatingRef(LEFT_ID);
        verify(mmrService, times(1)).resolveRatingRef(RIGHT_ID);
        verify(mmrService, never()).fetchRating(anyLong());
        verify(mmrService, never()).updateMatchResult(anyLong(), anyLong(), any(), anyBoolean());
        verify(resultChecker).broadcastResult(
                new ResultMmrDto((short) 1000, (short) 1000, (short) 1000, (short) 1000));
    }

    // A ranked match the lobby filled with a bot rates the human as well; practice does not.
    @Test
    void aPvpMatchAgainstABotMovesTheHumanRating() {
        GameLoop loop = startedLoop(SessionType.PVP, 1000, 1000, BOT_ID);
        when(resultChecker.getLoser()).thenReturn(Master.RightPlayer);

        loop.handleGameEnd();
        runQueuedTeardown();

        ResultMmrDto expected = new ResultMmrDto((short) 1000, (short) 1000, (short) 1010, (short) 990);
        verify(resultChecker).broadcastResult(expected);
        verify(mmrService).saveMatchResult(rating(LEFT_ID, 1000), rating(BOT_ID, 1000), expected);
    }

    @Test
    void aPracticeMatchAgainstABotKeepsTheHumanRating() {
        GameLoop loop = startedLoop(SessionType.Practice, 1000, 1000, BOT_ID);
        when(resultChecker.getLoser()).thenReturn(Master.RightPlayer);

        loop.handleGameEnd();
        runQueuedTeardown();

        ResultMmrDto expected = new ResultMmrDto((short) 1000, (short) 1000, (short) 1000, (short) 990);
        verify(resultChecker).broadcastResult(expected);
        verify(mmrService).saveMatchResult(rating(LEFT_ID, 1000), rating(BOT_ID, 1000), expected);
    }

    @Test
    void anUnratedSessionBroadcastsTheUnchangedRatingsAndWritesNoRating() {
        GameLoop loop = startedLoop(SessionType.PVE, 1300, 900);
        when(resultChecker.getLoser()).thenReturn(Master.LeftPlayer);

        loop.handleGameEnd();
        runQueuedTeardown();

        verify(resultChecker).broadcastResult(
                new ResultMmrDto((short) 1300, (short) 900, (short) 1300, (short) 900));
        verify(mmrService, never()).saveMatchResult(any(), any(), any());
        verify(userService).markOnline(LEFT_ID);
        verify(userService).markOnline(RIGHT_ID);
    }

    @Test
    void aFailedRatingWriteStillMarksBothUsersOnline() {
        GameLoop loop = startedLoop(SessionType.PVP, 1000, 1000);
        when(resultChecker.getLoser()).thenReturn(Master.LeftPlayer);
        doThrow(new IllegalStateException("database unreachable"))
                .when(mmrService).saveMatchResult(any(), any(), any());

        loop.handleGameEnd();
        runQueuedTeardown();

        verify(userService).markOnline(LEFT_ID);
        verify(userService).markOnline(RIGHT_ID);
        assertThat(loop.getEndPersistence()).isCompletedWithValue(null);
    }

    @Test
    void theWritesRunOnAnotherThreadThanTheOneThatEndedTheMatch() throws Exception {
        GameLoop loop = startedLoop(SessionType.PVP, 1000, 1000);
        when(resultChecker.getLoser()).thenReturn(Master.LeftPlayer);
        List<Thread> writers = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> writers.add(Thread.currentThread())).when(mmrService).saveMatchResult(any(), any(), any());
        doAnswer(invocation -> writers.add(Thread.currentThread())).when(userService).markOnline(anyLong());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            loop.setTeardownExecutor(executor);

            loop.handleGameEnd();
            loop.getEndPersistence().get(5, TimeUnit.SECONDS);

            assertThat(writers).hasSize(3).doesNotContain(Thread.currentThread());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aPlaygroundSessionReadsNoRating() {
        startedLoop(SessionType.Playground, 1000, 1000);

        verifyNoInteractions(mmrService);
    }

    private GameLoop startedLoop(SessionType sessionType, int leftRating, int rightRating) {
        return startedLoop(sessionType, leftRating, rightRating, RIGHT_ID);
    }

    private GameLoop startedLoop(SessionType sessionType, int leftRating, int rightRating, long rightId) {
        when(gameContext.getParameters()).thenReturn(mock(Parameters.class));
        when(gameContext.getResultChecker()).thenReturn(resultChecker);
        when(sessionObject.getLeftUserId()).thenReturn(LEFT_ID);
        when(sessionObject.getRightUserId()).thenReturn(rightId);
        when(sessionObject.getSessionType()).thenReturn(sessionType);
        when(sessionObject.getSessionId()).thenReturn("session-1");
        when(sessionObject.getMap()).thenReturn(GameMap.GRASSLAND);
        when(mmrService.resolveRatingRef(LEFT_ID)).thenReturn(rating(LEFT_ID, leftRating));
        when(mmrService.resolveRatingRef(rightId)).thenReturn(rating(rightId, rightRating));

        GameLoop loop = new GameLoop(mmrService, userService, gameContext, mock(Parameters.class)) {
            @Override
            void update() {
            }
        };
        loop.setTeardownExecutor(queuedTeardown::add);
        loop.init(sessionObject, () -> {
        });
        return loop;
    }

    private void runQueuedTeardown() {
        List<Runnable> tasks = List.copyOf(queuedTeardown);
        queuedTeardown.clear();
        tasks.forEach(Runnable::run);
    }

    private static MmrService.RatingRef rating(long participantId, int rating) {
        return new MmrService.RatingRef(participantId, participantId, participantId < 0, (short) rating);
    }
}
