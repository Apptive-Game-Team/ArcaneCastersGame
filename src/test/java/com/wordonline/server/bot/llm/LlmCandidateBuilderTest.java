package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.bot.BotVisibleObject;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;

class LlmCandidateBuilderTest {

    private static final List<Long> FULL_HAND = List.of(
            LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.ROCK_GOLEM_ID, LlmTestFixtures.SLIME_SUMMON_ID);

    @Test
    void offersCompletePlaysOfAffordableMagicsOnly() {
        LlmCandidates candidates = LlmTestFixtures.candidates();

        // The golem costs 8 with 5 mana. fire_shot gets its best blast and the most advanced slime.
        assertThat(candidates.plays()).containsOnlyKeys("p0", "p1");
        LlmCandidates.Play best = candidates.plays().get("p0");
        assertThat(best.magicId()).isEqualTo(LlmTestFixtures.FIRE_SHOT_ID);
        assertThat(best.manaCost()).isEqualTo(3);
        assertThat(best.target()).isEqualTo(new Vector3(6, 0, 6));
        assertThat(best.description()).isEqualTo("fire_shot (damage spell, cost 3, 20 damage in radius 1.5) at "
                + "(6, 6), 5.1 from our base: hits 2 enemy units (FireSlime, FireSlime) for 35 total damage "
                + "(11.7 per mana), kills 1. Neutral against the enemy units on the field (counter score 0).");
        LlmCandidates.Play advanced = candidates.plays().get("p1");
        assertThat(advanced.target()).isEqualTo(new Vector3(3.5f, 0, 4));
        assertThat(advanced.description()).contains("at (3.5, 4), 2.7 from our base: hits 1 enemy unit (FireSlime)",
                "kills 1");
    }

    @Test
    void summonPlaysSayWhatTheyPutDownAndWhatTheyBlock() {
        LlmCandidates candidates = LlmTestFixtures.builder()
                .build(LlmTestFixtures.eye(FULL_HAND, 9), LlmTestFixtures.parameters(), Master.LeftPlayer);

        LlmCandidates.Play slimes = play(candidates, LlmTestFixtures.SLIME_SUMMON_ID);
        assertThat(slimes.description()).startsWith("wind_slime_summon (summon, cost 4) at (2.6, 5), 1.6 from our base: "
                        + "puts 2 WindSlime (flying) in lane z=5; blocks the nearest enemy FireSlime (2.7 from our base); "
                        + "pressure on our base is medium (0.69).");
        assertThat(play(candidates, LlmTestFixtures.ROCK_GOLEM_ID).description()).contains("puts 1 RockGolem in lane");
    }

    @Test
    void saysPlainlyHowEachMagicMatchesTheEnemyUnits() {
        BotCounterEvaluator counters = mock(BotCounterEvaluator.class);
        when(counters.evaluate(argThat(magic -> magic.id == LlmTestFixtures.FIRE_SHOT_ID), anyList()))
                .thenReturn(3.0);
        when(counters.evaluateVulnerability(argThat(magic -> magic.id == LlmTestFixtures.SLIME_SUMMON_ID), anyList()))
                .thenReturn(1.5);

        LlmCandidates candidates = LlmTestFixtures.builder(counters)
                .build(LlmTestFixtures.eye(FULL_HAND, 9), LlmTestFixtures.parameters(), Master.LeftPlayer);

        assertThat(play(candidates, LlmTestFixtures.FIRE_SHOT_ID).description())
                .endsWith("Strong against the enemy units on the field (counter score +3).");
        assertThat(play(candidates, LlmTestFixtures.SLIME_SUMMON_ID).description())
                .endsWith("Weak against the enemy units on the field (counter score -1.5).");
        assertThat(play(candidates, LlmTestFixtures.ROCK_GOLEM_ID).description())
                .endsWith("Neutral against the enemy units on the field (counter score 0).");
    }

    @Test
    void counterIsScoredOnEnemyUnitsOnly() {
        BotCounterEvaluator counters = mock(BotCounterEvaluator.class);
        List<List<BotVisibleObject>> seen = new ArrayList<>();
        when(counters.evaluate(any(), anyList())).thenAnswer(call -> {
            seen.add(call.getArgument(1));
            return 0.0;
        });

        LlmTestFixtures.builder(counters).build(LlmTestFixtures.eye(), LlmTestFixtures.parameters(), Master.LeftPlayer);

        assertThat(seen).isNotEmpty();
        assertThat(seen.getFirst()).extracting(BotVisibleObject::type).containsOnly(PrefabType.FireSlime);
        assertThat(seen.getFirst()).allMatch(object -> object.master() == Master.RightPlayer);
    }

