package com.wordonline.server.bot.llm;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.bot.BotDecisionSource;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

import lombok.extern.slf4j.Slf4j;

/**
 * Asks an LLM provider which magic in hand to cast and where, without ever waiting for it.
 *
 * <p>{@link #think} runs on the shared bot executor, whose overflow runs on the game loop thread,
 * so it only ever looks at state and hands work off. When no request is in flight and
 * {@code minIntervalMillis} has passed since the last one, it asks the rule brain what it would
 * do, builds the play menu from the snapshot with that decision as the standard play, and submits
 * the HTTP call to the dedicated LLM pool. A menu with a single play and no wait is cast at once
 * without asking. While the call is in flight it
 * returns null, so the bot does nothing. The next think after the call finishes returns its
 * decision once.
 *
 * <p>When the call fails, or the pool is full, or the provider is backing off, the rule brain
 * decides that tick instead. {@link LlmProviderHealth} owns the back-off.
 *
 * <p>At DEBUG, the full request body is logged on the first request and every
 * {@value #BODY_LOG_EVERY}th after it, and every answer is logged with its play. Neither carries the
 * API key, which only ever travels in a header.
 *
 * <p>Only one think runs at a time per bot (BotAgentSystem gates it), while the request thread
 * writes the outcome; the hand-off between them goes through the two atomics.
 */
@Slf4j
public final class LlmDecisionSource implements BotDecisionSource {

    /** Requests between two logged request bodies, per bot. */
    static final int BODY_LOG_EVERY = 20;

    /** Supplies the plaintext API key on the request thread. */
    @FunctionalInterface
    public interface KeySupplier {
        String get() throws LlmCallFailure;
    }

    private final BotDecisionSource fallback;
    private final LlmProviderClient client;
    private final LlmHttpClient httpClient;
    private final KeySupplier keySupplier;
    private final LlmProviderHealth health;
    private final Executor executor;
    private final LlmCandidateBuilder candidateBuilder;
    private final String model;
    private final long minIntervalMillis;

    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicReference<Outcome> completed = new AtomicReference<>();
    // Written and read only by think, which never runs twice at once for one bot.
    private boolean requestedBefore;
    private long lastRequestAtMillis;
    private long requestCount;

    public LlmDecisionSource(BotDecisionSource fallback,
                             LlmProviderClient client,
                             LlmHttpClient httpClient,
                             KeySupplier keySupplier,
                             LlmProviderHealth health,
                             Executor executor,
                             LlmCandidateBuilder candidateBuilder,
                             String model,
                             long minIntervalMillis) {
        this.fallback = fallback;
        this.client = client;
        this.httpClient = httpClient;
        this.keySupplier = keySupplier;
        this.health = health;
        this.executor = executor;
        this.candidateBuilder = candidateBuilder;
        this.model = model;
        this.minIntervalMillis = minIntervalMillis;
    }

    @Override
    public BotBrain.InputDecision think(BotEye botEye, Parameters parameters, Master botSide, boolean overdue) {
        Outcome outcome = completed.getAndSet(null);
        if (outcome != null) {
            if (outcome.failed()) {
                return fallback.think(botEye, parameters, botSide, overdue);
            }
            return stillInHand(outcome.decision(), botEye)
                    ? reaim(outcome, botEye, parameters, botSide, overdue)
                    : null;
        }
        if (inFlight.get()) {
            return null;
        }
        if (health.isBackingOff()) {
            return fallback.think(botEye, parameters, botSide, overdue);
        }
        long now = health.now();
        if (requestedBefore && now - lastRequestAtMillis < minIntervalMillis) {
            return null;
        }

        LlmCandidates candidates;
        String body;
        try {
            candidates = candidateBuilder.build(botEye, parameters, botSide,
                    standardDecision(botEye, parameters, botSide, overdue));
            if (!candidates.hasPlays()) {
                return null;
            }
            if (candidates.plays().size() == 1 && !candidates.offersWait()) {
                String onlyKey = candidates.plays().keySet().iterator().next();
                return new LlmAnswer(onlyKey, "Only play: " + candidates.describe(onlyKey), null)
                        .toDecision(candidates, client.ruleId());
            }
            body = client.requestBody(candidates, model);
        } catch (LlmCallFailure failure) {
            health.recordFailure(failure);
            return fallback.think(botEye, parameters, botSide, overdue);
        } catch (RuntimeException e) {
            log.debug("[LlmBot {}] Building the request failed", client.type(), e);
            return fallback.think(botEye, parameters, botSide, overdue);
        }

        requestedBefore = true;
        lastRequestAtMillis = now;
        requestCount++;
        if (log.isDebugEnabled() && shouldLogBody(requestCount)) {
            log.debug("[LlmBot {}] Request #{} body: {}", client.type(), requestCount, body);
        }
        inFlight.set(true);
        try {
            executor.execute(() -> call(candidates, body));
        } catch (RejectedExecutionException e) {
            inFlight.set(false);
            log.debug("[LlmBot {}] Request pool full, rule brain decides this tick", client.type());
            return fallback.think(botEye, parameters, botSide, overdue);
        }
        return null;
    }

