package com.wordonline.server.bot.llm;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Asks jev for a plan (attack, defend, save) and plays that plan every tick with code doing the aiming.
 *
 * <p>{@link LlmDecisionSource} asks jev for one cast at a time, so the bot stands still while a
 * request is in flight and the cast lands on a snapshot that is already old. Here the request only
 * changes {@link #stance}; every think casts from the current snapshot under the stance it holds,
 * so the bot acts at the rule brain's pace and aims as well as the rule brain does:
 * <ul>
 *   <li>{@code attack}: the affordable play that lands the most damage per mana on the enemy base,
 *       else a summon to push, else nothing until mana is nearly full.</li>
 *   <li>{@code defend}: the rule brain's play.</li>
 *   <li>{@code save}: nothing until the dearest card in hand is affordable, then that card.</li>
 * </ul>
 * An enemy unit close to the base overrides attack and save with the rule brain's play, so a wrong
 * plan costs tempo, never the base. A failed request leaves the stance as it was; a provider that is
 * backing off leaves the bot on the rule brain.
 *
 * <p>Only one think runs at a time per bot (BotAgentSystem gates it), while the request thread
 * writes the stance; the hand-off goes through the atomics.
 */
@Slf4j
public final class StanceDecisionSource implements BotDecisionSource {

    /** Mana at or above which a plan that holds mana spends it anyway, so income is not wasted at the cap. */
    static final int MANA_GUARD = 100;

    enum Stance {
        ATTACK("attack"), DEFEND("defend"), SAVE("save");

        final String key;

        Stance(String key) {
            this.key = key;
        }

        static Optional<Stance> fromKey(String key) {
            for (Stance stance : values()) {
                if (stance.key.equals(key)) {
                    return Optional.of(stance);
                }
            }
            return Optional.empty();
        }
    }

    private final BotDecisionSource fallback;
    private final JevProviderClient client;
    private final LlmHttpClient httpClient;
    private final LlmDecisionSource.KeySupplier keySupplier;
    private final LlmProviderHealth health;
    private final Executor executor;
    private final LlmCandidateBuilder candidateBuilder;
    private final String model;
    private final long minIntervalMillis;

    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicReference<Stance> stance = new AtomicReference<>(Stance.DEFEND);
    // Written and read only by think, which never runs twice at once for one bot.
    private boolean requestedBefore;
    private long lastRequestAtMillis;

    public StanceDecisionSource(BotDecisionSource fallback,
                                JevProviderClient client,
                                LlmHttpClient httpClient,
                                LlmDecisionSource.KeySupplier keySupplier,
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
        BotBrain.InputDecision rule = standardDecision(botEye, parameters, botSide, overdue);
        if (health.isBackingOff()) {
            return rule;
        }
        LlmCandidates candidates;
        try {
            candidates = candidateBuilder.build(botEye, parameters, botSide, null);
        } catch (RuntimeException e) {
            log.debug("[StanceBot] Building the plays failed, rule brain decides this tick", e);
            return rule;
        }
        Turn turn = new Turn(botEye, candidates, rule);
        maybeAsk(turn);
        return turn.play(stance.get());
    }

    private void maybeAsk(Turn turn) {
        if (inFlight.get()) {
            return;
        }
        long now = health.now();
        if (requestedBefore && now - lastRequestAtMillis < minIntervalMillis) {
            return;
        }
        String body;
        try {
            body = client.stanceBody(turn.criteria(), turn.candidates.state(), model);
        } catch (LlmCallFailure failure) {
            health.recordFailure(failure);
            return;
        }
        requestedBefore = true;
        lastRequestAtMillis = now;
        inFlight.set(true);
        try {
            executor.execute(() -> call(body));
        } catch (RejectedExecutionException e) {
            inFlight.set(false);
        }
    }

    // Runs on the LLM request pool.
    private void call(String body) {
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
            LlmAnswer answer = client.parseStance(response.body());
            health.recordSuccess();
            Stance.fromKey(answer.playKey()).ifPresent(chosen -> {
                Stance before = stance.getAndSet(chosen);
                log.debug("[StanceBot] Answer stance={} confidence={} (was {})", chosen.key, answer.confidence(),
                        before.key);
            });
        } catch (LlmCallFailure failure) {
            health.recordFailure(failure);
        } catch (HttpTimeoutException e) {
            health.recordFailure(new LlmCallFailure(client.type() + " request timed out"));
        } catch (IOException e) {
            health.recordFailure(new LlmCallFailure(client.type() + " request failed: " + e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            health.recordFailure(new LlmCallFailure(client.type() + " request failed: " + e.getClass().getSimpleName()));
        } finally {
            inFlight.set(false);
        }
    }

    private BotBrain.InputDecision standardDecision(BotEye botEye, Parameters parameters, Master botSide,
                                                    boolean overdue) {
        try {
            return fallback.think(botEye, parameters, botSide, overdue);
        } catch (RuntimeException e) {
            log.debug("[StanceBot] Rule brain failed", e);
            return null;
        }
    }

    Stance stance() {
        return stance.get();
    }

    /** What each plan would cast on one snapshot. */
    static final class Turn {
        final BotEye eye;
        final LlmCandidates candidates;
        final BotBrain.InputDecision rule;
        final boolean urgent;
        final int dearestCost;

        Turn(BotEye eye, LlmCandidates candidates, BotBrain.InputDecision rule) {
            this.eye = eye;
            this.candidates = candidates;
            this.rule = rule;
            this.urgent = Boolean.TRUE.equals(candidates.state().get("enemyNearOurBase"));
            this.dearestCost = dearestCost(candidates.state());
        }

        BotBrain.InputDecision play(Stance stance) {
            if (stance == Stance.DEFEND || urgent) {
                return rule;
            }
            Optional<LlmCandidates.Play> play = stance == Stance.ATTACK ? attackPlay() : savePlay();
            if (play.isPresent()) {
                return cast(play.get(), stance);
            }
            return eye.mana() >= MANA_GUARD ? rule : null;
        }

        Optional<LlmCandidates.Play> attackPlay() {
            Optional<LlmCandidates.Play> baseHit = plays().stream()
                    .filter(play -> play.baseDamage() > 0)
                    .max(Comparator.comparingDouble(play -> play.baseDamage() / Math.max(1, play.manaCost())));
            if (baseHit.isPresent()) {
                return baseHit;
            }
            return plays().stream()
                    .filter(LlmCandidates.Play::summon)
                    .max(Comparator.comparingInt(LlmCandidates.Play::manaCost));
        }

        Optional<LlmCandidates.Play> savePlay() {
            return plays().stream()
                    .filter(play -> play.manaCost() >= dearestCost)
                    .max(Comparator.comparingDouble(play -> play.baseDamage() + play.unitDamage()));
        }

        /** The option text jev reads for each plan, saying what it would cast right now. */
        Map<String, String> criteria() {
            Map<String, String> criteria = new LinkedHashMap<>();
            criteria.put(Stance.ATTACK.key, "Attack: " + (urgent
                    ? "not possible now, an enemy unit is close to our base, so this defends instead."
                    : attackPlay().map(play -> "cast " + play.description())
                            .orElse("nothing in hand can reach the enemy base or push; hold mana.")));
            criteria.put(Stance.DEFEND.key, "Defend: " + (rule == null
                    ? "nothing to defend with right now; hold mana."
                    : "the cautious rule bot's play: " + describe(rule)));
            criteria.put(Stance.SAVE.key, "Save: " + (urgent
                    ? "not possible now, an enemy unit is close to our base, so this defends instead."
                    : savePlay().map(play -> "the dearest card is affordable, cast " + play.description())
                            .orElse("cast nothing until the dearest card in hand (cost " + dearestCost
                                    + ") is affordable; mana is " + eye.mana() + " now.")));
            return criteria;
        }

        private String describe(BotBrain.InputDecision decision) {
            return plays().stream()
                    .filter(play -> play.magicId() == decision.magicId())
                    .min(Comparator.comparingDouble(play -> play.target().distance(decision.target())))
                    .map(LlmCandidates.Play::description)
                    .orElse(decision.reason() == null ? "a cast" : decision.reason());
        }

        private List<LlmCandidates.Play> plays() {
            return List.copyOf(candidates.plays().values());
        }

        private static BotBrain.InputDecision cast(LlmCandidates.Play play, Stance stance) {
            return new BotBrain.InputDecision(play.magicId(), new Vector3(play.target()), "llm.jev." + stance.key,
                    LlmAnswer.truncate(stance.key + ": " + play.description()));
        }

        @SuppressWarnings("unchecked")
        private static int dearestCost(Map<String, Object> state) {
            Object hand = state.get("hand");
            if (!(hand instanceof List<?> cards)) {
                return Integer.MAX_VALUE;
            }
            return cards.stream()
                    .filter(Map.class::isInstance)
                    .map(card -> ((Map<String, Object>) card).get("cost"))
                    .filter(Number.class::isInstance)
                    .mapToInt(cost -> ((Number) cost).intValue())
                    .filter(cost -> cost < LlmCandidateBuilder.UNPRICED_COST)
                    .max()
                    .orElse(Integer.MAX_VALUE);
        }
    }
}
