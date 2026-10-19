package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

@Component("crater_carrier")
public class CraterCarrierMagic extends AbstractSpawnMagic {
    public CraterCarrierMagic(Parameters parameters) {
        super(PrefabType.CraterCarrier, parameters.object(GameObjectKey.CRATER_CARRIER));
    }
}
