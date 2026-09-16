package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

@Component("storm_stag")
public class StormStagMagic extends AbstractSpawnMagic {
    public StormStagMagic(Parameters parameters) {
        super(PrefabType.StormStag, parameters.object(GameObjectKey.STORM_STAG));
    }
}
