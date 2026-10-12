package com.wordonline.server.game.domain.object.prefab.implement.nature;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BeanShooterMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("bean_shooter_prefab")
public class BeanShooterPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public BeanShooterPrefabInitializer(Parameters parameters) {
        super(PrefabType.BeanShooter);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var shooterParameters = parameters.object(GameObjectKey.BEAN_SHOOTER);
        gameObject.getComponents().add(new RigidBody(gameObject, shooterParameters.intValue(ParameterKey.MASS)));
        gameObject.getComponents().add(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, shooterParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.getComponents().add(new BeanShooterMob(gameObject,
                shooterParameters.intValue(ParameterKey.HP),
                shooterParameters.floatValue(ParameterKey.SPEED),
                shooterParameters.intValue(ParameterKey.DAMAGE),
                shooterParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                shooterParameters.floatValue(ParameterKey.ATTACK_RANGE)));
        gameObject.setElement(ElementType.NATURE);
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
