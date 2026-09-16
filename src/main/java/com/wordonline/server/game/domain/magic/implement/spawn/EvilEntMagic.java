package com.wordonline.server.game.domain.magic.implement.spawn;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;

@Component("evil_ent")
public class EvilEntMagic extends AbstractSpawnMagic {

    public EvilEntMagic(Parameters parameters) {
        super(PrefabType.EvilEnt, parameters.object(GameObjectKey.EVIL_ENT));
    }
}
