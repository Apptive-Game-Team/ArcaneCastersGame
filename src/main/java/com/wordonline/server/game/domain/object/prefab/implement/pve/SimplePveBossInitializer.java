package com.wordonline.server.game.domain.object.prefab.implement.pve;

import org.springframework.beans.factory.annotation.Autowired;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.magic.Spawner;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PVEBossMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public abstract class SimplePveBossInitializer extends PrefabInitializer {

    private static final float DEFAULT_BOSS_SPEED = 0f;
    private static final float DEFAULT_BOSS_ATTACK_INTERVAL = 2.5f;
    private static final float DEFAULT_BOSS_ATTACK_RANGE = 7f;

    private final Parameters parameters;
    @Autowired
    private DatabaseMagicParser magicParser;

    private final String parameterKey;
    private final ElementType elementType;
    @Getter(AccessLevel.PROTECTED)
    private final float bossSpeed;
    @Getter(AccessLevel.PROTECTED)
    private final float bossAttackInterval;
    @Getter(AccessLevel.PROTECTED)
    private final float bossAttackRange;
    @Getter(AccessLevel.PROTECTED)
    private final int targetMask;
    private final List<SpawnConfig> spawnConfigs;
    private final List<String> magicNames;

    protected SimplePveBossInitializer(PrefabType prefabType,
                                       Parameters parameters,
                                       String parameterKey,
                                       ElementType elementType,
                                       List<SpawnConfig> spawnConfigs,
                                       List<String> magicNames) {
        this(
                prefabType,
                parameters,
                parameterKey,
                elementType,
                DEFAULT_BOSS_SPEED,
                DEFAULT_BOSS_ATTACK_INTERVAL,
                DEFAULT_BOSS_ATTACK_RANGE,
                spawnConfigs,
                magicNames
        );
    }

    protected SimplePveBossInitializer(PrefabType prefabType,
                                       Parameters parameters,
                                       String parameterKey,
                                       ElementType elementType,
                                       float bossSpeed,
                                       float bossAttackInterval,
                                       float bossAttackRange,
                                       List<SpawnConfig> spawnConfigs,
                                       List<String> magicNames) {
        super(prefabType);
        this.parameters = parameters;
        this.parameterKey = parameterKey;
        this.elementType = elementType;
        this.bossSpeed = bossSpeed;
        this.bossAttackInterval = bossAttackInterval;
        this.bossAttackRange = bossAttackRange;
        this.targetMask = TargetMask.GROUND.bit;
        this.spawnConfigs = spawnConfigs == null ? List.of() : List.copyOf(spawnConfigs);
        this.magicNames = magicNames == null ? List.of() : List.copyOf(magicNames);
    }

    @Override
    public void initialize(GameObject gameObject) {
        gameObject.addComponent(new RigidBody(gameObject, (int) parameters.getValue(parameterKey, "mass")));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, (float) parameters.getValue(parameterKey, "radius"), true));
        gameObject.setElement(elementType);
        // Only objects with a Collidable component take part in collisions. Units and
        // buildings get one through their effect receiver; without it a boss was skipped by
        // PhysicSystem, so projectiles such as the dragon tower's flame flew straight through
        // it and burn, slow and other effects never landed.
        gameObject.addComponent(new CommonEffectReceiver(gameObject));

        // The boss mob goes on before the Spawner: a Spawner is also a Mob, and damage, slows and
        // every other effect find their target with getComponent(Mob.class), which returns the
        // first one. With the Spawner first, all damage landed on it and was discarded.
        int maxHp = (int) parameters.getValue(parameterKey, "hp");
        List<Magic> magics = resolveMagics(magicNames);
        gameObject.addComponent(createBossMob(gameObject, maxHp, magics));

        SpawnConfig spawnConfig = selectRandomSpawnConfig();
        if (spawnConfig != null) {
            gameObject.addComponent(new Spawner(
                    gameObject,
                    0,
                    spawnConfig.prefabType(),
                    spawnConfig.intervalSec(),
                    false,
                    spawnConfig.spawnCount()
            ));
        }
    }

    protected Magic parseMagic(String magicName) {
        return magicParser.parseMagicForBot(magicName);
    }

    protected PVEBossMob createBossMob(GameObject gameObject, int maxHp, List<Magic> magics) {
        return new PVEBossMob(
                gameObject,
                maxHp,
                bossSpeed,
                targetMask,
                bossAttackInterval,
                bossAttackRange,
                magics
        );
    }

    private List<Magic> resolveMagics(List<String> magicNames) {
        return magicNames.stream()
                .map(this::parseMagic)
                .filter(Objects::nonNull)
                .toList();
    }

    private SpawnConfig selectRandomSpawnConfig() {
        if (spawnConfigs.isEmpty()) {
            return null;
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(spawnConfigs.size());
        return spawnConfigs.get(randomIndex);
    }

    protected record SpawnConfig(PrefabType prefabType, float intervalSec, int spawnCount) {
    }
}
