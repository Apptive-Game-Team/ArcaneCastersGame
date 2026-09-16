package com.wordonline.server.game.domain.object.prefab.implement.wind;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("bomb_sprite_explosion_prefab")
public class BombSpriteExplosionPrefabInitializer extends PrefabInitializer {

    private static final float PRESENTATION_DURATION = 0.6f;

    public BombSpriteExplosionPrefabInitializer() {
        super(PrefabType.BombSpriteExplosion);
    }

    @Override
    public void initialize(GameObject gameObject) {
        gameObject.addComponent(new TimedSelfDestroyer(
                gameObject,
                PRESENTATION_DURATION));
    }
}
