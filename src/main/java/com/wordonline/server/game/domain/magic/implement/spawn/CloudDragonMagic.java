package com.wordonline.server.game.domain.magic.implement.spawn;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;

@Component("cloud_dragon")
public class CloudDragonMagic extends AbstractSpawnMagic {
    
    public CloudDragonMagic(Parameters parameters) {
        super(PrefabType.CloudDragon, parameters.object(GameObjectKey.CLOUD_DRAGON),
                GameConfig.AERIAL_MOB_INIT_HEIGHT);
    }
}
