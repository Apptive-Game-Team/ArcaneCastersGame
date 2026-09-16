package com.wordonline.server.game.domain.magic.implement.drop;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.springframework.stereotype.Component;

@Component("chicken_commando")
public class ChickenCommandoMagic extends Magic {

    @Override
    public void run(GameContext gameContext, Master master, Vector3 position) {
        float spawnHeight = gameContext.getParameters()
                .object(GameObjectKey.CHICKEN_COMMANDO)
                .floatValue(ParameterKey.SPAWN_HEIGHT);
        Vector3 spawnPosition = new Vector3(position.getX(), spawnHeight, position.getZ());
        new GameObject(master, PrefabType.ChickenCommando, spawnPosition, gameContext);
    }
}
