package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.implement.shoot.VineFanMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PVEBossMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.VineWitchMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("pve_vine_witch_prefab")
public class PveVineWitchPrefabInitializer extends SimplePveBossInitializer {

    private static final float BOSS_ATTACK_INTERVAL = 8f;
    private static final int BOSS_SPAWN_COUNT = 2;
    private static final List<String> MAGIC_NAMES = List.of(
            "vine_toss",
            "vine_colony"
    );

    public PveVineWitchPrefabInitializer(Parameters parameters) {
        super(
                PrefabType.PveVineWitch,
                parameters,
                "pve_vine_witch",
                ElementType.NATURE,
                0f,
                BOSS_ATTACK_INTERVAL,
                7f,
                List.of(
                        new SpawnConfig(PrefabType.LeafSlime, BOSS_ATTACK_INTERVAL, BOSS_SPAWN_COUNT),
                        new SpawnConfig(PrefabType.WaterSlime, BOSS_ATTACK_INTERVAL, BOSS_SPAWN_COUNT),
                        new SpawnConfig(PrefabType.VineSpirit, BOSS_ATTACK_INTERVAL, BOSS_SPAWN_COUNT)
                ),
                MAGIC_NAMES
        );
    }

    @Override
    protected PVEBossMob createBossMob(GameObject gameObject, int maxHp, List<Magic> magics) {
        Magic vineFanMagic = parseMagic("vine_fan");
        return new VineWitchMob(
                gameObject,
                maxHp,
                getBossSpeed(),
                getTargetMask(),
                getBossAttackInterval(),
                getBossAttackRange(),
                magics,
                vineFanMagic instanceof VineFanMagic fanMagic ? fanMagic : null
        );
    }
}
