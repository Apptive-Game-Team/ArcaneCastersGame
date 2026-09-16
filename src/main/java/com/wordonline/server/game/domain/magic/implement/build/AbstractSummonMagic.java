package com.wordonline.server.game.domain.magic.implement.build;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;

public abstract class AbstractSummonMagic extends Magic implements ObjectSummoningMagic {

    private final PrefabType prefabType;

    public AbstractSummonMagic(PrefabType prefabType) {
        this.prefabType = prefabType;
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        new GameObject(getMaster(master), prefabType, position, gameContext);
    }

    @Override
    public PrefabType summonedPrefab() {
        return prefabType;
    }

    protected Master getMaster(Master master) {
        return master;
    }
}