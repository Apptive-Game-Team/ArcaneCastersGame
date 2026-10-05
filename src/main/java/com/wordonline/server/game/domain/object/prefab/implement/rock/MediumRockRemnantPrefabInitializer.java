package com.wordonline.server.game.domain.object.prefab.implement.rock;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.StaticObstacle;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("medium_rock_remnant_prefab")
public class MediumRockRemnantPrefabInitializer extends PrefabInitializer {

    private static final float TIME_TO_LIVE_SECONDS = 20f;

    private final Parameters parameters;

    public MediumRockRemnantPrefabInitializer(Parameters parameters) {
        super(PrefabType.MediumRockRemnant);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var remnantParameters = parameters.object(GameObjectKey.MEDIUM_ROCK_REMNANT);
        gameObject.addCollider(new CircleCollider(
                gameObject,
                remnantParameters.floatValue(ParameterKey.RADIUS),
                false
        ));
        gameObject.addComponent(new StaticObstacle(gameObject));
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, TIME_TO_LIVE_SECONDS));
        gameObject.setElement(ElementType.ROCK);
    }
}
