package com.wordonline.server.statistic.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.server.config.ServerIdentityProperties;
import com.wordonline.server.server.service.ServerInstanceIdProvider;
import com.wordonline.server.statistic.domain.GameSessionStatus;
import com.wordonline.server.statistic.repository.GameSessionRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durable lifecycle record for every non-debug game session.
 *
 * <p>Sessions otherwise exist only in {@code SessionService}'s in-memory map, and match
 * statistics are only written when a loop terminates - so a deadlocked loop or a crashed
 * process used to leave no trace at all. A row inserted at session start closes that gap:
 * whatever happens afterwards, the admin page can at least see that the game existed, and
 * a row still IN_PROGRESS long after {@code started_at} is itself the evidence that its
 * session died uncleanly.
 *
 * <p>Recording must never break gameplay: every write is caught and logged.
 */
@Slf4j
@Service
@Profile("!local")
@RequiredArgsConstructor
public class GameSessionRecordService {

    static final String REASON_SERVER_RESTART = "SERVER_RESTART";

    private final GameSessionRecordRepository repository;
    private final ServerIdentityProperties serverIdentityProperties;
    private final ServerInstanceIdProvider serverInstanceIdProvider;
    private final BuildProperties buildProperties;

    // Row ids keyed by session id. An entry is claimed exactly once by remove(), so the
    // normal-end path and the watchdog cannot both resolve the same row.
    private final Map<String, Long> recordIdsBySessionId = new ConcurrentHashMap<>();

    public void recordStart(SessionObject sessionObject) {
        try {
            long id = repository.insert(
                    sessionObject.getSessionId(),
                    sessionObject.getLeftUserId(),
                    sessionObject.getRightUserId(),
                    sessionObject.getSessionType(),
                    serverIdentityProperties.domain(),
                    serverIdentityProperties.externalPort(),
                    serverInstanceIdProvider.getInstanceId(),
                    buildProperties.getVersion());
            recordIdsBySessionId.put(sessionObject.getSessionId(), id);
        } catch (RuntimeException e) {
            log.warn("[SessionRecord] Failed to record session start; sessionId: {}",
                    sessionObject.getSessionId(), e);
        }
    }

    public void recordEnd(String sessionId, GameSessionStatus status, String endReason,
                          String endDetail, Long statisticGameId) {
        Long id = recordIdsBySessionId.remove(sessionId);
        if (id == null) {
            return;
        }
        try {
            repository.markEnded(id, status, endReason, endDetail, statisticGameId);
        } catch (RuntimeException e) {
            log.warn("[SessionRecord] Failed to record session end; sessionId: {}, status: {}",
                    sessionId, status, e);
        }
    }

    // Rows left IN_PROGRESS by a previous process of this deployment can never be
    // resolved by anyone else: the sessions lived in that process's heap. Sweeping them
    // at startup keeps "IN_PROGRESS" meaning "actually running somewhere".
    @EventListener(ApplicationReadyEvent.class)
    public void abandonLeftoverSessions() {
        try {
            int abandoned = repository.abandonLeftovers(
                    serverIdentityProperties.domain(),
                    serverIdentityProperties.externalPort(),
                    serverInstanceIdProvider.getInstanceId(),
                    REASON_SERVER_RESTART);
            if (abandoned > 0) {
                log.warn("[SessionRecord] Marked {} leftover session(s) ABANDONED after restart", abandoned);
            }
        } catch (RuntimeException e) {
            log.warn("[SessionRecord] Failed to abandon leftover sessions", e);
        }
    }
}
