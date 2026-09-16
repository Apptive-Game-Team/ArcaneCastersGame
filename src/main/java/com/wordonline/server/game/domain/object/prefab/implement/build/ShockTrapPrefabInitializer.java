package com.wordonline.server.game.domain.object.prefab.implement.build;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.magic.ShockTrapDetector;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("shock_trap_prefab")
public class ShockTrapPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public ShockTrapPrefabInitializer(Parameters parameters) {
        super(PrefabType.ShockTrap);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var shockTrapParameters = parameters.object(GameObjectKey.SHOCK_TRAP);
        gameObject.addComponent(new RigidBody(gameObject, shockTrapParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, shockTrapParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new DummyMob(gameObject, shockTrapParameters.intValue(ParameterKey.HP)));
        gameObject.addComponent(new ShockTrapDetector(
                gameObject,
                shockTrapParameters.floatValue(ParameterKey.EFFECT_RADIUS),
                shockTrapParameters.floatValue(ParameterKey.TRIGGER_DELAY),
                shockTrapParameters.floatValue(ParameterKey.STUN_DURATION)
        ));
        gameObject.addComponent(new BuildingEffectReceiver(gameObject));
        gameObject.setElement(ElementType.LIGHTNING);
    }
}
