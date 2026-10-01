package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.DoubleTargetMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.ProjectileRangeAttackMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("rock_mage_prefab")
public class RockMagePrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public RockMagePrefabInitializer(Parameters parameters) {
        super(PrefabType.RockMage);
        this.parameters = parameters;
    }


    @Override
    public void initialize(GameObject gameObject) {
        var rockMageParameters = parameters.object(GameObjectKey.ROCK_MAGE);
        gameObject.getComponents().add(new RigidBody(gameObject, rockMageParameters.intValue(ParameterKey.MASS)));
        gameObject.getComponents().add(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, rockMageParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.getComponents().add(new DoubleTargetMob(gameObject,
                rockMageParameters.intValue(ParameterKey.HP),
                rockMageParameters.floatValue(ParameterKey.SPEED),
                TargetMask.ANY.bit,
                rockMageParameters.intValue(ParameterKey.DAMAGE),
                rockMageParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                rockMageParameters.floatValue(ParameterKey.ATTACK_RANGE),
                "RockShot",
                0.5f
        ));
        gameObject.setElement(ElementType.ROCK);
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
