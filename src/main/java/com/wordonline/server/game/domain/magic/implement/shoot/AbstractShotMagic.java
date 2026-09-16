package com.wordonline.server.game.domain.magic.implement.shoot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.Shot;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

public abstract class AbstractShotMagic extends Magic {

    private final PrefabType prefabType;

    public AbstractShotMagic(PrefabType prefabType) {
        this.prefabType = prefabType;
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        run(gameContext, master, gameContext.findPlayerGameObject(master)
                .map(gameObject -> new Vector3(gameObject.getPosition()))
                .orElse(null), position);
    }

    @Override
    public void run(GameContext gameContext, Master master, Vector3 castOrigin, Vector3 position) {
        if (castOrigin == null) {
            return;
        }

        GameObject gameObject = new GameObject(
                getMaster(master),
                prefabType,
                castOrigin,
                gameContext);
        gameObject.getComponent(Shot.class).setTarget(position);
    }

    protected Master getMaster(Master master) {
        return master;
    }
}
