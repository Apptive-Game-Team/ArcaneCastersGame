package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

@Component("wind_spirit")
public class WindSpiritMagic extends AbstractSpawnMagic {
    public WindSpiritMagic(Parameters parameters) {
        super(PrefabType.WindSpirit, parameters.object(GameObjectKey.WIND_SPIRIT),
                GameConfig.AERIAL_MOB_INIT_HEIGHT);
    }
}
