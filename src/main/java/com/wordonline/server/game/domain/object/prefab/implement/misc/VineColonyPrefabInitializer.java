package com.wordonline.server.game.domain.object.prefab.implement.misc;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.SummonMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("vine_colony_prefab")
public class VineColonyPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public VineColonyPrefabInitializer(Parameters parameters) {
        super(PrefabType.VineColony);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var vineColonyParameters = parameters.object(GameObjectKey.VINE_COLONY);
        gameObject.getComponents().add(new RigidBody(gameObject, vineColonyParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, vineColonyParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.getComponents().add(
                new SummonMob(
                        gameObject,
                        vineColonyParameters.intValue(ParameterKey.HP),
                        0,
                        vineColonyParameters.intValue(ParameterKey.ATTACK_INTERVAL),
                        vineColonyParameters.intValue(ParameterKey.ATTACK_RANGE),
                        PrefabType.Vine
                ));
        gameObject.addComponent(new TimedSelfDestroyer(
                gameObject,
                vineColonyParameters.floatValue(ParameterKey.DURATION)
        ));
        gameObject.setElement(ElementType.NATURE);
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
