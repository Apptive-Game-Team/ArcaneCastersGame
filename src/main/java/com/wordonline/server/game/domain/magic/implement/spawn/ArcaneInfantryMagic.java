package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

@Component("arcane_infantry")
public class ArcaneInfantryMagic extends AbstractSpawnMagic {
    public ArcaneInfantryMagic(Parameters parameters) {
        super(PrefabType.ArcaneInfantry, parameters.object(GameObjectKey.ARCANE_INFANTRY));
    }
}
