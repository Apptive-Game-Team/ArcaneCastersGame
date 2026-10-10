package com.wordonline.server.session.controller;

import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.game.domain.map.GameMap;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerInstanceIdProvider;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.server.service.ServerUrlProvider;
import com.wordonline.server.session.dto.CreateSessionRequest;
import com.wordonline.server.session.dto.SessionReadyResponse;
import com.wordonline.server.session.service.SessionCreationResult;
import com.wordonline.server.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionServerControllerTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final ServerStatusService serverStatusService = mock(ServerStatusService.class);
    private final ServerUrlProvider serverUrlProvider = mock(ServerUrlProvider.class);
    private final ServerInstanceIdProvider serverInstanceIdProvider = new ServerInstanceIdProvider();
    private final SessionServerController controller = new SessionServerController(
            sessionService, serverStatusService, serverUrlProvider, serverInstanceIdProvider);

    @Test
    void returnsReadySessionWithExternalConnectionInfo() {
        CreateSessionRequest request = new CreateSessionRequest(
                "attempt-1", "session-1", 1L, 2L, SessionType.PVP, null);

        when(serverStatusService.getCurrentState()).thenReturn(ServerState.ACTIVE);
        when(serverUrlProvider.getServerUrl()).thenReturn("https://game.example.com");
        when(sessionService.createSession("attempt-1", request.toSessionDto()))
                .thenReturn(new SessionCreationResult("attempt-1", "session-1", true, GameMap.GRASSLAND));

        ResponseEntity<SessionReadyResponse> response = controller.createGameSession(request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().ready());
        assertEquals("https://game.example.com", response.getBody().serverUrl());
        assertEquals("https://game.example.com/ws", response.getBody().webSocketUrl());
        assertEquals(serverInstanceIdProvider.getInstanceId(), response.getBody().instanceId());
    }

    @Test
    void theResponseCarriesTheMapTypeOfTheCreatedSession() {
        CreateSessionRequest request = new CreateSessionRequest(
                "attempt-1", "pve-1", 1L, -1L, SessionType.PVE, 3L);

        when(serverStatusService.getCurrentState()).thenReturn(ServerState.ACTIVE);
        when(serverUrlProvider.getServerUrl()).thenReturn("https://game.example.com");
        when(sessionService.createSession("attempt-1", request.toSessionDto()))
                .thenReturn(new SessionCreationResult("attempt-1", "pve-1", true, GameMap.FORTRESS));

        assertEquals(GameMap.FORTRESS, controller.createGameSession(request).getBody().mapType());
    }

    @Test
    void readinessResponsesFromOneProcessCarryTheSameInstanceId() {
        // The lobby compares the id it stored at session creation with the one the server
        // publishes later. A value that drifts inside a live process would read as a restart
        // and tear down sessions that are still running.
        CreateSessionRequest first = new CreateSessionRequest(
                "attempt-1", "session-1", 1L, 2L, SessionType.PVP, null);
        CreateSessionRequest second = new CreateSessionRequest(
                "attempt-2", "session-2", 3L, 4L, SessionType.PVP, null);

        when(serverStatusService.getCurrentState()).thenReturn(ServerState.ACTIVE);
        when(serverUrlProvider.getServerUrl()).thenReturn("https://game.example.com");
        when(sessionService.createSession("attempt-1", first.toSessionDto()))
                .thenReturn(new SessionCreationResult("attempt-1", "session-1", true, GameMap.GRASSLAND));
        when(sessionService.createSession("attempt-2", second.toSessionDto()))
                .thenReturn(new SessionCreationResult("attempt-2", "session-2", true, GameMap.GRASSLAND));

        String firstId = controller.createGameSession(first).getBody().instanceId();
        String secondId = controller.createGameSession(second).getBody().instanceId();

        assertEquals(firstId, secondId);
    }

    @Test
    void refusedSessionsStillCarryTheInstanceId() {
        // A refusal is the lobby's cue to try the next server, but it still needs to know which
        // process answered so a later restart is not mistaken for a still-live refusal.
        when(serverStatusService.getCurrentState()).thenReturn(ServerState.DRAINING);

        ResponseEntity<SessionReadyResponse> response = controller.createGameSession(
                new CreateSessionRequest("attempt-1", "session-1", 1L, 2L, SessionType.PVP, null));

        assertEquals(400, response.getStatusCode().value());
        assertEquals(serverInstanceIdProvider.getInstanceId(), response.getBody().instanceId());
    }
}
