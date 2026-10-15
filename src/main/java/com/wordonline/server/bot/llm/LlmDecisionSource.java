package com.wordonline.server.bot.llm;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.bot.BotDecisionSource;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.dto.Master;

import lombok.extern.slf4j.Slf4j;

/**
 * Asks an LLM provider which magic in hand to cast and where, without ever waiting for it.
 *
 * <p>{@link #think} runs on the shared bot executor, whose overflow runs on the game loop thread,
 * so it only ever looks at state and hands work off. When no request is in flight and
 * {@code minIntervalMillis} has passed since the last one, it builds the option menu from the
 * snapshot and submits the HTTP call to the dedicated LLM pool. While the call is in flight it
 * returns null, so the bot does nothing. The next think after the call finishes returns its
 * decision once.
 *
 * <p>When the call fails, or the pool is full, or the provider is backing off, the rule brain
 * decides that tick instead. {@link LlmProviderHealth} owns the back-off.
 *
 * <p>Only one think runs at a time per bot (BotAgentSystem gates it), while the request thread
 * writes the outcome; the hand-off between them goes through the two atomics.
 */
@Slf4j
public final class LlmDecisionSource implements BotDecisionSource {

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
            return stillInHand(outcome.decision(), botEye) ? outcome.decision() : null;
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
            candidates = candidateBuilder.build(botEye, parameters, botSide);
            if (!candidates.hasCastableMagic()) {
                return null;
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
                        LlmProviderHealth.parseRetryAfterMillis(response.retryAfter()));
            }
            LlmAnswer answer = client.parseResponse(response.body());
            health.recordSuccess();
            BotBrain.InputDecision decision = answer.toDecision(candidates, client.ruleId());
            log.debug("[LlmBot {}] Answer magic={} target={} -> {}", client.type(),
                    answer.magicKey(), answer.targetKey(), decision == null ? "no cast" : decision.magicId());
            completed.set(Outcome.decided(decision));
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

    // The answer was asked about an older snapshot; a card played or cycled since then is dropped
    // here rather than sent for the input handler to refuse.
    private static boolean stillInHand(BotBrain.InputDecision decision, BotEye botEye) {
        return decision != null && botEye.cardList().contains(decision.magicId());
    }

    boolean isInFlight() {
        return inFlight.get();
    }

    private record Outcome(BotBrain.InputDecision decision, boolean failed) {
        static final Outcome FAILED = new Outcome(null, true);

        static Outcome decided(BotBrain.InputDecision decision) {
            return new Outcome(decision, false);
        }
    }
}
