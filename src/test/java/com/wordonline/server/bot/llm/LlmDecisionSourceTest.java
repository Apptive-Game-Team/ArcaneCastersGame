package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.wordonline.server.bot.domain.BotBrainType;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.bot.BotDecisionSource;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.bot.BotVisibleObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

class LlmDecisionSourceTest {

    private static final String JEV_ANSWER = """
            {"answers":{"play":{"type":"choice","choice":"p1","confidence":0.9}}}
            """;
    private static final long MIN_INTERVAL = 1_500;

    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final LlmProviderHealth health = new LlmProviderHealth(BotBrainType.JEV, clock::get);
    private final CountingRule rule = new CountingRule();
    private final ManualExecutor executor = new ManualExecutor();
    private final Parameters parameters = LlmTestFixtures.parameters();
    private final BotEye eye = LlmTestFixtures.eye();

    private LlmHttpClient.Response nextResponse = new LlmHttpClient.Response(200, JEV_ANSWER, null);
    private final AtomicInteger httpCalls = new AtomicInteger();
    private final LlmHttpClient fakeHttp = (uri, headers, body) -> {
        httpCalls.incrementAndGet();
        return nextResponse;
    };

    @Test
    void doesNothingWhileInFlightThenReturnsTheDecisionOnce() {
        LlmDecisionSource source = source(fakeHttp, executor);

        assertThat(think(source)).isNull();
        assertThat(executor.pending()).isEqualTo(1);
        assertThat(source.isInFlight()).isTrue();
        assertThat(think(source)).isNull();
        assertThat(executor.pending()).as("no second request while one is in flight").isEqualTo(1);

        executor.runAll();

        BotBrain.InputDecision decision = think(source);
        assertThat(decision.magicId()).isEqualTo(LlmTestFixtures.FIRE_SHOT_ID);
        assertThat(decision.target()).isEqualTo(new Vector3(3.5f, 0, 4));
        assertThat(decision.ruleId()).isEqualTo("llm.jev");
        assertThat(think(source)).as("returned once, and the interval has not passed").isNull();
        assertThat(rule.calls).as("asked for the standard play, then again to re-aim at cast time").hasValue(2);
    }

    @Test
    void reaimsTheAnsweredPlayAtTheCurrentSnapshot() {
        LlmDecisionSource source = source(fakeHttp, executor);
        think(source);
        executor.runAll();

        // The answered play aims at the FireSlime at (3.5, 4); it has walked on by the time the
        // answer is cast.
        BotEye moved = new BotEye(eye.gameObjectList().stream()
                .map(object -> object.id() == 14
                        ? new BotVisibleObject(14, object.master(), object.type(), new Vector3(2.5f, 0, 4.5f),
                                object.hp(), object.mob(), object.targetable())
                        : object)
                .toList(), eye.cardList(), eye.mana(), eye.enemyPlayerHp());

        BotBrain.InputDecision decision = source.think(moved, parameters, Master.LeftPlayer, false);

        assertThat(decision.magicId()).isEqualTo(LlmTestFixtures.FIRE_SHOT_ID);
        assertThat(decision.target().distance(new Vector3(2.5f, 0, 4.5f))).isLessThan(0.01);
    }

    @Test
    void waitsForTheMinimumIntervalBetweenRequests() {
        LlmDecisionSource source = source(fakeHttp, executor);
        think(source);
        executor.runAll();
        think(source);

        clock.addAndGet(MIN_INTERVAL - 1);
        assertThat(think(source)).isNull();
        assertThat(executor.pending()).isZero();

        clock.addAndGet(1);
        think(source);
        assertThat(executor.pending()).isEqualTo(1);
    }

    @Test
    void sendsTheKeyAsABearerHeader() {
        StringBuilder authorization = new StringBuilder();
        LlmDecisionSource source = source((uri, headers, body) -> {
            authorization.append(headers.get("Authorization"));
            return nextResponse;
        }, executor);

        think(source);
        executor.runAll();

        assertThat(authorization).hasToString("Bearer sk-test");
    }

