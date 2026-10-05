package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.MeleeAttackMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("pve_fire_tadpole_prefab")
public class PveFireTadpolePrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public PveFireTadpolePrefabInitializer(Parameters parameters) {
        super(PrefabType.PveFireTadpole);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var fireTadpoleParameters = parameters.object(GameObjectKey.PVE_FIRE_TADPOLE);
        gameObject.addComponent(new RigidBody(gameObject, fireTadpoleParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, fireTadpoleParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new MeleeAttackMob(gameObject,
                fireTadpoleParameters.intValue(ParameterKey.HP),
                fireTadpoleParameters.floatValue(ParameterKey.SPEED),
                TargetMask.GROUND.bit,
                fireTadpoleParameters.intValue(ParameterKey.DAMAGE),
                fireTadpoleParameters.floatValue(ParameterKey.ATTACK_INTERVAL)));
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, fireTadpoleParameters.floatValue(ParameterKey.DURATION)));
        gameObject.setElement(ElementType.FIRE);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