    // Runs on the LLM request pool.
    private void call(LlmCandidates candidates, String body) {
        try {
            String apiKey = keySupplier.get();
            LlmHttpClient.Response response = httpClient.post(client.uri(), client.headers(apiKey), body);
            if (response.status() < 200 || response.status() >= 300) {
                String message = LlmProviderClient.errorMessage(response.body());
                throw new LlmCallFailure(
                        client.type() + " returned HTTP " + response.status() + (message == null ? "" : ": " + message),
                        response.status(),
                        LlmProviderHealth.parseRetryAfterMillis(response.retryAfter()),
                        message);
            }
            LlmAnswer answer = client.parseResponse(response.body());
            health.recordSuccess();
            BotBrain.InputDecision decision = answer.toDecision(candidates, client.ruleId());
            log.debug("[LlmBot {}] Answer play={} confidence={} mana={} pressure={} options={} -> {}", client.type(),
                    answer.playKey(), answer.confidence(), candidates.state().get("mana"),
                    candidates.state().get("pressure"), candidates.criteria().keySet(),
                    candidates.describe(answer.playKey()));
            completed.set(Outcome.decided(decision, candidates.plays().get(answer.playKey())));
        } catch (LlmCallFailure failure) {
            fail(failure);
        } catch (HttpTimeoutException e) {
            fail(new LlmCallFailure(client.type() + " request timed out"));
        } catch (IOException e) {
            fail(new LlmCallFailure(client.type() + " request failed: " + e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completed.set(Outcome.FAILED);
        } catch (RuntimeException e) {
            fail(new LlmCallFailure(client.type() + " request failed: " + e.getClass().getSimpleName()));
        } finally {
            // Cleared after the outcome is stored, so a think that sees no request in flight also
            // sees its outcome.
            inFlight.set(false);
        }
    }

    private void fail(LlmCallFailure failure) {
        health.recordFailure(failure);
        completed.set(Outcome.FAILED);
    }

    // The rule brain is stateless between calls (BotBrain keeps only its persona, parsers and the
    // hospitality director's fixed share), so asking it here changes nothing it decides later. A
    // throw only costs the standard play.
    private BotBrain.InputDecision standardDecision(BotEye botEye, Parameters parameters, Master botSide,
                                                    boolean overdue) {
        try {
            return fallback.think(botEye, parameters, botSide, overdue);
        } catch (RuntimeException e) {
            log.debug("[LlmBot {}] Rule brain failed while building the standard play", client.type(), e);
            return null;
        }
    }

    // The answer was asked about an older snapshot; a card played or cycled since then is dropped
    // here rather than sent for the input handler to refuse.
    private static boolean stillInHand(BotBrain.InputDecision decision, BotEye botEye) {
        return decision != null && botEye.cardList().contains(decision.magicId());
    }

    /** The first request and every {@link #BODY_LOG_EVERY}th after it. */
    static boolean shouldLogBody(long requestNumber) {
        return requestNumber % BODY_LOG_EVERY == 1;
    }

    boolean isInFlight() {
        return inFlight.get();
    }

    /**
     * The answer was given for the snapshot the request was built from, at least one reaction
     * interval ago. Units have walked since, and a small blast aimed at where they were hits
     * nothing. The model chose the magic and the kind of play; the point is recomputed from the
     * current snapshot: the rule brain's fresh pick for a standard play, otherwise the fresh play of
     * the same magic nearest the old point. A damage spell with no target left is dropped rather
     * than cast into empty ground; a summon keeps its old point.
     */
    private BotBrain.InputDecision reaim(Outcome outcome, BotEye botEye, Parameters parameters,
                                         Master botSide, boolean overdue) {
        BotBrain.InputDecision decision = outcome.decision();
        LlmCandidates.Play chosen = outcome.play();
        if (decision == null || chosen == null) {
            return decision;
        }
        try {
            BotBrain.InputDecision standard = standardDecision(botEye, parameters, botSide, overdue);
            if (chosen.description().startsWith(LlmCandidateBuilder.STANDARD_TACTIC)
                    && standard != null && standard.magicId() == decision.magicId()) {
                return withTarget(decision, standard.target());
            }
            LlmCandidates fresh = candidateBuilder.build(botEye, parameters, botSide, standard);
            return fresh.plays().values().stream()
                    .filter(play -> play.magicId() == decision.magicId())
                    .min(Comparator.comparingDouble(play -> play.target().distance(chosen.target())))
                    .map(play -> withTarget(decision, play.target()))
                    .orElse(chosen.summon() ? decision : null);
        } catch (RuntimeException e) {
            log.debug("[LlmBot {}] Re-aiming failed, casting at the answered point", client.type(), e);
            return decision;
        }
    }

    private static BotBrain.InputDecision withTarget(BotBrain.InputDecision decision, Vector3 target) {
        return new BotBrain.InputDecision(decision.magicId(), new Vector3(target), decision.ruleId(),
                decision.reason());
    }

    private record Outcome(BotBrain.InputDecision decision, LlmCandidates.Play play, boolean failed) {
        static final Outcome FAILED = new Outcome(null, null, true);

        static Outcome decided(BotBrain.InputDecision decision, LlmCandidates.Play play) {
            return new Outcome(decision, play, false);
        }
    }
}
