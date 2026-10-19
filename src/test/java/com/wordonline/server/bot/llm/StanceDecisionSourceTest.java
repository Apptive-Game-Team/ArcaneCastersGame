package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.wordonline.server.bot.domain.BotBrainType;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.bot.BotDecisionSource;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

class StanceDecisionSourceTest {

    private static final BotBrain.InputDecision RULE =
            new BotBrain.InputDecision(LlmTestFixtures.FIRE_SHOT_ID, new Vector3(6, 0, 6.5f), "rule.test", "rule");
    private static final BotDecisionSource RULE_SOURCE = (eye, parameters, side, overdue) -> RULE;

    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final LlmProviderHealth health = new LlmProviderHealth(BotBrainType.JEV, clock::get);
    private final ManualExecutor executor = new ManualExecutor();
    private final JevProviderClient client = new JevProviderClient("https://jev.test/v1/systemone", "jev-latest");
    private String answer = stanceAnswer("attack");

    @Test
    void startsOnDefendWhichIsTheRulePlay() {
        StanceDecisionSource source = source();

        BotBrain.InputDecision decision = think(source, LlmTestFixtures.quietEye(
                List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.SLIME_SUMMON_ID), 5));

        assertThat(source.stance()).isEqualTo(StanceDecisionSource.Stance.DEFEND);
        assertThat(decision).isSameAs(RULE);
    }

    @Test
    void castsUnderTheCurrentStanceWhileTheRequestIsInFlight() {
        StanceDecisionSource source = source();
        BotEye quiet = LlmTestFixtures.quietEye(List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.SLIME_SUMMON_ID), 5);

        assertThat(think(source, quiet)).as("acts at once, not after the answer").isSameAs(RULE);
        assertThat(executor.pending()).isEqualTo(1);
        assertThat(think(source, quiet)).isSameAs(RULE);
        assertThat(executor.pending()).as("no second request while one is in flight").isEqualTo(1);

        executor.runAll();

        assertThat(source.stance()).isEqualTo(StanceDecisionSource.Stance.ATTACK);
    }

    @Test
    void attackHitsTheEnemyBaseWhenASpellReachesIt() {
        Parameters parameters = LlmTestFixtures.parameters();
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("range"), anyDouble())).thenReturn(20.0);
        BotEye quiet = LlmTestFixtures.quietEye(List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.SLIME_SUMMON_ID), 5);

        BotBrain.InputDecision decision = turn(quiet, parameters).play(StanceDecisionSource.Stance.ATTACK);

        assertThat(decision.magicId()).isEqualTo(LlmTestFixtures.FIRE_SHOT_ID);
        assertThat(decision.target()).isEqualTo(new Vector3(17, 0, 5));
        assertThat(decision.ruleId()).isEqualTo("llm.jev.attack");
    }

    @Test
    void attackSummonsToPushWhenNothingReachesTheBase() {
        BotEye quiet = LlmTestFixtures.quietEye(List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.SLIME_SUMMON_ID), 5);

        BotBrain.InputDecision decision = turn(quiet, LlmTestFixtures.parameters())
                .play(StanceDecisionSource.Stance.ATTACK);

        assertThat(decision.magicId()).isEqualTo(LlmTestFixtures.SLIME_SUMMON_ID);
    }

    @Test
    void saveHoldsUntilTheDearestCardIsAffordable() {
        List<Long> cards = List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.ROCK_GOLEM_ID);

        assertThat(turn(LlmTestFixtures.quietEye(cards, 5), LlmTestFixtures.parameters())
                .play(StanceDecisionSource.Stance.SAVE)).isNull();
        assertThat(turn(LlmTestFixtures.quietEye(cards, 9), LlmTestFixtures.parameters())
                .play(StanceDecisionSource.Stance.SAVE).magicId()).isEqualTo(LlmTestFixtures.ROCK_GOLEM_ID);
    }

    @Test
    void anEnemyCloseToTheBaseOverridesAttackAndSave() {
        StanceDecisionSource.Turn turn = turn(LlmTestFixtures.eye(), LlmTestFixtures.parameters());

        assertThat(turn.play(StanceDecisionSource.Stance.ATTACK)).isSameAs(RULE);
        assertThat(turn.play(StanceDecisionSource.Stance.SAVE)).isSameAs(RULE);
    }

    @Test
    void anUnknownAnswerKeepsTheStance() {
        answer = stanceAnswer("p0");
        StanceDecisionSource source = source();

        think(source, LlmTestFixtures.eye());
        executor.runAll();

        assertThat(source.stance()).isEqualTo(StanceDecisionSource.Stance.DEFEND);
    }

    @Test
    void criteriaOfferEveryStance() {
        StanceDecisionSource.Turn turn = turn(LlmTestFixtures.quietEye(
                List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.ROCK_GOLEM_ID), 5), LlmTestFixtures.parameters());

        assertThat(turn.criteria()).containsOnlyKeys("attack", "defend", "save");
        assertThat(turn.criteria().get("save")).contains("cost 8").contains("mana is 5");
    }

    private StanceDecisionSource source() {
        LlmHttpClient http = (uri, headers, body) -> new LlmHttpClient.Response(200, answer, null);
        return new StanceDecisionSource(RULE_SOURCE, client, http, () -> "key", health, executor,
                LlmTestFixtures.builder(), "jev-latest", 500);
    }

    private static StanceDecisionSource.Turn turn(BotEye eye, Parameters parameters) {
        return new StanceDecisionSource.Turn(eye,
                LlmTestFixtures.builder().build(eye, parameters, Master.LeftPlayer), RULE);
    }

    private static BotBrain.InputDecision think(StanceDecisionSource source, BotEye eye) {
        return source.think(eye, LlmTestFixtures.parameters(), Master.LeftPlayer, false);
    }

    private static String stanceAnswer(String key) {
        return "{\"answers\":{\"stance\":{\"type\":\"choice\",\"choice\":\"" + key + "\",\"confidence\":0.8}}}";
    }

    private static final class ManualExecutor implements Executor {
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