    @Test
    void fallsBackToTheRuleBrainOnFailure() {
        nextResponse = new LlmHttpClient.Response(500, "{\"error\":{\"code\":500,\"message\":\"upstream\"}}", null);
        LlmDecisionSource source = source(fakeHttp, executor);

        think(source);
        executor.runAll();

        assertThat(think(source)).isSameAs(CountingRule.DECISION);
        assertThat(rule.calls).as("once for the standard play, once as the fallback").hasValue(2);
        assertThat(health.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void fallsBackWhenTheKeyIsMissingOrTheCallTimesOut() {
        LlmDecisionSource missingKey = new LlmDecisionSource(rule, client(), fakeHttp,
                () -> {
                    throw new LlmCallFailure("No API key is stored for JEV.");
                },
                health, executor, LlmTestFixtures.builder(), "jev-latest", MIN_INTERVAL);
        think(missingKey);
        executor.runAll();
        assertThat(think(missingKey)).isSameAs(CountingRule.DECISION);
        assertThat(httpCalls).hasValue(0);

        LlmDecisionSource timeout = source((uri, headers, body) -> {
            throw new HttpTimeoutException("request timed out");
        }, executor);
        think(timeout);
        executor.runAll();
        assertThat(think(timeout)).isSameAs(CountingRule.DECISION);
    }

    @Test
    void anUnparseableAnswerFallsBackButAWaitAnswerCastsNothing() {
        nextResponse = new LlmHttpClient.Response(200, "not json", null);
        LlmDecisionSource source = source(fakeHttp, executor);
        think(source);
        executor.runAll();
        assertThat(think(source)).isSameAs(CountingRule.DECISION);

        clock.addAndGet(MIN_INTERVAL);
        nextResponse = new LlmHttpClient.Response(200, "{\"answers\":{\"play\":{\"choice\":\"wait\"}}}", null);
        think(source);
        executor.runAll();
        assertThat(think(source)).isNull();
        assertThat(rule.calls).as("standard, fallback, standard").hasValue(3);
        assertThat(health.consecutiveFailures()).isZero();
    }

    @Test
    void backsOffAfterThreeFailuresAndPlaysTheRuleBrainMeanwhile() {
        nextResponse = new LlmHttpClient.Response(401, "{\"error\":{\"code\":401,\"message\":\"bad key\"}}", null);
        LlmDecisionSource source = source(fakeHttp, executor);

        for (int i = 0; i < LlmProviderHealth.FAILURES_BEFORE_BACKOFF; i++) {
            think(source);
            executor.runAll();
            think(source);
            clock.addAndGet(MIN_INTERVAL);
        }
        assertThat(httpCalls).hasValue(3);
        assertThat(health.isBackingOff()).isTrue();

        assertThat(think(source)).isSameAs(CountingRule.DECISION);
        assertThat(executor.pending()).as("no request during the back-off").isZero();

        clock.addAndGet(LlmProviderHealth.BACKOFF_MILLIS);
        assertThat(health.isBackingOff()).isFalse();
        think(source);
        assertThat(executor.pending()).isEqualTo(1);
    }

    @Test
    void honoursRetryAfterOnRateLimit() {
        nextResponse = new LlmHttpClient.Response(429, "{}", "120");
        LlmDecisionSource source = source(fakeHttp, executor);

        think(source);
        executor.runAll();

        assertThat(health.isBackingOff()).as("one 429 with Retry-After backs off at once").isTrue();
        clock.addAndGet(119_000);
        assertThat(health.isBackingOff()).isTrue();
        clock.addAndGet(1_000);
        assertThat(health.isBackingOff()).isFalse();
    }

    @Test
    void aFullPoolDropsTheRequestAndUsesTheRuleBrain() {
        LlmDecisionSource source = source(fakeHttp, runnable -> {
            throw new RejectedExecutionException("full");
        });

        assertThat(think(source)).isSameAs(CountingRule.DECISION);
        assertThat(source.isInFlight()).isFalse();
    }

    @Test
    void aCardPlayedMeanwhileIsDropped() {
        LlmDecisionSource source = source(fakeHttp, executor);
        think(source);
        executor.runAll();

        BotEye later = LlmTestFixtures.eye(java.util.List.of(LlmTestFixtures.ROCK_GOLEM_ID), 9);
        assertThat(source.think(later, parameters, Master.LeftPlayer, false)).isNull();
    }

    @Test
    void onlyWaitPossibleSendsNoRequest() {
        LlmDecisionSource source = source(fakeHttp, executor);
        BotEye poor = LlmTestFixtures.eye(eye.cardList(), 1);

        assertThat(source.think(poor, parameters, Master.LeftPlayer, false)).isNull();
        assertThat(executor.pending()).isZero();
    }

    @Test
    void neverBlocksTheCallerOnASlowProvider() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            LlmDecisionSource source = source((uri, headers, body) -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
                return new LlmHttpClient.Response(200, JEV_ANSWER, null);
            }, pool);

            assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
                assertThat(think(source)).isNull();
                for (int i = 0; i < 10; i++) {
                    assertThat(think(source)).isNull();
                }
            });

            release.countDown();
            long deadline = System.currentTimeMillis() + 2_000;
            while (source.isInFlight() && System.currentTimeMillis() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(think(source).magicId()).isEqualTo(LlmTestFixtures.FIRE_SHOT_ID);
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void offersTheRuleBrainsPlayAndMapsItBack() {
        nextResponse = new LlmHttpClient.Response(200, "{\"answers\":{\"play\":{\"choice\":\"p0\"}}}", null);
        StringBuilder sent = new StringBuilder();
        LlmDecisionSource source = source((uri, headers, body) -> {
            sent.append(body);
            return nextResponse;
        }, executor);

        think(source);
        executor.runAll();

        assertThat(sent).contains("\"p0\":\"STANDARD TACTIC (what the rule bot would do: rule)");
        BotBrain.InputDecision decision = think(source);
        assertThat(decision.target()).isEqualTo(CountingRule.DECISION.target());
        assertThat(decision.ruleId()).isEqualTo("llm.jev");
        assertThat(decision.reason()).startsWith("STANDARD TACTIC");
    }

    @Test
    void aThrowingRuleBrainOnlyCostsTheStandardPlay() {
        BotDecisionSource throwing = (botEye, parameters, botSide, overdue) -> {
            throw new IllegalStateException("boom");
        };
        LlmDecisionSource source = new LlmDecisionSource(throwing, client(), fakeHttp, () -> "sk-test", health,
                executor, LlmTestFixtures.builder(), "jev-latest", MIN_INTERVAL);

        assertThat(think(source)).isNull();
        assertThat(executor.pending()).isEqualTo(1);
    }

    @Test
    void aSinglePlayWithoutWaitIsCastWithoutAsking() {
        BotEye lone = new BotEye(java.util.List.of(
                new com.wordonline.server.game.domain.bot.BotVisibleObject(11, Master.RightPlayer,
                        com.wordonline.server.game.domain.object.prefab.PrefabType.FireSlime,
                        new Vector3(4, 0, 5), 25, true, true)),
                java.util.List.of(LlmTestFixtures.FIRE_SHOT_ID), 5, 88);
        LlmDecisionSource source = new LlmDecisionSource((botEye, parameters, botSide, overdue) -> null, client(),
                fakeHttp, () -> "sk-test", health, executor, LlmTestFixtures.builder(), "jev-latest", MIN_INTERVAL);

        BotBrain.InputDecision decision = source.think(lone, parameters, Master.LeftPlayer, false);

        assertThat(decision.target()).isEqualTo(new Vector3(4, 0, 5));
        assertThat(decision.reason()).startsWith("Only play: fire_shot");
        assertThat(executor.pending()).isZero();
        assertThat(httpCalls).hasValue(0);
    }

    @Test
    void logsTheBodyOnTheFirstRequestAndEveryTwentiethAfterIt() {
        assertThat(LlmDecisionSource.shouldLogBody(1)).isTrue();
        assertThat(LlmDecisionSource.shouldLogBody(2)).isFalse();
        assertThat(LlmDecisionSource.shouldLogBody(20)).isFalse();
        assertThat(LlmDecisionSource.shouldLogBody(21)).isTrue();
    }

    private BotBrain.InputDecision think(LlmDecisionSource source) {
        return source.think(eye, parameters, Master.LeftPlayer, false);
    }

    private LlmDecisionSource source(LlmHttpClient http, java.util.concurrent.Executor executor) {
        return new LlmDecisionSource(rule, client(), http, () -> "sk-test", health, executor,
                LlmTestFixtures.builder(), "jev-latest", MIN_INTERVAL);
    }

    private static JevProviderClient client() {
        return new JevProviderClient(LlmBotProperties.DEFAULT_JEV_URL, LlmBotProperties.DEFAULT_JEV_MODEL);
    }

    private static final class CountingRule implements BotDecisionSource {
        // Lands next to the best blast at (6, 6), so it takes that play's place as p0.
        static final BotBrain.InputDecision DECISION =
                new BotBrain.InputDecision(LlmTestFixtures.FIRE_SHOT_ID, new Vector3(6, 0, 6.5f), "rule.test", "rule");
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public BotBrain.InputDecision think(BotEye botEye, Parameters parameters, Master botSide, boolean overdue) {
            calls.incrementAndGet();
            return DECISION;
        }
    }

    private static final class ManualExecutor implements java.util.concurrent.Executor {
        private final Deque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        int pending() {
            return queue.size();
        }

        void runAll() {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }
}
