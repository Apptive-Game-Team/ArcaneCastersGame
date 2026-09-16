package com.wordonline.server.bot.service;

import com.wordonline.server.bot.config.BotAutoMatchProperties;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.server.config.ServerIdentityProperties;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.session.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotGameSchedulerTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final BotPersonaService botPersonaService = mock(BotPersonaService.class);
    private final ServerStatusService serverStatusService = mock(ServerStatusService.class);

    @BeforeEach
    void setUp() {
        when(serverStatusService.getCurrentState()).thenReturn(ServerState.ACTIVE);
    }

    @Test
    void createsPracticeSessionWithEnabledBotsWhenIdle() {
        BotPersona beginner = bot(-1, "Beginner Bot");
        BotPersona advanced = bot(-2, "Advanced Bot");
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findEnabled()).thenReturn(List.of(beginner, advanced));

        scheduler(defaultProperties()).ensureBotGameWhenIdle();

        ArgumentCaptor<SessionDto> captor = ArgumentCaptor.forClass(SessionDto.class);
        verify(sessionService).createSession(captor.capture());
        SessionDto sessionDto = captor.getValue();

        assertThat(sessionDto.sessionId()).startsWith("bot-auto-");
        assertThat(Set.of(sessionDto.uid1(), sessionDto.uid2()))
                .containsExactlyInAnyOrder(
                        beginner.userId(),
                        advanced.userId()
                );
        assertThat(sessionDto.uid1()).isNotEqualTo(sessionDto.uid2());
        assertThat(sessionDto.sessionType()).isEqualTo(SessionType.Practice);
        assertThat(sessionDto.scenarioId()).isNull();
    }

    @Test
    void skipsWhenOnlyOneEnabledBotPersonaExists() {
        BotPersona beginner = bot(-1, "Beginner Bot");
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findEnabled()).thenReturn(List.of(beginner));

        scheduler(defaultProperties()).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenAnySessionIsActive() {
        when(sessionService.getActiveSessions()).thenReturn(1L);

        scheduler(defaultProperties()).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenNoEnabledBotsExist() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findEnabled()).thenReturn(List.of());

        scheduler(defaultProperties()).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenServerIsNotActive() {
        when(serverStatusService.getCurrentState()).thenReturn(ServerState.INACTIVE);

        scheduler(defaultProperties()).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenAutoMatchIsDisabled() {
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(false, 5)).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fillsUpToTargetGamesWhenMoreAreRequested() {
        when(sessionService.getActiveSessions()).thenReturn(2L);
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 5)).ensureBotGameWhenIdle();

        verify(sessionService, times(3)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsDistinctSessionIdsWhenFillingSeveralGames() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 4)).ensureBotGameWhenIdle();

        ArgumentCaptor<SessionDto> captor = ArgumentCaptor.forClass(SessionDto.class);
        verify(sessionService, times(4)).createSession(captor.capture());
        assertThat(captor.getAllValues()).extracting(SessionDto::sessionId).doesNotHaveDuplicates();
    }

    @Test
    void skipsWhenActiveSessionsAlreadyReachTargetGames() {
        when(sessionService.getActiveSessions()).thenReturn(3L);

        scheduler(new BotAutoMatchProperties(true, 3)).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminOverrideOnServerRowWinsOverConfiguredTargetGames() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenReturn(Optional.of(3));
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 1)).ensureBotGameWhenIdle();

        verify(sessionService, times(3)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anOverrideOfZeroStopsBotGamesWithoutDisablingTheScheduler() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenReturn(Optional.of(0));

        scheduler(new BotAutoMatchProperties(true, 5)).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fallsBackToConfiguredTargetGamesWhenNoOverrideIsSet() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenReturn(Optional.empty());
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 2)).ensureBotGameWhenIdle();

        verify(sessionService, times(2)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fallsBackToConfiguredTargetGamesWhenTheOverrideReadFails() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenThrow(new RuntimeException("database is down"));
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 2)).ensureBotGameWhenIdle();

        verify(sessionService, times(2)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theTargetIsClampedToTheServersMaxSessions() {
        // A box measured to hold about a hundred sessions obeys an admin target of two hundred
        // otherwise, and falls over.
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenReturn(Optional.of(200));
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 1), 6, new BotGameScheduler.SweepProperties(1000))
                .ensureBotGameWhenIdle();

        verify(sessionService, times(6)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theConfiguredTargetIsClampedToMaxSessionsToo() {
        when(sessionService.getActiveSessions()).thenReturn(2L);
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 50), 5, new BotGameScheduler.SweepProperties(1000))
                .ensureBotGameWhenIdle();

        verify(sessionService, times(3)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void atMostOneSweepsWorthOfSessionsIsCreatedPerSweep() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 50), 100, new BotGameScheduler.SweepProperties(4))
                .ensureBotGameWhenIdle();

        verify(sessionService, times(4)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aServerAtCapacityCreatesNothingAndDoesNotReadTheOverride() {
        when(sessionService.getActiveSessions()).thenReturn(8L);

        scheduler(new BotAutoMatchProperties(true, 50), 8, new BotGameScheduler.SweepProperties(1000))
                .ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
        verify(serverStatusService, never()).findTargetBotSessions();
    }

    @Test
    void anAbsentMaxSessionsDoesNotClamp() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findEnabled()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(true, 3), null, new BotGameScheduler.SweepProperties(1000))
                .ensureBotGameWhenIdle();

        verify(sessionService, times(3)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sweepPropertiesFallBackToFiveSessionsPerSweep() {
        assertThat(new BotGameScheduler.SweepProperties(null).maxSessionsPerSweep()).isEqualTo(5);
        assertThat(new BotGameScheduler.SweepProperties(0).maxSessionsPerSweep()).isEqualTo(5);
        assertThat(new BotGameScheduler.SweepProperties(12).maxSessionsPerSweep()).isEqualTo(12);
    }

    @Test
    void fallsBackToOneGameWhenPropertiesAreMissing() {
        BotAutoMatchProperties properties = new BotAutoMatchProperties(null, null);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.targetGames()).isEqualTo(1);
    }

    private BotGameScheduler scheduler(BotAutoMatchProperties properties) {
        return scheduler(properties, 1000, new BotGameScheduler.SweepProperties(1000));
    }

    private BotGameScheduler scheduler(BotAutoMatchProperties properties,
                                       Integer maxSessions,
                                       BotGameScheduler.SweepProperties sweepProperties) {
        return new BotGameScheduler(
                sessionService,
                botPersonaService,
                serverStatusService,
                properties,
                new ServerIdentityProperties("http", "localhost", 7777, maxSessions, ""),
                sweepProperties
        );
    }

    private BotAutoMatchProperties defaultProperties() {
        return new BotAutoMatchProperties(true, 1);
    }

    private BotPersona bot(long id, String name) {
        return new BotPersona(
                id,
                name,
                BotTier.BEGINNER,
                250,
                8,
                0.25,
                true,
                false
        );
    }
}
