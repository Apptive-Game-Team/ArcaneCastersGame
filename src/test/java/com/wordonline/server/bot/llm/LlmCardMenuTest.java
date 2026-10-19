package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

class LlmCardMenuTest {

    private final Parameters parameters = LlmTestFixtures.parameters();
    private final LlmCandidateBuilder builder = new LlmCandidateBuilder(LlmTestFixtures.lookup(),
            LlmTestFixtures.neutralCounters(), () -> new Random(7), true,
            new MagicDescriptions(Map.of("fire_shot", "Fires a test bolt.")));

    @Test
    void offersOneOptionPerAffordableCardKeyedByItsNameAndWaiting() {
        LlmCandidates cards = builder.build(
                LlmTestFixtures.eye(List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.SLIME_SUMMON_ID,
                        LlmTestFixtures.ROCK_GOLEM_ID), 5),
                parameters, Master.LeftPlayer);

        assertThat(cards.criteria()).containsOnlyKeys("fire_shot", "wind_slime_summon", "wait");
    }

    @Test
    void aSpellSaysWhatTheCardDoesAndWhatItWouldHitNow() {
        String fireShot = builder.build(LlmTestFixtures.eye(), parameters, Master.LeftPlayer).describe("fire_shot");

        assertThat(fireShot).startsWith("fire_shot (spell, 3 mana, range 10, radius 1.5, 20 damage to each target in the radius): Fires a test bolt.")
                .contains("Right now its best target hits");
    }

    @Test
    void aUnitThatNeverMovesSaysSoAndCountsTheEnemiesItWouldReach() {
        String slime = UnitGuide.objectName(PrefabType.WindSlime);
        when(parameters.getValueOrDefault(eq(slime), eq("hp"), anyDouble())).thenReturn(100.0);
        when(parameters.getValueOrDefault(eq(slime), eq("push_force"), anyDouble())).thenReturn(3.0);
        when(parameters.getValueOrDefault(eq(slime), eq("push_range_x"), anyDouble())).thenReturn(6.0);

        String summon = builder.build(
                        LlmTestFixtures.eye(List.of(LlmTestFixtures.SLIME_SUMMON_ID), 5), parameters, Master.LeftPlayer)
                .describe("wind_slime_summon");

        assertThat(summon).contains("stays where it is placed and never moves", "blows enemy units back",
                "within its reach of 6 now");
    }

    @Test
    void aSpellWithNothingToHitIsLeftOut() {
        LlmCandidates cards = builder.build(
                LlmTestFixtures.quietEye(List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.SLIME_SUMMON_ID), 5),
                parameters, Master.LeftPlayer);

        assertThat(cards.criteria()).containsOnlyKeys("wind_slime_summon", "wait");
    }

    @Test
    void theStateCountsCopiesAndHandSlotsAndWaitingSaysAFullHandDrawsNothing() {
        List<Long> fullHand = List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.FIRE_SHOT_ID,
                LlmTestFixtures.ROCK_GOLEM_ID, LlmTestFixtures.ROCK_GOLEM_ID, LlmTestFixtures.ROCK_GOLEM_ID,
                LlmTestFixtures.SLIME_SUMMON_ID);

        LlmCandidates cards = builder.build(LlmTestFixtures.eye(fullHand, 5), parameters, Master.LeftPlayer);

        assertThat(cards.state().get("handSlotsUsed")).isEqualTo("6 of 6");
        assertThat(cards.state().get("hand").toString()).contains("magic=fire_shot, cost=3, copies=2")
                .contains("magic=rock_golem_summon, cost=8, copies=3");
        assertThat(cards.waitDescription()).contains("rock_golem_summon (8 mana)")
                .contains("The hand is full, so no new card is drawn");
    }

    @Test
    void noOptionIsMarkedAsTheRuleBotsPlay() {
        BotBrain.InputDecision rule =
                new BotBrain.InputDecision(LlmTestFixtures.FIRE_SHOT_ID, new Vector3(6, 0, 6.5f), "rule.test", "rule");

        LlmCandidates cards = builder.build(LlmTestFixtures.eye(), parameters, Master.LeftPlayer, rule);

        assertThat(cards.criteria().values()).noneMatch(text -> text.contains(LlmCandidateBuilder.STANDARD_TACTIC));
    }

    @Test
    void theBundledCardTextCoversTheMagicBook() {
        assertThat(MagicDescriptions.bundled().describe("wind_totem"))
                .startsWith("Places a wind totem that repeatedly pushes enemy ground units");
        assertThat(MagicDescriptions.bundled().describe("no_such_magic")).isNull();
    }

    @Test
    void aSpellIsAimedByCodeAndAsksNoAimQuestion() {
        LlmCandidates.Play fireShot = builder.build(LlmTestFixtures.eye(), parameters, Master.LeftPlayer)
                .plays().get("fire_shot");

        assertThat(fireShot.aims()).isEmpty();
    }

    @Test
    void aSummonOffersLanesAtTwoDepthsAndBlocking() {
        LlmCandidates.Play slime = builder.build(
                        LlmTestFixtures.quietEye(List.of(LlmTestFixtures.SLIME_SUMMON_ID), 5), parameters, Master.LeftPlayer)
                .plays().get("wind_slime_summon");

        assertThat(slime.aims()).containsKeys("block", "near_low", "near_high");
        assertThat(slime.aims().get("near_low").target().getZ()).isEqualTo(2f);
        assertThat(slime.aims().get("near_low").description()).startsWith("Near our base in the low lane (z=2)");
        assertThat(slime.aims().values()).allMatch(aim -> aim.target().distance(new Vector3(1, 0, 5)) <= 6.01);
    }

    @Test
    void theChosenAimNamesThePointTheSummonIsPut() {
        LlmCandidates cards = builder.build(
                LlmTestFixtures.quietEye(List.of(LlmTestFixtures.SLIME_SUMMON_ID), 5), parameters, Master.LeftPlayer);
        LlmCandidates.Play slime = cards.plays().get("wind_slime_summon");

        BotBrain.InputDecision decision =
                new LlmAnswer("wind_slime_summon", null, 0.9, "near_high").toDecision(cards, "llm.jev");

        assertThat(decision.target()).isEqualTo(slime.aims().get("near_high").target());
        assertThat(new LlmAnswer("wind_slime_summon", null, 0.9, "nowhere").toDecision(cards, "llm.jev").target())
                .as("an unknown aim falls back to the card's own point")
                .isEqualTo(slime.target());
    }
}
