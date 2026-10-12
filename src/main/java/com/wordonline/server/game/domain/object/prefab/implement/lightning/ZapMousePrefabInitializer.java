package com.wordonline.server.game.domain.object.prefab.implement.lightning;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.ElectricDeathEnergy;
import com.wordonline.server.game.domain.object.component.effect.receiver.LightningSummonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.MeleeAttackMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("zap_mouse_prefab")
public class ZapMousePrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public ZapMousePrefabInitializer(Parameters parameters) {
        super(PrefabType.ZapMouse);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var zapMouseParameters = parameters.object(GameObjectKey.ZAP_MOUSE);
        gameObject.addComponent(new RigidBody(gameObject, zapMouseParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, zapMouseParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new MeleeAttackMob(gameObject,
                zapMouseParameters.intValue(ParameterKey.HP),
                zapMouseParameters.floatValue(ParameterKey.SPEED),
                TargetMask.GROUND.bit,
                zapMouseParameters.intValue(ParameterKey.DAMAGE),
                zapMouseParameters.floatValue(ParameterKey.ATTACK_INTERVAL)));
        gameObject.setElement(ElementType.LIGHTNING);
        gameObject.addComponent(new ElectricDeathEnergy(gameObject));
        gameObject.addComponent(new LightningSummonEffectReceiver(gameObject));
    }
}
