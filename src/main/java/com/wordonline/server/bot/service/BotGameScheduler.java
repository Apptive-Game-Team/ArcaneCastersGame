package com.wordonline.server.bot.service;

import com.wordonline.server.bot.config.BotAutoMatchProperties;
import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.game.domain.SessionType;
import com.wordonline.server.server.config.ServerIdentityProperties;
import com.wordonline.server.server.entity.ServerState;
import com.wordonline.server.server.service.ServerStatusService;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotGameScheduler {

    private static final String SESSION_PREFIX = "bot-auto-";
    /** Same default as MmrService for a bot without a users row. */
    private static final int DEFAULT_MMR = 1000;

    private final SessionService sessionService;
    private final BotPersonaService botPersonaService;
    private final ServerStatusService serverStatusService;
    private final BotAutoMatchProperties botAutoMatchProperties;
    private final ServerIdentityProperties serverIdentityProperties;
    private final SweepProperties sweepProperties;

    @Scheduled(fixedDelayString = "${bot.auto-match.check-interval-ms:60000}")
    public void ensureBotGameWhenIdle() {
        if (!botAutoMatchProperties.enabled() || serverStatusService.getCurrentState() != ServerState.ACTIVE) {
            return;
        }

        long activeSessions = sessionService.getActiveSessions();
        int capacity = capacity();
        if (activeSessions >= capacity) {
            // Nothing this sweep can do, so it does not pay for the target override read.
            return;
        }

        long missingGames = Math.min(resolveTargetSessions(), capacity) - activeSessions;
        if (missingGames <= 0) {
            return;
        }

        List<BotPersona> matchable = botPersonaService.findMatchable();
        if (matchable.size() < 2) {
            log.warn("[BotGameScheduler] Need at least two matchable bot personas. matchableCount={}", matchable.size());
            return;
        }
        Map<Long, Integer> mmrByUserId = botPersonaService.findMmrByUserIds(
                matchable.stream().map(BotPersona::userId).toList());
        Set<Long> busyUserIds = new HashSet<>(sessionService.getParticipantUserIds());

        // Ramp up over several sweeps instead of creating the whole shortfall in one go. Each
        // session costs a deck query, a record insert, an object graph and a Thread.start, all
        // synchronous on the scheduler thread, and the loop watchdog shares that pool.
        long sessionsThisSweep = Math.min(missingGames, sweepProperties.maxSessionsPerSweep());
        for (long i = 0; i < sessionsThisSweep; i++) {
            // Free bots are preferred, but with fewer than two of them the pool is every matchable
            // bot: a server asked for more bot games than it has bot pairs must still reach its target.
            List<BotPersona> free = matchable.stream()
                    .filter(bot -> !busyUserIds.contains(bot.userId()))
                    .toList();
            List<BotPersona> pool = free.size() >= 2 ? free : matchable;
            List<BotPersona> pair = pickPair(pool, mmrByUserId,
                    botAutoMatchProperties.nearestOpponentCount(), ThreadLocalRandom.current());
            busyUserIds.add(pair.get(0).userId());
            busyUserIds.add(pair.get(1).userId());
            createBotGame(pair.get(0), pair.get(1));
        }
    }

    /**
     * Picks the first bot at random, then its opponent at random among the {@code nearestCount}
     * other bots closest to it in MMR. A bot without a mmr entry counts as {@value #DEFAULT_MMR}.
     * Returns {@code [first, opponent]}; {@code candidates} needs at least two bots.
     */
    static List<BotPersona> pickPair(List<BotPersona> candidates,
                                     Map<Long, Integer> mmrByUserId,
                                     int nearestCount,
                                     RandomGenerator random) {
        BotPersona first = candidates.get(random.nextInt(candidates.size()));
        int firstMmr = mmrOf(first, mmrByUserId);
        List<BotPersona> nearest = candidates.stream()
                .filter(bot -> bot.userId() != first.userId())
                .sorted(Comparator
                        .comparingInt((BotPersona bot) -> Math.abs(mmrOf(bot, mmrByUserId) - firstMmr))
                        .thenComparingLong(BotPersona::userId))
                .limit(nearestCount)
                .toList();
        return List.of(first, nearest.get(random.nextInt(nearest.size())));
    }

    private static int mmrOf(BotPersona bot, Map<Long, Integer> mmrByUserId) {
        return mmrByUserId.getOrDefault(bot.userId(), DEFAULT_MMR);
    }

    /**
     * The capacity this server publishes to the {@code servers} table. The admin's target
     * override is clamped to it, because a box measured to hold about a hundred sessions will
     * still obey a target of two hundred and fall over. A missing value means no clamp; the
     * property has a default, so that only happens in a context that does not bind it.
     */
    private int capacity() {
        Integer maxSessions = serverIdentityProperties.maxSessions();
        return maxSessions == null ? Integer.MAX_VALUE : maxSessions;
    }

    /**
     * The admin's per-server override on the {@code servers} row wins over the static
     * {@code bot.auto-match.target-games} default. A failing database read must not kill the
     * scheduler, so that also falls back to the configured default.
     */
    private int resolveTargetSessions() {
        try {
            return serverStatusService.findTargetBotSessions()
                    .orElseGet(botAutoMatchProperties::targetGames);
        } catch (RuntimeException exception) {
            log.warn("[BotGameScheduler] Can't read the target bot session override, using the configured default {}",
                    botAutoMatchProperties.targetGames(), exception);
            return botAutoMatchProperties.targetGames();
        }
    }

    /**
     * How many sessions one sweep may create. This belongs with the other
     * {@code bot.auto-match} settings and shares their prefix, so the key is
     * {@code bot.auto-match.max-sessions-per-sweep}.
     */
    @ConfigurationProperties(prefix = "bot.auto-match")
    public record SweepProperties(Integer maxSessionsPerSweep) {

        private static final int FALLBACK_MAX_SESSIONS_PER_SWEEP = 5;

        public SweepProperties {
            maxSessionsPerSweep = maxSessionsPerSweep == null || maxSessionsPerSweep < 1
                    ? FALLBACK_MAX_SESSIONS_PER_SWEEP
                    : maxSessionsPerSweep;
        }
    }

    private void createBotGame(BotPersona leftBot, BotPersona rightBot) {
        SessionDto sessionDto = new SessionDto(
                SESSION_PREFIX + UUID.randomUUID(),
                leftBot.userId(),
                rightBot.userId(),
                SessionType.Practice,
                null
        );

        sessionService.createSession(sessionDto);
        log.info("[BotGameScheduler] Auto bot game created. sessionId={}, leftBot={}, rightBot={}",
                sessionDto.sessionId(), leftBot.name(), rightBot.name());
    }
}
