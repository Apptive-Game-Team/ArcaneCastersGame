package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

@Component("bubble_spirit")
public class BubbleSpiritMagic extends AbstractSpawnMagic {
    public BubbleSpiritMagic(Parameters parameters) {
        super(PrefabType.BubbleSpirit, parameters.object(GameObjectKey.BUBBLE_SPIRIT),
                GameConfig.AERIAL_MOB_INIT_HEIGHT);
    }
}
