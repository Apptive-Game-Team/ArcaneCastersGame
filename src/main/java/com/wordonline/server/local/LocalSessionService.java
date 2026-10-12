package com.wordonline.server.local;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.wordonline.server.game.domain.SessionObject;
import com.wordonline.server.game.service.GameLoop;
import com.wordonline.server.session.dto.SessionDto;
import com.wordonline.server.session.service.SessionCreationResult;
import com.wordonline.server.session.service.SessionService;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates the one session a locally hosted server runs, from the session the lobby issued to the
 * client (root plan, local hosting contracts, section 4).
 *
 * <p>The lobby has already decided everything a shared server's lobby call would carry: the session
 * id, the bot, the scenario, the player's deck and owned magics. This writes the player into the
 * in-memory database so the ordinary Practice and PVE paths find them exactly as they would find a
 * player in the shared database, then creates the session through {@link SessionService}.
 */
@Slf4j
@Service
@Profile("local")
public class LocalSessionService implements ApplicationListener<WebServerInitializedEvent> {

    static final int MAXIMUM_TICK_RATE = 1000;
    private static final int OWNED_MAGIC_COUNT = 3;
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(3);

    private final SessionService sessionService;
    private final LocalStatisticService localStatisticService;
    private final LocalTokenJwtDecoder localTokenJwtDecoder;
    private final LocalDatabase localDatabase;
    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    private volatile int port;

    public LocalSessionService(SessionService sessionService, LocalStatisticService localStatisticService,
                               LocalTokenJwtDecoder localTokenJwtDecoder, LocalDatabase localDatabase,
                               JdbcClient jdbcClient, TransactionTemplate transactionTemplate) {
        this.sessionService = sessionService;
        this.localStatisticService = localStatisticService;
        this.localTokenJwtDecoder = localTokenJwtDecoder;
        this.localDatabase = localDatabase;
        this.jdbcClient = jdbcClient;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        port = event.getWebServer().getPort();
    }

    public synchronized LocalSessionResponse create(LocalSessionRequest request) {
        LocalSessionRequest.Validated session = request.validate(MAXIMUM_TICK_RATE);
        if (!Objects.equals(session.staticVersion(), localDatabase.staticVersion())) {
            log.warn("[Local] Session asks for staticVersion {} but {} is loaded; sessionId: {}",
                    session.staticVersion(), localDatabase.staticVersion(), session.sessionId());
        }

        // A retried request for the session that is already running must not end it.
        SessionObject existing = sessionService.getSessionObject(session.sessionId());
        if (existing != null && existing.getLeftUserId() == session.leftUserId()) {
            localTokenJwtDecoder.setMemberId(session.leftUserId());
            return response(session.sessionId());
        }
        if (existing != null) {
            throw new IllegalArgumentException("sessionId already belongs to another player");
        }

        abandonRunningSessions();
        requireKnownMagics(session.leftDeckCardIds());
        transactionTemplate.executeWithoutResult(status -> writePlayer(session));
        // The token speaks for this player from now on, including the WebSocket connection the
        // client opens right after this response.
        localTokenJwtDecoder.setMemberId(session.leftUserId());

        SessionDto sessionDto = new SessionDto(
                session.sessionId(),
                session.leftUserId(),
                session.rightUserId(),
                session.sessionType(),
                session.scenarioId(),
                session.leftDeckCardIds(),
                null,
                session.tickRate());
        SessionCreationResult result = sessionService.createSession(session.sessionId(), sessionDto);
        if (!result.ready()) {
            log.warn("[Local] Session loop has not started yet; sessionId: {}", session.sessionId());
        }
        log.info("[Local] Session created; sessionId: {}, type: {}, tickRate: {}",
                session.sessionId(), session.sessionType(), session.tickRate());
        return response(session.sessionId());
    }

