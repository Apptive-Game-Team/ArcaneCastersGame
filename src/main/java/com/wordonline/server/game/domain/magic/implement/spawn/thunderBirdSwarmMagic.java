package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("thunder_bird_swarm")
public class thunderBirdSwarmMagic extends AbstractSpawnMagic {

    public thunderBirdSwarmMagic(Parameters parameters) {
        super(PrefabType.ThunderBird, parameters.object(GameObjectKey.THUNDER_BIRD),
                GameConfig.AERIAL_MOB_INIT_HEIGHT);
    }
}
