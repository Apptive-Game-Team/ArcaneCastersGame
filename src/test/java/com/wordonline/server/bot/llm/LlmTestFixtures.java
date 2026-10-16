package com.wordonline.server.bot.llm;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.LongFunction;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.bot.BotVisibleObject;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;

/** A small hand and a board shared by the LLM brain tests. */
final class LlmTestFixtures {

    static final long FIRE_SHOT_ID = 3;
    static final long ROCK_GOLEM_ID = 7;
    static final long SLIME_SUMMON_ID = 9;

    private LlmTestFixtures() {
    }

    static Magic magic(long id, String name) {
        Magic magic = new Magic() {
            @Override
            public void run(GameContext gameContext, Master master, Vector3 position) {
            }
        };
        magic.id = id;
        magic.name = name;
        return magic;
    }

    static Magic summon(long id, String name, PrefabType prefab, int quantity, boolean airborne) {
        Magic magic = new SummonMagic(prefab, quantity, airborne);
        magic.id = id;
        magic.name = name;
        return magic;
    }

    static LongFunction<Magic> lookup() {
        Map<Long, Magic> magics = Map.of(
                FIRE_SHOT_ID, magic(FIRE_SHOT_ID, "fire_shot"),
                ROCK_GOLEM_ID, summon(ROCK_GOLEM_ID, "rock_golem_summon", PrefabType.RockGolem, 1, false),
                SLIME_SUMMON_ID, summon(SLIME_SUMMON_ID, "wind_slime_summon", PrefabType.WindSlime, 2, true));
        return magics::get;
    }

    /**
     * fire_shot costs 3 and deals 20 in radius 1.5 within range 10; rock_golem_summon costs 8 and
     * wind_slime_summon 4, both within range 6.
     */
    static Parameters parameters() {
        Parameters parameters = mock(Parameters.class);
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble())).thenReturn(0.0);
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("mana_cost"), anyDouble())).thenReturn(3.0);
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("damage"), anyDouble())).thenReturn(20.0);
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("radius"), anyDouble())).thenReturn(1.5);
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("range"), anyDouble())).thenReturn(10.0);
        when(parameters.getValueOrDefault(eq("rock_golem_summon"), eq("mana_cost"), anyDouble())).thenReturn(8.0);
        when(parameters.getValueOrDefault(eq("rock_golem_summon"), eq("range"), anyDouble())).thenReturn(6.0);
        when(parameters.getValueOrDefault(eq("wind_slime_summon"), eq("mana_cost"), anyDouble())).thenReturn(4.0);
        when(parameters.getValueOrDefault(eq("wind_slime_summon"), eq("range"), anyDouble())).thenReturn(6.0);
        return parameters;
    }

    /**
     * The bot plays the left side with 5 mana: fire_shot is affordable, the golem is not. Three
     * enemy slimes and an enemy projectile push toward the bot's base at (1, 5); the one at
     * (3.5, 4) is ahead of the other two, which stand together around (5, 5) with the projectile.
     */
    static BotEye eye() {
        return eye(List.of(FIRE_SHOT_ID, ROCK_GOLEM_ID), 5);
    }

    static BotEye eye(List<Long> cards, int mana) {
        return new BotEye(
                List.of(
                        new BotVisibleObject(1, Master.RightPlayer, PrefabType.Player, new Vector3(17, 0, 5), 88, true, true),
                        new BotVisibleObject(2, Master.LeftPlayer, PrefabType.Player, new Vector3(1, 0, 5), 90, true, true),
                        new BotVisibleObject(10, Master.RightPlayer, PrefabType.FireSlime, new Vector3(12, 0, 4), 30, true, true),
                        new BotVisibleObject(11, Master.RightPlayer, PrefabType.FireSlime, new Vector3(6, 0, 6), 25, true, true),
                        new BotVisibleObject(13, Master.RightPlayer, PrefabType.FireSlime, new Vector3(6.5f, 0, 5.5f), 15, true, true),
                        new BotVisibleObject(14, Master.RightPlayer, PrefabType.FireSlime, new Vector3(3.5f, 0, 4), 20, true, true),
                        new BotVisibleObject(12, Master.RightPlayer, PrefabType.FireShot, new Vector3(5, 0, 5), 10, false, true),
                        new BotVisibleObject(20, Master.LeftPlayer, PrefabType.FireSlime, new Vector3(4, 0, 5), 40, true, true)),
                cards,
                mana,
                88);
    }

    /** A board with a single enemy slime far down the lane: nothing is urgent. */
    static BotEye quietEye(List<Long> cards, int mana) {
        return new BotEye(
                List.of(
                        new BotVisibleObject(1, Master.RightPlayer, PrefabType.Player, new Vector3(17, 0, 5), 88, true, true),
                        new BotVisibleObject(2, Master.LeftPlayer, PrefabType.Player, new Vector3(1, 0, 5), 90, true, true),
                        new BotVisibleObject(10, Master.RightPlayer, PrefabType.FireSlime, new Vector3(14, 0, 4), 30, true, true)),
                cards,
                mana,
                88);
    }

    static BotCounterEvaluator neutralCounters() {
        return mock(BotCounterEvaluator.class);
    }

    static LlmCandidateBuilder builder() {
        return builder(neutralCounters());
    }

    static LlmCandidateBuilder builder(BotCounterEvaluator counters) {
        return new LlmCandidateBuilder(lookup(), counters, () -> new Random(7));
    }

    static LlmCandidates candidates() {
        return builder().build(eye(), parameters(), Master.LeftPlayer);
    }

    private static final class SummonMagic extends Magic implements ObjectSummoningMagic {
        private final PrefabType prefab;
        private final int quantity;
        private final boolean airborne;

        SummonMagic(PrefabType prefab, int quantity, boolean airborne) {
            this.prefab = prefab;
            this.quantity = quantity;
            this.airborne = airborne;
        }

        @Override
        public void run(GameContext gameContext, Master master, Vector3 position) {
        }

        @Override
        public PrefabType summonedPrefab() {
            return prefab;
        }

        @Override
        public int summonedQuantity() {
            return quantity;
        }

        @Override
        public boolean summonsAirborne() {
            return airborne;
        }
    }
}
