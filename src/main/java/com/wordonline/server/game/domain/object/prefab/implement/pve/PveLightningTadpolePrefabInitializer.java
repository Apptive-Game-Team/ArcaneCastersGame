package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.ElectricDeathEnergy;
import com.wordonline.server.game.domain.object.component.effect.receiver.LightningSummonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.MeleeAttackMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("pve_lightning_tadpole_prefab")
public class PveLightningTadpolePrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public PveLightningTadpolePrefabInitializer(Parameters parameters) {
        super(PrefabType.PveLightningTadpole);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var lightningTadpoleParameters = parameters.object(GameObjectKey.PVE_LIGHTNING_TADPOLE);
        gameObject.addComponent(new RigidBody(gameObject, lightningTadpoleParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, lightningTadpoleParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new MeleeAttackMob(gameObject,
                lightningTadpoleParameters.intValue(ParameterKey.HP),
                lightningTadpoleParameters.floatValue(ParameterKey.SPEED),
                TargetMask.GROUND.bit,
                lightningTadpoleParameters.intValue(ParameterKey.DAMAGE),
                lightningTadpoleParameters.floatValue(ParameterKey.ATTACK_INTERVAL)));
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, lightningTadpoleParameters.floatValue(ParameterKey.DURATION)));
        gameObject.setElement(ElementType.LIGHTNING);
        gameObject.addComponent(new ElectricDeathEnergy(gameObject));
        gameObject.addComponent(new LightningSummonEffectReceiver(gameObject));
    }
}
