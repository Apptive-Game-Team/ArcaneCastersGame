package com.wordonline.server.game.domain.object.prefab.implement.subprefab;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.OnStartAttacker;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

@Component("titan_fist_prefab")
public class TitanFistPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public TitanFistPrefabInitializer(Parameters parameters) {
        super(PrefabType.TitanFist);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var fistParameters = parameters.object(GameObjectKey.TITAN_FIST);
        float radius = fistParameters.floatValue(ParameterKey.RADIUS);

        gameObject.addCollider(new CircleCollider(gameObject, radius, true));
        gameObject.setElement(ElementType.ROCK);
        gameObject.addComponent(new OnStartAttacker(
                gameObject,
                radius,
                fistParameters.intValue(ParameterKey.DAMAGE)
        ));
        gameObject.addComponent(new TimedSelfDestroyer(
                gameObject,
                fistParameters.floatValue(ParameterKey.DURATION)
        ));
    }
}
