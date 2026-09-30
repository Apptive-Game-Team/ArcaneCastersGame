package com.wordonline.server.game.domain.object.prefab.implement.rock;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("earth_call_prefab")
public class EarthCallPrefabInitializer extends PrefabInitializer {

    private static final float EFFECT_DURATION_SECONDS = 1f;

    public EarthCallPrefabInitializer() {
        super(PrefabType.EarthCall);
    }

    @Override
    public void initialize(GameObject gameObject) {
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, EFFECT_DURATION_SECONDS));
        gameObject.setElement(ElementType.ROCK);
    }
}
