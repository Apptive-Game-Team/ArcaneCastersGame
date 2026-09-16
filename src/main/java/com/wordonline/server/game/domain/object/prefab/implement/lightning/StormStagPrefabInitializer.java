package com.wordonline.server.game.domain.object.prefab.implement.lightning;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.OverchargeStatusEffect;
import com.wordonline.server.game.domain.object.component.mob.MovementSpeedTracker;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.StormStagMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("storm_stag_prefab")
public class StormStagPrefabInitializer extends PrefabInitializer {
    private final Parameters parameters;

    public StormStagPrefabInitializer(Parameters parameters) {
        super(PrefabType.StormStag);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var values = parameters.object(GameObjectKey.STORM_STAG);
        float maxSpeed = values.floatValue(ParameterKey.SPEED);

        gameObject.addComponent(new RigidBody(gameObject, values.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(
                gameObject, values.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new MovementSpeedTracker(gameObject, maxSpeed));
        gameObject.addComponent(new StormStagMob(
                gameObject,
                values.intValue(ParameterKey.HP),
                maxSpeed,
                TargetMask.GROUND.bit,
                values.intValue(ParameterKey.DAMAGE),
                values.floatValue(ParameterKey.ACCELERATION),
                values.floatValue(ParameterKey.DETECTION_RANGE),
                values.floatValue(ParameterKey.PANIC_DURATION)));
        gameObject.setElement(ElementType.LIGHTNING);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
        gameObject.addComponent(new OverchargeStatusEffect(gameObject, Float.POSITIVE_INFINITY));
    }
}
