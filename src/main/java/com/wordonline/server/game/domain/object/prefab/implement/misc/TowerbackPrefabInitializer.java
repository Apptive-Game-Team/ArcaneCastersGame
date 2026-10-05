package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.AttackMob;
import com.wordonline.server.game.domain.object.component.mob.simple.Tower;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("towerback_prefab")
public class TowerbackPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public TowerbackPrefabInitializer(Parameters parameters) {
        super(PrefabType.Towerback);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var towerbackParameters = parameters.object(GameObjectKey.TOWERBACK);
        gameObject.addComponent(new RigidBody(gameObject, towerbackParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, towerbackParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.setElement(ElementType.ROCK);
        gameObject.addComponent(new AttackMob(gameObject,
                towerbackParameters.intValue(ParameterKey.HP),
                towerbackParameters.floatValue(ParameterKey.SUB_SPEED),
                TargetMask.GROUND.bit,
                towerbackParameters.intValue(ParameterKey.SUB_DAMAGE),
                towerbackParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                towerbackParameters.floatValue(ParameterKey.SUB_ATTACK_RANGE)
        ));
        gameObject.addComponent(new Tower(
                gameObject,
                towerbackParameters.intValue(ParameterKey.DAMAGE),
                TargetMask.AIR.bit,
                0.2f,
                towerbackParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                towerbackParameters.floatValue(ParameterKey.ATTACK_RANGE)
        ));
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
