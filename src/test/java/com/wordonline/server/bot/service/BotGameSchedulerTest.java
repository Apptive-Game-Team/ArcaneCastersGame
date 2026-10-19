package com.wordonline.server.bot.service;

import com.wordonline.server.bot.config.BotAutoMatchProperties;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTemperament;
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
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
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
        when(botPersonaService.findMatchable()).thenReturn(List.of(beginner, advanced));

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
        when(botPersonaService.findMatchable()).thenReturn(List.of(beginner));

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
        when(botPersonaService.findMatchable()).thenReturn(List.of());

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
        when(botPersonaService.findMatchable()).thenReturn(List.of(bot(-1, "Beginner Bot"), bot(-2, "Advanced Bot")));

        scheduler(new BotAutoMatchProperties(false, 5, null)).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fillsUpToTargetGamesWhenMoreAreRequested() {
        when(sessionService.getActiveSessions()).thenReturn(2L);
        when(botPersonaService.findMatchable()).thenReturn(bots(6));

        scheduler(new BotAutoMatchProperties(true, 5, null)).ensureBotGameWhenIdle();

        verify(sessionService, times(3)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsDistinctSessionIdsWhenFillingSeveralGames() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findMatchable()).thenReturn(bots(8));

        scheduler(new BotAutoMatchProperties(true, 4, null)).ensureBotGameWhenIdle();

        ArgumentCaptor<SessionDto> captor = ArgumentCaptor.forClass(SessionDto.class);
        verify(sessionService, times(4)).createSession(captor.capture());
        assertThat(captor.getAllValues()).extracting(SessionDto::sessionId).doesNotHaveDuplicates();
    }

    @Test
    void skipsWhenActiveSessionsAlreadyReachTargetGames() {
        when(sessionService.getActiveSessions()).thenReturn(3L);

        scheduler(new BotAutoMatchProperties(true, 3, null)).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminOverrideOnServerRowWinsOverConfiguredTargetGames() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenReturn(Optional.of(3));
        when(botPersonaService.findMatchable()).thenReturn(bots(6));

        scheduler(new BotAutoMatchProperties(true, 1, null)).ensureBotGameWhenIdle();

        verify(sessionService, times(3)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anOverrideOfZeroStopsBotGamesWithoutDisablingTheScheduler() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenReturn(Optional.of(0));

        scheduler(new BotAutoMatchProperties(true, 5, null)).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fallsBackToConfiguredTargetGamesWhenNoOverrideIsSet() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenReturn(Optional.empty());
        when(botPersonaService.findMatchable()).thenReturn(bots(4));

        scheduler(new BotAutoMatchProperties(true, 2, null)).ensureBotGameWhenIdle();

        verify(sessionService, times(2)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fallsBackToConfiguredTargetGamesWhenTheOverrideReadFails() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenThrow(new RuntimeException("database is down"));
        when(botPersonaService.findMatchable()).thenReturn(bots(4));

        scheduler(new BotAutoMatchProperties(true, 2, null)).ensureBotGameWhenIdle();

        verify(sessionService, times(2)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theTargetIsClampedToTheServersMaxSessions() {
        // A box measured to hold about a hundred sessions obeys an admin target of two hundred
        // otherwise, and falls over.
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(serverStatusService.findTargetBotSessions()).thenReturn(Optional.of(200));
        when(botPersonaService.findMatchable()).thenReturn(bots(12));

        scheduler(new BotAutoMatchProperties(true, 1, null), 6, new BotGameScheduler.SweepProperties(1000))
                .ensureBotGameWhenIdle();

        verify(sessionService, times(6)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theConfiguredTargetIsClampedToMaxSessionsToo() {
        when(sessionService.getActiveSessions()).thenReturn(2L);
        when(botPersonaService.findMatchable()).thenReturn(bots(6));

        scheduler(new BotAutoMatchProperties(true, 50, null), 5, new BotGameScheduler.SweepProperties(1000))
                .ensureBotGameWhenIdle();

        verify(sessionService, times(3)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void atMostOneSweepsWorthOfSessionsIsCreatedPerSweep() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findMatchable()).thenReturn(bots(8));

        scheduler(new BotAutoMatchProperties(true, 50, null), 100, new BotGameScheduler.SweepProperties(4))
                .ensureBotGameWhenIdle();

        verify(sessionService, times(4)).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aServerAtCapacityCreatesNothingAndDoesNotReadTheOverride() {
        when(sessionService.getActiveSessions()).thenReturn(8L);

        scheduler(new BotAutoMatchProperties(true, 50, null), 8, new BotGameScheduler.SweepProperties(1000))
                .ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
        verify(serverStatusService, never()).findTargetBotSessions();
    }

    @Test
    void anAbsentMaxSessionsDoesNotClamp() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findMatchable()).thenReturn(bots(6));

        scheduler(new BotAutoMatchProperties(true, 3, null), null, new BotGameScheduler.SweepProperties(1000))
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
        BotAutoMatchProperties properties = new BotAutoMatchProperties(null, null, null);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.targetGames()).isEqualTo(1);
    }

    @Test
    void picksTheOpponentAmongTheNearestMmrBots() {
        List<BotPersona> candidates = List.of(
                bot(-1, "A"), bot(-2, "B"), bot(-3, "C"), bot(-4, "D"), bot(-5, "E"));
        Map<Long, Integer> mmr = Map.of(-1L, 1000, -2L, 1010, -3L, 1020, -4L, 1030, -5L, 2000);
        RandomGenerator random = new Random(7);

        for (int i = 0; i < 200; i++) {
            List<BotPersona> pair = BotGameScheduler.pickPair(candidates, mmr, 2, random);
            long first = pair.get(0).userId();
            long opponent = pair.get(1).userId();
            assertThat(opponent).isNotEqualTo(first);
            List<Long> nearestTwo = candidates.stream()
                    .map(BotPersona::userId)
                    .filter(id -> id != first)
                    .sorted(java.util.Comparator.comparingInt((Long id) -> Math.abs(mmr.get(id) - mmr.get(first)))
                            .thenComparingLong(id -> id))
                    .limit(2)
                    .toList();
            assertThat(nearestTwo).contains(opponent);
        }
    }

    @Test
    void theOpponentIsRandomAmongTheNearestNotAlwaysTheSame() {
        List<BotPersona> candidates = List.of(bot(-1, "A"), bot(-2, "B"), bot(-3, "C"), bot(-4, "D"));
        Map<Long, Integer> mmr = Map.of(-1L, 1000, -2L, 1001, -3L, 1002, -4L, 1003);
        RandomGenerator random = new Random(3);

        Set<Long> opponentsOfA = new java.util.HashSet<>();
        for (int i = 0; i < 300; i++) {
            List<BotPersona> pair = BotGameScheduler.pickPair(candidates, mmr, 3, random);
            if (pair.get(0).userId() == -1) {
                opponentsOfA.add(pair.get(1).userId());
            }
        }

        assertThat(opponentsOfA).containsExactlyInAnyOrder(-2L, -3L, -4L);
    }

    @Test
    void aBotWithoutMmrCountsAsOneThousand() {
        List<BotPersona> candidates = List.of(bot(-1, "NoRow"), bot(-2, "Low"), bot(-3, "High"));
        Map<Long, Integer> mmr = Map.of(-2L, 400, -3L, 1100);
        // Only -1 has no row, so for it the nearest is -3 (100 away) rather than -2 (600 away).
        RandomGenerator alwaysFirst = new FixedRandom(0);

        List<BotPersona> pair = BotGameScheduler.pickPair(candidates, mmr, 1, alwaysFirst);

        assertThat(pair.get(0).userId()).isEqualTo(-1);
        assertThat(pair.get(1).userId()).isEqualTo(-3);
    }

    @Test
    void botsAlreadyPlayingOnThisServerAreNotPaired() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(sessionService.getParticipantUserIds()).thenReturn(Set.of(-1L, -2L));
        when(botPersonaService.findMatchable()).thenReturn(bots(4));

        scheduler(new BotAutoMatchProperties(true, 5, null)).ensureBotGameWhenIdle();

        ArgumentCaptor<SessionDto> captor = ArgumentCaptor.forClass(SessionDto.class);
        verify(sessionService).createSession(captor.capture());
        assertThat(Set.of(captor.getValue().uid1(), captor.getValue().uid2())).containsExactlyInAnyOrder(-3L, -4L);
    }

    @Test
    void skipsWhenFewerThanTwoBotsAreFree() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(sessionService.getParticipantUserIds()).thenReturn(Set.of(-1L, -2L, -3L));
        when(botPersonaService.findMatchable()).thenReturn(bots(4));

        scheduler(new BotAutoMatchProperties(true, 5, null)).ensureBotGameWhenIdle();

        verify(sessionService, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void noBotIsReusedWithinOneSweep() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findMatchable()).thenReturn(bots(5));

        scheduler(new BotAutoMatchProperties(true, 10, null)).ensureBotGameWhenIdle();

        // Five bots make two pairs; the fifth has nobody left to play and the sweep stops.
        ArgumentCaptor<SessionDto> captor = ArgumentCaptor.forClass(SessionDto.class);
        verify(sessionService, times(2)).createSession(captor.capture());
        List<Long> used = captor.getAllValues().stream()
                .flatMap(dto -> java.util.stream.Stream.of(dto.uid1(), dto.uid2()))
                .toList();
        assertThat(used).doesNotHaveDuplicates().hasSize(4);
    }

    @Test
    void readsMmrOnceForAllCandidatesInOneSweep() {
        when(sessionService.getActiveSessions()).thenReturn(0L);
        when(botPersonaService.findMatchable()).thenReturn(bots(6));

        scheduler(new BotAutoMatchProperties(true, 3, null)).ensureBotGameWhenIdle();

        verify(botPersonaService, times(1)).findMmrByUserIds(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void nearestOpponentCountFallsBackToThree() {
        assertThat(new BotAutoMatchProperties(null, null, null).nearestOpponentCount()).isEqualTo(3);
        assertThat(new BotAutoMatchProperties(null, null, 0).nearestOpponentCount()).isEqualTo(3);
        assertThat(new BotAutoMatchProperties(null, null, 5).nearestOpponentCount()).isEqualTo(5);
    }

    /** Always returns the same bounded value, so the first pick is deterministic. */
    private static final class FixedRandom implements RandomGenerator {
        private final int value;

        FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
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
        return new BotAutoMatchProperties(true, 1, null);
    }

    private List<BotPersona> bots(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> bot(-i, "Bot " + i))
                .toList();
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
                false,
                BotTemperament.WARM
        );
    }
}
