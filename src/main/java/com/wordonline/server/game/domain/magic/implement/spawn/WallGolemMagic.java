package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

@Component("wall_golem")
public class WallGolemMagic extends AbstractSpawnMagic {
    public WallGolemMagic(Parameters parameters) {
        super(PrefabType.WallGolem, parameters.object(GameObjectKey.WALL_GOLEM));
    }
}
