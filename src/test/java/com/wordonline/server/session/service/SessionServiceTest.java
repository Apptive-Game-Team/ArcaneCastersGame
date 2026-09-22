package com.wordonline.server.session.service;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.dto.PingChecker;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.game.service.ResultChecker;
import com.wordonline.server.game.service.UserScenarioService;
import com.wordonline.server.game.service.UserService;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.lobby.client.LobbySessionClient;
import com.wordonline.server.session.dto.RoomInfoDto;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.session.util.GameLoopFactory;
import com.wordonline.server.session.util.SessionObjectFactory;
import com.wordonline.server.statistic.service.GameSessionRecordService;
import com.wordonline.server.statistic.service.StatisticService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.bot.service.BotPersonaService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private final SessionObjectFactory sessionObjectFactory = mock(SessionObjectFactory.class);
    private final GameLoopFactory gameLoopFactory = mock(GameLoopFactory.class);
    private final StatisticService statisticService = mock(StatisticService.class);
    private final GameSessionRecordService gameSessionRecordService = mock(GameSessionRecordService.class);
    private final UserService userService = mock(UserService.class);
    private final BotPersonaService botPersonaService = mock(BotPersonaService.class);
    private final LobbySessionClient lobbySessionClient = mock(LobbySessionClient.class);
    private final SessionService sessionService = new SessionService(
            sessionObjectFactory,
            gameLoopFactory,
            statisticService,
            gameSessionRecordService,
            userService,
            botPersonaService,
            mock(UserScenarioService.class),
            lobbySessionClient);

    private SessionDto sessionDto;
    private SessionObject sessionObject;
    private GameLoop gameLoop;

    @BeforeEach
    void setUp() {
        sessionService.clearSessions();
        sessionDto = new SessionDto("session-1", 1L, 2L, SessionType.PVP, null);
        sessionObject = mock(SessionObject.class);
        gameLoop = mock(GameLoop.class);

        when(sessionObjectFactory.createSessionObject(sessionDto)).thenReturn(sessionObject);
        when(sessionObject.getSessionId()).thenReturn(sessionDto.sessionId());
        when(sessionObject.getSessionType()).thenReturn(sessionDto.sessionType());
        when(sessionObject.getGameLoop()).thenReturn(gameLoop);
        when(gameLoopFactory.create(sessionDto.sessionType())).thenReturn(gameLoop);
        when(gameLoop.is_running()).thenReturn(true);
        when(gameLoop.awaitStart(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        sessionService.clearSessions();
    }

    // 진행도는 이겼을 때만 오른다. 회수된 세션에는 승자가 없으므로 아무 일도 없어야 한다.
    // 플레이어는 같은 조건으로 그 판을 다시 하게 되고, 건너뛰지 않는다.
    @Test
    void leavesNoviceProgressAloneWhenASessionIsReaped() {
        SessionObject practice = hospitalityPracticeSession("bot-1", 5L, -9L);

        sessionService.createSession(hospitalityPracticeDto("bot-1", 5L, -9L));
        sessionService.reapStuckSession(practice, "stuck");

        verify(userService, never()).advanceNoviceProgress(anyLong());
    }

    private SessionDto hospitalityPracticeDto(String sessionId, long leftUserId, long rightUserId) {
        when(botPersonaService.findByParticipantIdOrDefault(rightUserId))
                .thenReturn(new BotPersona(rightUserId, "Host", BotTier.HOSPITALITY, 1200, 30, -1.0, true, true,
                        BotTemperament.WARM));
        return practiceDto(sessionId, leftUserId, rightUserId);
    }

    private SessionDto practiceDto(String sessionId, long leftUserId, long rightUserId) {
        return new SessionDto(sessionId, leftUserId, rightUserId, SessionType.Practice, null);
    }

    private SessionObject hospitalityPracticeSession(String sessionId, long leftUserId, long rightUserId) {
        when(botPersonaService.findByParticipantIdOrDefault(rightUserId))
                .thenReturn(new BotPersona(rightUserId, "Host", BotTier.HOSPITALITY, 1200, 30, -1.0, true, true,
                        BotTemperament.WARM));
        return practiceSession(sessionId, leftUserId, rightUserId);
    }

    private SessionObject practiceSession(String sessionId, long leftUserId, long rightUserId) {
        SessionDto dto = practiceDto(sessionId, leftUserId, rightUserId);
        SessionObject practice = mock(SessionObject.class);
        GameLoop practiceLoop = mock(GameLoop.class);
        when(sessionObjectFactory.createSessionObject(dto)).thenReturn(practice);
        when(practice.getSessionId()).thenReturn(sessionId);
        when(practice.getSessionType()).thenReturn(SessionType.Practice);
        when(practice.getLeftUserId()).thenReturn(leftUserId);
        when(practice.getRightUserId()).thenReturn(rightUserId);
        when(practice.getGameLoop()).thenReturn(practiceLoop);
        when(practice.getPingChecker()).thenReturn(mock(PingChecker.class));
        when(gameLoopFactory.create(SessionType.Practice)).thenReturn(practiceLoop);
        when(practiceLoop.is_running()).thenReturn(true);
        when(practiceLoop.awaitStart(any())).thenReturn(true);
        return practice;
    }


    @Test
    void repeatedAttemptReturnsReadySessionWithoutCreatingDuplicate() {
        SessionCreationResult first = sessionService.createSession("attempt-1", sessionDto);
        SessionCreationResult repeated = sessionService.createSession("attempt-1", sessionDto);

        assertTrue(first.ready());
        assertTrue(repeated.ready());
        verify(sessionObjectFactory, times(1)).createSessionObject(sessionDto);
    }

    @Test
    void repeatedAttemptRejectsDifferentSession() {
        sessionService.createSession("attempt-1", sessionDto);
        SessionDto conflicting = new SessionDto("session-2", 1L, 2L, SessionType.PVP, null);

        assertThrows(IllegalArgumentException.class,
                () -> sessionService.createSession("attempt-1", conflicting));
    }

    @Test
    void sessionWhoseLoopNeverStartsIsNotReportedReady() {
        when(gameLoop.awaitStart(any())).thenReturn(false);
        when(gameLoop.is_running()).thenReturn(false);

        SessionCreationResult result = sessionService.createSession("attempt-1", sessionDto);

        assertFalse(result.ready());
        assertFalse(sessionService.isSessionActive(sessionDto.sessionId()));
        assertThat(sessionService.getActiveSessions()).isZero();
        assertThat(sessionService.getAllActiveSessionsInfo("http://game")).isEmpty();
    }

    // ConcurrentHashMap iteration follows key hash order, so without the explicit sort the room list
    // would come back in an order unrelated to when the sessions were created.
    @Test
    void roomListIsOrderedOldestFirstRegardlessOfMapOrder() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        when(sessionObject.getCreatedAt()).thenReturn(now.plusSeconds(60));
        sessionService.createSession(sessionDto);

        SessionDto olderDto = new SessionDto("session-0", 3L, 4L, SessionType.PVP, null);
        SessionObject older = mock(SessionObject.class);
        GameLoop olderLoop = mock(GameLoop.class);
        when(sessionObjectFactory.createSessionObject(olderDto)).thenReturn(older);
        when(older.getSessionId()).thenReturn(olderDto.sessionId());
        when(older.getSessionType()).thenReturn(olderDto.sessionType());
        when(older.getGameLoop()).thenReturn(olderLoop);
        when(older.getCreatedAt()).thenReturn(now);
        when(gameLoopFactory.create(olderDto.sessionType())).thenReturn(olderLoop);
        when(olderLoop.is_running()).thenReturn(true);
        sessionService.createSession(olderDto);

        List<RoomInfoDto> rooms = sessionService.getAllActiveSessionsInfo("http://game");

        assertThat(rooms).extracting(RoomInfoDto::sessionId)
                .containsExactly("session-0", "session-1");
        assertThat(rooms).extracting(RoomInfoDto::createdAt)
                .containsExactly(now, now.plusSeconds(60));
    }

    @Test
    void missingSessionIsInactive() {
        assertFalse(sessionService.isSessionActive("missing"));
    }

    @Test
    void reportsInactiveForUnknownSessionId() {
        SessionService service = new SessionService(null, null, null, null, null, null, null, null);

        assertThat(service.isSessionActive("no-such-session")).isFalse();
    }

    @Test
    void hardenedTeardownStillPublishesDecreasedSessionCount() {
        List<Integer> publishedCounts = new CopyOnWriteArrayList<>();
        sessionService.subscribeSessionNumChange(recordingSubscriber(publishedCounts));

        ArgumentCaptor<Runnable> onTerminated = ArgumentCaptor.forClass(Runnable.class);
        sessionService.createSession("attempt-1", sessionDto);
        verify(gameLoop).init(eq(sessionObject), onTerminated.capture());
        await().atMost(Duration.ofSeconds(5)).until(() -> publishedCounts.contains(1));

        stubTeardownCollaborators();
        onTerminated.getValue().run();

        await().atMost(Duration.ofSeconds(5)).until(() -> publishedCounts.contains(0));
        assertFalse(sessionService.isSessionActive(sessionDto.sessionId()));
    }

    @Test
    void teardownPublishesDecreasedSessionCountEvenWhenStatisticsSaveFails() {
        List<Integer> publishedCounts = new CopyOnWriteArrayList<>();
        sessionService.subscribeSessionNumChange(recordingSubscriber(publishedCounts));

        ArgumentCaptor<Runnable> onTerminated = ArgumentCaptor.forClass(Runnable.class);
        sessionService.createSession("attempt-1", sessionDto);
        verify(gameLoop).init(eq(sessionObject), onTerminated.capture());

        stubTeardownCollaborators();
        doThrow(new IllegalStateException("statistics unavailable"))
                .when(statisticService).saveGameResult(any(), any(), any());

        assertDoesNotThrow(() -> onTerminated.getValue().run());

        await().atMost(Duration.ofSeconds(5)).until(() -> publishedCounts.contains(0));
    }

    @Test
    void teardownNotifiesLobbyThatTheSessionEnded() {
        ArgumentCaptor<Runnable> onTerminated = ArgumentCaptor.forClass(Runnable.class);
        sessionService.createSession("attempt-1", sessionDto);
        verify(gameLoop).init(eq(sessionObject), onTerminated.capture());

        stubTeardownCollaborators();
        onTerminated.getValue().run();

        verify(lobbySessionClient).notifySessionEnded(sessionDto.sessionId());
    }

    @Test
    void failingLobbyNotificationStillSavesStatisticsAndWins() {
        ArgumentCaptor<Runnable> onTerminated = ArgumentCaptor.forClass(Runnable.class);
        sessionService.createSession("attempt-1", sessionDto);
        verify(gameLoop).init(eq(sessionObject), onTerminated.capture());

        ResultChecker resultChecker = stubTeardownCollaborators();
        when(resultChecker.getLoser()).thenReturn(Master.RightPlayer);
        when(resultChecker.getWinnerId()).thenReturn(1L);
        doThrow(new IllegalStateException("lobby unreachable"))
                .when(lobbySessionClient).notifySessionEnded(any());

        assertDoesNotThrow(() -> onTerminated.getValue().run());

        verify(statisticService).saveGameResult(any(), eq(Master.RightPlayer), eq(SessionType.PVP));
        verify(userService).incrementTotalWins(1L);
        assertFalse(sessionService.isSessionActive(sessionDto.sessionId()));
    }

    // The reap and the loop's own finally block race for the same session entry; whoever
    // wins the map removal owns the teardown, so the zombie's later termination must be a no-op.
    @Test
    void reapedSessionSkipsSecondTeardownWhenZombieLoopLaterTerminates() {
        ArgumentCaptor<Runnable> onTerminated = ArgumentCaptor.forClass(Runnable.class);
        sessionService.createSession("attempt-1", sessionDto);
        verify(gameLoop).init(eq(sessionObject), onTerminated.capture());

        stubTeardownCollaborators();
        when(sessionObject.getLeftUserId()).thenReturn(1L);
        when(sessionObject.getRightUserId()).thenReturn(2L);

        assertTrue(sessionService.reapStuckSession(sessionObject, "stalled for 10000 ms"));

        verify(statisticService).saveAbandonedGameResult(any(), eq(SessionType.PVP));
        verify(lobbySessionClient).notifySessionEnded(sessionDto.sessionId());
        verify(userService).markOnline(1L);
        verify(userService).markOnline(2L);
        verify(gameLoop).close();
        verify(gameLoop).interruptLoopThread();
        assertFalse(sessionService.isSessionActive(sessionDto.sessionId()));

        onTerminated.getValue().run();

        verify(statisticService, times(0)).saveGameResult(any(), any(), any());
        verify(lobbySessionClient, times(1)).notifySessionEnded(any());
    }

    @Test
    void reapReturnsFalseWhenSessionAlreadyEndedNormally() {
        ArgumentCaptor<Runnable> onTerminated = ArgumentCaptor.forClass(Runnable.class);
        sessionService.createSession("attempt-1", sessionDto);
        verify(gameLoop).init(eq(sessionObject), onTerminated.capture());

        stubTeardownCollaborators();
        onTerminated.getValue().run();

        assertFalse(sessionService.reapStuckSession(sessionObject, "stalled"));
        verify(statisticService, times(0)).saveAbandonedGameResult(any(), any());
    }

    private ResultChecker stubTeardownCollaborators() {
        GameContext gameContext = mock(GameContext.class);
        ResultChecker resultChecker = mock(ResultChecker.class);

        when(sessionObject.getPingChecker()).thenReturn(mock(PingChecker.class));
        when(sessionObject.getGameContext()).thenReturn(gameContext);
        when(gameContext.getResultChecker()).thenReturn(resultChecker);
        when(resultChecker.getLoser()).thenReturn(null);

        return resultChecker;
    }

    private Flow.Subscriber<Integer> recordingSubscriber(List<Integer> sink) {
        return new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Integer item) {
                sink.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        };
    }
}
