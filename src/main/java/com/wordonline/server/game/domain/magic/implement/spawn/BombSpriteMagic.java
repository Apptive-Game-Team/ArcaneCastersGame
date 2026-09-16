package com.wordonline.server.game.domain.magic.implement.spawn;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import org.springframework.stereotype.Component;

@Component("bomb_sprite")
public class BombSpriteMagic extends AbstractSpawnMagic {

    public BombSpriteMagic(Parameters parameters) {
        super(
                PrefabType.BombSprite,
                parameters.object(GameObjectKey.BOMB_SPRITE),
                GameConfig.AERIAL_MOB_INIT_HEIGHT);
    }
}