    @Test
    void offersWaitOnlyWhenNothingIsUrgentAndADearerMagicIsInHand() {
        LlmCandidates saving = LlmTestFixtures.builder()
                .build(LlmTestFixtures.quietEye(FULL_HAND, 5), LlmTestFixtures.parameters(), Master.LeftPlayer);
        assertThat(saving.offersWait()).isTrue();
        assertThat(saving.criteria()).containsEntry(LlmCandidates.WAIT, "Cast nothing now and save mana for "
                + "rock_golem_summon (cost 8), mana is 5 now; nothing is urgent: the nearest enemy unit is 13 from our base.");
        assertThat(saving.criteria().keySet()).last().isEqualTo(LlmCandidates.WAIT);

        LlmCandidates rich = LlmTestFixtures.builder()
                .build(LlmTestFixtures.quietEye(FULL_HAND, 9), LlmTestFixtures.parameters(), Master.LeftPlayer);
        assertThat(rich.offersWait()).as("everything is affordable already").isFalse();

        LlmCandidates pushed = LlmTestFixtures.builder()
                .build(LlmTestFixtures.eye(FULL_HAND, 5), LlmTestFixtures.parameters(), Master.LeftPlayer);
        assertThat(pushed.offersWait()).as("enemies are close to our base").isFalse();
        assertThat(pushed.criteria()).doesNotContainKey(LlmCandidates.WAIT);
    }

    @Test
    void theRuleBrainsPlayComesFirstMarkedAsTheStandardTactic() {
        BotBrain.InputDecision rule = new BotBrain.InputDecision(LlmTestFixtures.FIRE_SHOT_ID,
                new Vector3(5, 0, 5), "combo.mob-cluster", "AbstractExplosionMagic targets a cluster of 3 enemy mobs.");

        LlmCandidates candidates = LlmTestFixtures.builder()
                .build(LlmTestFixtures.eye(), LlmTestFixtures.parameters(), Master.LeftPlayer, rule);

        assertThat(candidates.plays()).hasSize(3);
        LlmCandidates.Play standard = candidates.plays().get("p0");
        assertThat(standard.target()).isEqualTo(new Vector3(5, 0, 5));
        assertThat(standard.description()).startsWith("STANDARD TACTIC (what the rule bot would do: "
                + "AbstractExplosionMagic targets a cluster of 3 enemy mobs): fire_shot (damage spell, cost 3, "
                + "20 damage in radius 1.5) at (5, 5), 4 from our base: hits 1 enemy unit (FireSlime)");
    }

    @Test
    void theRuleBrainsPlayReplacesTheSamePlayInsteadOfRepeatingIt() {
        BotBrain.InputDecision rule = new BotBrain.InputDecision(LlmTestFixtures.FIRE_SHOT_ID,
                new Vector3(6, 0, 6.5f), "score.best-value", "Best offensive value covers 2 target(s).");

        LlmCandidates candidates = LlmTestFixtures.builder()
                .build(LlmTestFixtures.eye(), LlmTestFixtures.parameters(), Master.LeftPlayer, rule);

        assertThat(candidates.plays()).containsOnlyKeys("p0", "p1");
        assertThat(candidates.plays().get("p0").description()).startsWith("STANDARD TACTIC");
        assertThat(candidates.plays().get("p0").target()).isEqualTo(new Vector3(6, 0, 6.5f));
        assertThat(candidates.plays().get("p1").target()).isEqualTo(new Vector3(3.5f, 0, 4));
    }

    @Test
    void anUnknownOrUnaffordableRuleMagicIsLeftOut() {
        BotBrain.InputDecision unknown = new BotBrain.InputDecision(999, new Vector3(5, 0, 5), "x", "y");
        BotBrain.InputDecision golem = new BotBrain.InputDecision(
                LlmTestFixtures.ROCK_GOLEM_ID, new Vector3(5, 0, 5), "x", "y");

        for (BotBrain.InputDecision rule : List.of(unknown, golem)) {
            LlmCandidates candidates = LlmTestFixtures.builder()
                    .build(LlmTestFixtures.eye(), LlmTestFixtures.parameters(), Master.LeftPlayer, rule);
            assertThat(candidates.criteria().values()).noneMatch(text -> text.startsWith("STANDARD TACTIC"));
        }
    }

