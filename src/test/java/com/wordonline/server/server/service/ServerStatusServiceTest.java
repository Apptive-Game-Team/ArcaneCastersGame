package com.wordonline.server.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.wordonline.server.server.config.ServerIdentityProperties;
import com.wordonline.server.server.entity.Server;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.entity.ServerType;
import com.wordonline.server.server.repository.ServerRepository;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ServerStatusServiceTest {

    @Mock
    private ServerRepository serverRepository;

    private final ServerInstanceIdProvider serverInstanceIdProvider = new ServerInstanceIdProvider();

    private ServerStatusService serverStatusService;

    @BeforeEach
    void setUp() {
        serverStatusService = new ServerStatusService(
                serverRepository,
                new ServerIdentityProperties("https", "game.example.com", 7777, 64, "http://ac-game-blue:8080/"),
                serverInstanceIdProvider);
    }

    @Test
    void publishHeartbeatCreatesServerWithCapacityAndTimestamp() {
        when(serverRepository.findByDomainAndPort("game.example.com", 7777)).thenReturn(Optional.empty());
        Instant before = Instant.now();

        serverStatusService.publishHeartbeat(7);

        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        Server saved = captor.getValue();
        assertThat(saved.getProtocol()).isEqualTo("https");
        assertThat(saved.getDomain()).isEqualTo("game.example.com");
        assertThat(saved.getPort()).isEqualTo(7777);
        assertThat(saved.getType()).isEqualTo(ServerType.GAME);
        assertThat(saved.getState()).isEqualTo(ServerState.ACTIVE);
        assertThat(saved.getSessionCount()).isEqualTo(7);
        assertThat(saved.getMaxSessions()).isEqualTo(64);
        assertThat(saved.getLastHeartbeatAt()).isAfterOrEqualTo(before);
        assertThat(saved.getInstanceId()).isEqualTo(serverInstanceIdProvider.getInstanceId());
        // setUp configures a trailing slash; ServerIdentityProperties strips it before the
        // service ever sees the value.
        assertThat(saved.getInternalBaseUrl()).isEqualTo("http://ac-game-blue:8080");
    }

    @Test
    void blankInternalBaseUrlIsWrittenAsNullRatherThanEmptyString() {
        ServerStatusService service = new ServerStatusService(
                serverRepository,
                new ServerIdentityProperties("https", "game.example.com", 7777, 64, "   "),
                serverInstanceIdProvider);
        when(serverRepository.findByDomainAndPort("game.example.com", 7777)).thenReturn(Optional.empty());

        service.publishHeartbeat(1);

        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        assertThat(captor.getValue().getInternalBaseUrl()).isNull();
    }

    @Test
    void heartbeatOverwritesAnInstanceIdLeftBehindByAPreviousProcess() {
        // The row survives the restart; the sessions do not. Leaving the old id in place would
        // tell the lobby the previous process is still holding them.
        Server server = new Server("https", "game.example.com", 7777, ServerType.GAME, ServerState.ACTIVE);
        server.setInstanceId("00000000-0000-0000-0000-00000000dead");
        when(serverRepository.findByDomainAndPort("game.example.com", 7777)).thenReturn(Optional.of(server));

        serverStatusService.publishHeartbeat(3);

        assertThat(server.getInstanceId()).isEqualTo(serverInstanceIdProvider.getInstanceId());
        verify(serverRepository).save(server);
    }

    @Test
    void findTargetBotSessionsReturnsTheAdminOverrideFromTheOwnRow() {
        Server server = new Server(1L, "https", "game.example.com", 7777, ServerType.GAME,
                ServerState.ACTIVE, Instant.now(), 0, 64, null, null, 4);
        when(serverRepository.findByDomainAndPort("game.example.com", 7777)).thenReturn(Optional.of(server));

        assertThat(serverStatusService.findTargetBotSessions()).contains(4);
    }

    @Test
    void findTargetBotSessionsIsEmptyWithoutARowOrOverride() {
        when(serverRepository.findByDomainAndPort("game.example.com", 7777)).thenReturn(Optional.empty());
        assertThat(serverStatusService.findTargetBotSessions()).isEmpty();

        Server noOverride = new Server("https", "game.example.com", 7777, ServerType.GAME, ServerState.ACTIVE);
        when(serverRepository.findByDomainAndPort("game.example.com", 7777)).thenReturn(Optional.of(noOverride));
        assertThat(serverStatusService.findTargetBotSessions()).isEmpty();
    }

    @Test
    void theRowIsReadAndWrittenInOneTransaction() throws NoSuchMethodException {
        // The read and SimpleJpaRepository.save's own @Transactional otherwise take one
        // connection each for a single row update. publishStatus is private, so the boundary
        // has to sit on the entry points the transaction proxy sees.
        assertThat(ServerStatusService.class.getMethod("publishHeartbeat", int.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(ServerStatusService.class.getMethod("setServerStatus", ServerState.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void inactiveStatusClearsSessionCount() {
        Server server = new Server("https", "game.example.com", 7777, ServerType.GAME, ServerState.ACTIVE);
        server.setSessionCount(5);
        when(serverRepository.findByDomainAndPort("game.example.com", 7777)).thenReturn(Optional.of(server));

        serverStatusService.setServerStatus(ServerState.INACTIVE);

        assertThat(server.getState()).isEqualTo(ServerState.INACTIVE);
        assertThat(server.getSessionCount()).isZero();
        assertThat(server.getMaxSessions()).isEqualTo(64);
        assertThat(server.getLastHeartbeatAt()).isNotNull();
        assertThat(server.getInstanceId()).isEqualTo(serverInstanceIdProvider.getInstanceId());
        verify(serverRepository).save(server);
    }
}
