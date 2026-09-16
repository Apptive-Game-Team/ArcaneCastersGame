package com.wordonline.server.game.domain.magic.implement.spawn;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;

@Component("sea_serpent")
public class SeaSerpentMagic extends AbstractSpawnMagic {

    public SeaSerpentMagic(Parameters parameters) {
        super(PrefabType.SeaSerpent, parameters.object(GameObjectKey.SEA_SERPENT));
    }
}
