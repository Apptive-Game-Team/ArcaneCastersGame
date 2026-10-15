package com.wordonline.server.bot.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.bot.BotVisibleObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

class LlmCandidateBuilderTest {

    @Test
    void offersOnlyAffordableMagicsAndWait() {
        LlmCandidates candidates = LlmTestFixtures.candidates();

        assertThat(candidates.magics()).containsOnlyKeys("m3");
        assertThat(candidates.magics().get("m3").description())
                .isEqualTo("fire_shot, 3 mana: deals 20 damage in radius 1.5, max cast range 10");
        assertThat(candidates.magicCriteria()).containsOnlyKeys("m3", "wait");
    }

    @Test
    void listsEnemyUnitsNearestTheOwnBaseFirstThenTheEnemyBaseThenDefensiveSpots() {
        LlmCandidates candidates = LlmTestFixtures.candidates();

        // The enemy player body and the in-flight projectile are not unit targets.
        assertThat(candidates.targetCriteria()).containsExactly(
                java.util.Map.entry("t0", "enemy FireSlime hp 25 at (6, 6)"),
                java.util.Map.entry("t1", "enemy FireSlime hp 30 at (12, 4)"),
                java.util.Map.entry("t2", "enemy base (player hp 88) at (17, 5)"),
                java.util.Map.entry("t3", "defensive spot 3 in front of own base at (4, 5)"),
                java.util.Map.entry("t4", "defensive spot 6 in front of own base at (7, 5)"));
        assertThat(candidates.targets().get("t0").position()).isEqualTo(new Vector3(6, 0, 6));
    }

    @Test
    void defensiveSpotsFaceLeftForTheRightSide() {
        LlmCandidates candidates = new LlmCandidateBuilder(LlmTestFixtures.lookup())
                .build(LlmTestFixtures.eye(), LlmTestFixtures.parameters(), Master.RightPlayer);

        assertThat(candidates.targetCriteria().values())
                .contains("defensive spot 3 in front of own base at (14, 5)", "enemy base (player hp 88) at (1, 5)");
        assertThat(candidates.state()).containsEntry("side", "right");
    }

    @Test
    void capsTargetsAtTwelve() {
        List<BotVisibleObject> objects = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            objects.add(new BotVisibleObject(i, Master.RightPlayer, PrefabType.FireSlime,
                    new Vector3(5 + i * 0.5f, 0, 5), 10, true, true));
        }
        BotEye eye = new BotEye(objects, List.of(LlmTestFixtures.FIRE_SHOT_ID), 5, 100);

        LlmCandidates candidates = new LlmCandidateBuilder(LlmTestFixtures.lookup())
                .build(eye, LlmTestFixtures.parameters(), Master.LeftPlayer);

        assertThat(candidates.targets()).hasSize(LlmCandidateBuilder.MAX_TARGETS);
        assertThat(candidates.targets().get("t9").description()).startsWith("enemy base");
        assertThat(candidates.targets()).containsKey("t11");
    }

    @Test
    void nothingAffordableMeansNothingToAsk() {
        BotEye poor = new BotEye(List.of(), List.of(LlmTestFixtures.FIRE_SHOT_ID, LlmTestFixtures.ROCK_GOLEM_ID), 2, 100);

        LlmCandidates candidates = new LlmCandidateBuilder(LlmTestFixtures.lookup())
                .build(poor, LlmTestFixtures.parameters(), Master.LeftPlayer);

        assertThat(candidates.hasCastableMagic()).isFalse();
    }
}
