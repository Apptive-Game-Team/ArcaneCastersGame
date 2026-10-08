package com.wordonline.server.session.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerInstanceIdProvider;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.server.service.ServerUrlProvider;
import com.wordonline.server.session.dto.CreateSessionRequest;
import com.wordonline.server.session.dto.SessionLengthDto;
import com.wordonline.server.session.dto.SessionReadyResponse;
import com.wordonline.server.session.dto.SimpleBooleanDto;
import com.wordonline.server.session.service.SessionService;
import com.wordonline.server.session.service.SessionCreationResult;
import com.wordonline.server.session.dto.RoomListDto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@PreAuthorize("hasAuthority('WORDONLINE_SERVER')")
@RequestMapping("/api/server")
public class SessionServerController {

    private final SessionService sessionService;
    private final ServerStatusService serverStatusService;
    private final ServerUrlProvider serverUrlProvider;
    private final ServerInstanceIdProvider serverInstanceIdProvider;

    @PostMapping("/game-sessions")
    public ResponseEntity<SessionReadyResponse> createGameSession(@RequestBody CreateSessionRequest request) {
        if (request.toSessionDto().sessionType() == com.wordonline.server.game.domain.SessionType.Playground) {
            return ResponseEntity.badRequest().body(toResponse(request.attemptId(), request.sessionId(), false));
        }

        if (!serverStatusService.getCurrentState().equals(ServerState.ACTIVE)) {
            return ResponseEntity.badRequest().body(toResponse(request.attemptId(), request.sessionId(), false));
        }

        SessionCreationResult result = sessionService.createSession(request.attemptId(), request.toSessionDto());
        return ResponseEntity.ok(toResponse(result.attemptId(), result.sessionId(), result.ready()));
    }

    private SessionReadyResponse toResponse(String attemptId, String sessionId, boolean ready) {
        String serverUrl = serverUrlProvider.getServerUrl();
        return new SessionReadyResponse(
                attemptId, sessionId, ready, serverUrl, serverUrl + "/ws",
                serverInstanceIdProvider.getInstanceId());
    }

    @GetMapping("/game-sessions/{sessionId}/active")
    public ResponseEntity<SimpleBooleanDto> isGameSessionActive(@PathVariable String sessionId) {
        return ResponseEntity.ok(
                new SimpleBooleanDto(sessionService.isSessionActive(sessionId)));
    }

    @GetMapping("/game-sessions/length")
    public ResponseEntity<SessionLengthDto> getSessionsLength() {
        return ResponseEntity.ok(
                new SessionLengthDto((int) sessionService.getActiveSessions()));
    }

    @GetMapping("/game-sessions")
    public ResponseEntity<RoomListDto> getGameSessions() {
        return ResponseEntity.ok(
                new RoomListDto(sessionService.getAllActiveSessionsInfo(serverUrlProvider.getServerUrl())));
    }
}
