package com.wordonline.server.bot.llm;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.bot.BotVisibleObject;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

/** A two-card hand and a small board shared by the LLM brain tests. */
final class LlmTestFixtures {

    static final long FIRE_SHOT_ID = 3;
    static final long ROCK_GOLEM_ID = 7;

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

    static LongFunction<Magic> lookup() {
        Map<Long, Magic> magics = Map.of(
                FIRE_SHOT_ID, magic(FIRE_SHOT_ID, "fire_shot"),
                ROCK_GOLEM_ID, magic(ROCK_GOLEM_ID, "rock_golem_summon"));
        return magics::get;
    }

    /** fire_shot costs 3 and deals 20 in radius 1.5; rock_golem_summon costs 8. */
    static Parameters parameters() {
        Parameters parameters = mock(Parameters.class);
        when(parameters.getValueOrDefault(anyString(), anyString(), anyDouble())).thenReturn(0.0);
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("mana_cost"), anyDouble())).thenReturn(3.0);
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("damage"), anyDouble())).thenReturn(20.0);
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("radius"), anyDouble())).thenReturn(1.5);
        when(parameters.getValueOrDefault(eq("fire_shot"), eq("range"), anyDouble())).thenReturn(10.0);
        when(parameters.getValueOrDefault(eq("rock_golem_summon"), eq("mana_cost"), anyDouble())).thenReturn(8.0);
        return parameters;
    }

    /** The bot plays the left side with 5 mana: fire_shot is affordable, the golem is not. */
    static BotEye eye() {
        return new BotEye(
                List.of(
                        new BotVisibleObject(1, Master.RightPlayer, PrefabType.Player, new Vector3(17, 0, 5), 100, true, true),
                        new BotVisibleObject(10, Master.RightPlayer, PrefabType.FireSlime, new Vector3(12, 0, 4), 30, true, true),
                        new BotVisibleObject(11, Master.RightPlayer, PrefabType.FireSlime, new Vector3(6, 0, 6), 25, true, true),
                        new BotVisibleObject(12, Master.RightPlayer, PrefabType.FireShot, new Vector3(5, 0, 5), 10, false, true),
                        new BotVisibleObject(20, Master.LeftPlayer, PrefabType.FireSlime, new Vector3(4, 0, 5), 40, true, true)),
                List.of(FIRE_SHOT_ID, ROCK_GOLEM_ID),
                5,
                88);
    }

    static LlmCandidates candidates() {
        return new LlmCandidateBuilder(lookup()).build(eye(), parameters(), Master.LeftPlayer);
    }
}