    @Test
    void anEnemyBaseInRangeIsAimedAtAndTheMostAdvancedUnitStaysAnOption() {
        BotEye eye = new BotEye(List.of(
                new BotVisibleObject(1, Master.LeftPlayer, PrefabType.Player, new Vector3(1, 0, 5), 60, true, true),
                new BotVisibleObject(2, Master.RightPlayer, PrefabType.Player, new Vector3(17, 0, 5), 88, true, true),
                new BotVisibleObject(11, Master.LeftPlayer, PrefabType.FireSlime, new Vector3(12, 0, 5), 25, true, true)),
                List.of(LlmTestFixtures.FIRE_SHOT_ID), 5, 60);
        Parameters parameters = LlmTestFixtures.parameters();
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("range"), anyDouble())).thenReturn(17.0);

        LlmCandidates candidates = LlmTestFixtures.builder().build(eye, parameters, Master.RightPlayer);

        assertThat(candidates.plays()).hasSize(2);
        assertThat(candidates.plays().get("p0").description())
                .contains("at (1, 5), 16 from our base: hits the enemy base for 20 total damage");
        assertThat(candidates.plays().get("p1").description())
                .contains("at (12, 5), 5 from our base: hits 1 enemy unit (FireSlime) for 20 total damage (6.7 per mana), kills 0");
        assertThat(candidates.state()).containsEntry("side", "right").containsEntry("ourHp", 88)
                .containsEntry("enemyHp", 60);
    }

    @Test
    void capsThePlays() {
        Map<Long, Magic> magics = new HashMap<>();
        List<Long> hand = new ArrayList<>();
        for (long id = 100; id < 120; id++) {
            magics.put(id, LlmTestFixtures.magic(id, "bolt_" + id));
            hand.add(id);
        }
        Parameters parameters = mock(Parameters.class);
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble())).thenReturn(0.0);
        when(parameters.getValueOrDefault(startsWith("bolt_"), eq("mana_cost"), anyDouble())).thenReturn(1.0);
        when(parameters.getValueOrDefault(startsWith("bolt_"), eq("range"), anyDouble())).thenReturn(10.0);
        when(parameters.getValueOrDefault(startsWith("bolt_"), eq("radius"), anyDouble())).thenReturn(1.0);
        BotEye eye = LlmTestFixtures.eye(hand, 20);

        LlmCandidates candidates = new LlmCandidateBuilder(magics::get, null, () -> new Random(1))
                .build(eye, parameters, Master.LeftPlayer);

        assertThat(candidates.plays()).hasSize(LlmCandidateBuilder.MAX_PLAYS);
        assertThat(candidates.plays()).containsKey("p15");
    }

    @Test
    void theStateIsCompactAndCarriesBothHpAndThePressure() {
        Map<String, Object> state = LlmTestFixtures.candidates().state();

        assertThat(state).containsOnlyKeys("side", "ourHp", "enemyHp", "mana", "pressure", "enemyNearOurBase",
                "hand", "enemyUnits", "ourUnits");
        assertThat(state).containsEntry("ourHp", 90).containsEntry("enemyHp", 88).containsEntry("mana", 5)
                .containsEntry("pressure", 0.69).containsEntry("enemyNearOurBase", true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enemies = (List<Map<String, Object>>) state.get("enemyUnits");
        // The projectile and the enemy player body are not units.
        assertThat(enemies).hasSize(4);
        assertThat(enemies.getFirst()).containsExactly(
                Map.entry("type", "FireSlime"), Map.entry("hp", 20), Map.entry("toOurBase", 2.7), Map.entry("z", 4.0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ours = (List<Map<String, Object>>) state.get("ourUnits");
        assertThat(ours.getFirst()).containsEntry("toEnemyBase", 13.0);
    }

    @Test
    void nothingAffordableMeansNothingToAsk() {
        BotEye poor = LlmTestFixtures.eye(FULL_HAND, 2);

        LlmCandidates candidates = LlmTestFixtures.builder()
                .build(poor, LlmTestFixtures.parameters(), Master.LeftPlayer);

        assertThat(candidates.hasPlays()).isFalse();
    }

    private static LlmCandidates.Play play(LlmCandidates candidates, long magicId) {
        return candidates.plays().values().stream()
                .filter(play -> play.magicId() == magicId)
                .findFirst()
                .orElseThrow();
    }
}
