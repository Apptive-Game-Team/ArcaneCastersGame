package com.wordonline.server.game.domain.magic.implement.explode;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.GameLoop;

public abstract class AbstractExplosionMagic extends Magic {

    private final PrefabType prefabType;

    public AbstractExplosionMagic(PrefabType prefabType) {
        this.prefabType = prefabType;
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        new GameObject(getMaster(master), prefabType, position, gameContext);
    }

    protected Master getMaster(Master master) {
        return master;
    }
}