    /**
     * Ends every running session as ABANDONED and waits up to a few seconds for their loops to
     * finish, so their {@code matchResult} lines are written before this returns.
     */
    public synchronized void abandonRunningSessions() {
        List<SessionObject> running = sessionService.getSessionObjects();
        for (SessionObject sessionObject : running) {
            GameLoop loop = sessionObject.getGameLoop();
            if (loop == null) {
                continue;
            }
            // A match already decided (in its last second of frames, or its loop stopping) keeps its
            // result; only one cut off in play is ABANDONED.
            if (!loop.getGameContext().getResultChecker().checkResult()) {
                localStatisticService.markAbandoned(loop.getGameContext());
            }
            loop.close();
            log.info("[Local] Ending running session; sessionId: {}", sessionObject.getSessionId());
        }
        long deadline = System.nanoTime() + STOP_TIMEOUT.toNanos();
        for (SessionObject sessionObject : running) {
            GameLoop loop = sessionObject.getGameLoop();
            while (loop != null && localStatisticService.hasUnreportedResult(loop.getGameContext())
                    && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private LocalSessionResponse response(String sessionId) {
        return new LocalSessionResponse(sessionId, "ws://127.0.0.1:" + port + "/ws");
    }

    private void requireKnownMagics(List<Long> magicIds) {
        List<Long> distinct = magicIds.stream().distinct().toList();
        long known = jdbcClient.sql("SELECT COUNT(*) FROM magics WHERE id IN (:ids)")
                .param("ids", distinct)
                .query(Long.class)
                .single();
        if (known != distinct.size()) {
            throw new IllegalArgumentException("leftDeckCardIds contains a magic the static data does not have");
        }
    }

    // The player's row, owned magics and selected deck, replacing whatever an earlier session wrote.
    private void writePlayer(LocalSessionRequest.Validated session) {
        long userId = session.leftUserId();
        int updated = jdbcClient.sql("UPDATE users SET novice_progress = :progress, status = 'Online' WHERE id = :id")
                .param("progress", session.noviceProgress())
                .param("id", userId)
                .update();
        if (updated == 0) {
            jdbcClient.sql("INSERT INTO users (id, novice_progress) VALUES (:id, :progress)")
                    .param("id", userId)
                    .param("progress", session.noviceProgress())
                    .update();
        }

        jdbcClient.sql("DELETE FROM user_magics WHERE user_id = :id").param("id", userId).update();
        for (Long magicId : session.ownedMagicIds().stream().distinct().toList()) {
            jdbcClient.sql("INSERT INTO user_magics (user_id, magic_id, count) VALUES (:userId, :magicId, :count)")
                    .param("userId", userId)
                    .param("magicId", magicId)
                    .param("count", OWNED_MAGIC_COUNT)
                    .update();
        }

        jdbcClient.sql("UPDATE users SET selected_deck_id = NULL WHERE id = :id").param("id", userId).update();
        jdbcClient.sql("DELETE FROM deck_cards WHERE deck_id IN (SELECT id FROM decks WHERE user_id = :id)")
                .param("id", userId)
                .update();
        jdbcClient.sql("DELETE FROM decks WHERE user_id = :id").param("id", userId).update();
        jdbcClient.sql("INSERT INTO decks (name, user_id) VALUES ('local', :id)").param("id", userId).update();
        long deckId = jdbcClient.sql("SELECT MAX(id) FROM decks WHERE user_id = :id")
                .param("id", userId)
                .query(Long.class)
                .single();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        session.leftDeckCardIds().forEach(magicId -> counts.merge(magicId, 1, Integer::sum));
        counts.forEach((magicId, count) ->
                jdbcClient.sql("INSERT INTO deck_cards (deck_id, magic_id, count) VALUES (:deckId, :magicId, :count)")
                        .param("deckId", deckId)
                        .param("magicId", magicId)
                        .param("count", count)
                        .update());
        jdbcClient.sql("UPDATE users SET selected_deck_id = :deckId WHERE id = :id")
                .param("deckId", deckId)
                .param("id", userId)
                .update();
    }

    /** Response of {@code POST /api/local/sessions}. */
    public record LocalSessionResponse(String sessionId, String webSocketUrl) {
    }
}
