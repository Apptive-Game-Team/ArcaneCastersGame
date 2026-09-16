package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.implement.explode.WaterExplosionMagic;
import com.wordonline.server.game.domain.magic.implement.explode.WindExplosionMagic;
import com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotBrainComboTest {

    @Test
    void seedSpiritComboPrecedesTheExistingValueDecision() {
        Magic vineToss = withId(new VineTossMagic(), 1, "vine_toss");
        Magic waterExplosion = withId(new WaterExplosionMagic(), 2, "water_explosion");
        BotBrain brain = brain(BotTier.ELITE, vineToss, waterExplosion);
        Vector3 seedPosition = new Vector3(4, 0, 5);
        BotEye eye = new BotEye(
                List.of(
                        visible(1, Master.LeftPlayer, PrefabType.SeedSpirit, seedPosition, true),
                        visible(2, Master.RightPlayer, PrefabType.WaterSlime, new Vector3(6, 0, 4), true),
                        visible(3, Master.RightPlayer, PrefabType.WaterSlime, new Vector3(6, 0, 5), true),
                        visible(4, Master.RightPlayer, PrefabType.WaterSlime, new Vector3(6, 0, 6), true)),
                List.of(vineToss.id, waterExplosion.id), 20, 100);

        BotBrain.InputDecision decision = brain.think(eye, parameters(), Master.LeftPlayer, new Random(1));

        assertThat(decision.ruleId()).isEqualTo(BotBrain.SEED_SPIRIT_RULE);
        assertThat(decision.magicId()).isEqualTo(vineToss.id);
        assertThat(decision.target()).isEqualTo(seedPosition);
    }

    @Test
    void explosionComboTargetsTheAverageOfThreeNearbyEnemyMobs() {
        Magic waterExplosion = withId(new WaterExplosionMagic(), 2, "water_explosion");
        BotBrain brain = brain(BotTier.ELITE, waterExplosion);
        BotEye eye = new BotEye(
                List.of(
                        visible(2, Master.RightPlayer, PrefabType.WaterSlime, new Vector3(5, 0, 4), true),
                        visible(3, Master.RightPlayer, PrefabType.WaterSlime, new Vector3(5, 0, 5), true),
                        visible(4, Master.RightPlayer, PrefabType.WaterSlime, new Vector3(5, 0, 6), true)),
                List.of(waterExplosion.id), 20, 100);

        BotBrain.InputDecision decision = brain.think(eye, parameters(), Master.LeftPlayer, new Random(1));

        assertThat(decision.ruleId()).isEqualTo(BotBrain.MOB_CLUSTER_RULE);
        assertThat(decision.target()).isEqualTo(new Vector3(5, 0, 5));
        assertThat(decision.reason()).contains("3 enemy mobs");
    }

    @Test
    void appliesTierNoiseExactlyOncePerScoredCandidate() {
        Magic waterExplosion = withId(new WaterExplosionMagic(), 2, "water_explosion");
        Magic windExplosion = withId(new WindExplosionMagic(), 3, "wind_explosion");
        BotBrain brain = brain(BotTier.BEGINNER, waterExplosion, windExplosion);
        BotEye eye = new BotEye(
                List.of(visible(2, Master.RightPlayer, PrefabType.WaterSlime, new Vector3(5, 0, 5), true)),
                List.of(waterExplosion.id, windExplosion.id), 20, 100);
        CountingRandom random = new CountingRandom();

        BotBrain.InputDecision decision = brain.think(eye, parameters(), Master.LeftPlayer, random);

        assertThat(decision).isNotNull();
        assertThat(random.nextDoubleCalls).isEqualTo(2);
    }

    private static Magic withId(Magic magic, long id, String name) {
        magic.id = id;
        magic.name = name;
        return magic;
    }

    private static BotBrain brain(BotTier tier, Magic... hand) {
        DatabaseMagicParser parser = mock(DatabaseMagicParser.class);
        for (Magic magic : hand) {
            when(parser.getMagic(magic.id)).thenReturn(magic);
        }
        when(parser.getAllMagics()).thenReturn(List.of(hand));
        BotCounterEvaluator counterEvaluator = mock(BotCounterEvaluator.class);
        when(counterEvaluator.evaluate(any(), any())).thenReturn(0.0);
        BotPersona persona = new BotPersona(1, "test", tier, 0, 1, 0, true, false);
        return new BotBrain(parser, counterEvaluator, persona);
    }

    private static Parameters parameters() {
        Parameters parameters = mock(Parameters.class);
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1);
            return switch (key) {
                case "mana_cost" -> 1.0;
                case "range" -> 20.0;
                case "radius" -> 1.0;
                case "damage" -> 10.0;
                default -> invocation.getArgument(2);
            };
        });
        return parameters;
    }

    private static BotVisibleObject visible(int id,
                                            Master master,
                                            PrefabType type,
                                            Vector3 position,
                                            boolean mob) {
        return new BotVisibleObject(id, master, type, position, 10, mob, true);
    }

    private static final class CountingRandom extends Random {
        private int nextDoubleCalls;

        @Override
        public double nextDouble() {
            nextDoubleCalls++;
            return 0.5;
        }
    }
}
