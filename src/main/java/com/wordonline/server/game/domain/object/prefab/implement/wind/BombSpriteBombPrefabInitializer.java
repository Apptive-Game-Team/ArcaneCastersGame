package com.wordonline.server.game.domain.object.prefab.implement.wind;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.BombSpriteBomb;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("bomb_sprite_bomb_prefab")
public class BombSpriteBombPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public BombSpriteBombPrefabInitializer(Parameters parameters) {
        super(PrefabType.BombSpriteBomb);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var bombParameters = parameters.object(GameObjectKey.BOMB_SPRITE_BOMB);

        gameObject.setElement(ElementType.WIND);
        gameObject.addComponent(new BombSpriteBomb(
                gameObject,
                bombParameters.intValue(ParameterKey.DAMAGE),
                bombParameters.floatValue(ParameterKey.SPEED),
                bombParameters.floatValue(ParameterKey.RADIUS)));
    }
}
