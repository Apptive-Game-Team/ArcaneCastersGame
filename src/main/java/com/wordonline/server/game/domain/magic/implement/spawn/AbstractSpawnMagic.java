package com.wordonline.server.game.domain.magic.implement.spawn;

import java.util.concurrent.ThreadLocalRandom;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

public abstract class AbstractSpawnMagic extends Magic implements ObjectSummoningMagic {

    private static final int DEFAULT_QUANTITY = 1;
    private static final float SPAWN_RANGE = 1.0f;

    private final PrefabType prefabType;
    private final GameObjectParameters parameters;
    private final float spawnHeight;

    protected AbstractSpawnMagic(PrefabType prefabType, GameObjectParameters parameters) {
        this(prefabType, parameters, 0f);
    }

    protected AbstractSpawnMagic(PrefabType prefabType, GameObjectParameters parameters, float spawnHeight) {
        this.prefabType = prefabType;
        this.parameters = parameters;
        this.spawnHeight = spawnHeight;
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        int quantity = parameters.intValueOrDefault(ParameterKey.QUANTITY, DEFAULT_QUANTITY);
        Master currentMaster = getMaster(master);

        for (int i = 0; i < quantity; i++) {
            new GameObject(currentMaster, prefabType, spawnPosition(position, quantity), gameContext);
        }
    }

    @Override
    public PrefabType summonedPrefab() {
        return prefabType;
    }

    @Override
    public int summonedQuantity() {
        return parameters.intValueOrDefault(ParameterKey.QUANTITY, DEFAULT_QUANTITY);
    }

    private Vector3 spawnPosition(Vector3 position, int quantity) {
        if (quantity == 1) {
            return new Vector3(position.getX(), spawnHeight, position.getZ());
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float randomX = random.nextFloat(-SPAWN_RANGE, SPAWN_RANGE);
        float randomZ = random.nextFloat(-SPAWN_RANGE, SPAWN_RANGE);
        return new Vector3(position.getX() + randomX, spawnHeight, position.getZ() + randomZ);
    }

    protected Master getMaster(Master master) {
        return master;
    }
}
