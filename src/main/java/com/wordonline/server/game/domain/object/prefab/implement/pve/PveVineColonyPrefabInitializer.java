package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("pve_vine_colony_prefab")
public class PveVineColonyPrefabInitializer extends SimplePveBossInitializer {

    private static final float BOSS_SPAWN_INTERVAL = 10f;
    private static final int BOSS_SPAWN_COUNT = 2;

    public PveVineColonyPrefabInitializer(Parameters parameters) {
        super(
                PrefabType.PveVineColony,
                parameters,
                "vine_colony",
                ElementType.NATURE,
                List.of(
                        new SpawnConfig(PrefabType.LeafSlime, BOSS_SPAWN_INTERVAL, BOSS_SPAWN_COUNT),
                        new SpawnConfig(PrefabType.WaterSlime, BOSS_SPAWN_INTERVAL, BOSS_SPAWN_COUNT),
                        new SpawnConfig(PrefabType.VineSpirit, BOSS_SPAWN_INTERVAL, BOSS_SPAWN_COUNT)
                ),
                List.of("vine_toss")
        );
    }
}
