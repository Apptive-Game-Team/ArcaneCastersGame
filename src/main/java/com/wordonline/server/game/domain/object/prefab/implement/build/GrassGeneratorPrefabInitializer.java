package com.wordonline.server.game.domain.object.prefab.implement.build;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.magic.GrassSpread;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("grass_generator_prefab")
public class GrassGeneratorPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public GrassGeneratorPrefabInitializer(Parameters parameters) {
        super(PrefabType.GrassGenerator);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var grassGeneratorParameters = parameters.object(GameObjectKey.GRASS_GENERATOR);
        float radius = grassGeneratorParameters.floatValue(ParameterKey.RADIUS);
        float effectRadius = grassGeneratorParameters.floatValue(ParameterKey.EFFECT_RADIUS);
        float leafFieldRadius = parameters.object(GameObjectKey.LEAF_FIELD).floatValue(ParameterKey.RADIUS);

        gameObject.addComponent(new RigidBody(gameObject, grassGeneratorParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, radius, false));
        gameObject.addComponent(new DummyMob(gameObject, grassGeneratorParameters.intValue(ParameterKey.HP)));
        gameObject.addComponent(new GrassSpread(
                gameObject,
                grassGeneratorParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                effectRadius,
                grassGeneratorParameters.intValue(ParameterKey.QUANTITY),
                leafFieldRadius
        ));
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, grassGeneratorParameters.floatValue(ParameterKey.DURATION)));
        gameObject.setElement(ElementType.NATURE);
        gameObject.addComponent(new BuildingEffectReceiver(gameObject));
    }
}
