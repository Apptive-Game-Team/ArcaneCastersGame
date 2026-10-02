package com.wordonline.server.game.domain.object.prefab.implement.rock;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.Slime;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("mini_rock_prefab")
public class MiniRockPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public MiniRockPrefabInitializer(Parameters parameters) {
        super(PrefabType.MiniRock);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var miniRockParameters = parameters.object(GameObjectKey.MINI_ROCK);
        gameObject.getComponents().add(new RigidBody(gameObject, miniRockParameters.intValue(ParameterKey.MASS)));
        gameObject.getComponents().add(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, miniRockParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.getComponents().add(new Slime(gameObject,
                miniRockParameters.intValue(ParameterKey.HP),
                miniRockParameters.floatValue(ParameterKey.SPEED),
                TargetMask.GROUND.bit,
                miniRockParameters.intValue(ParameterKey.DAMAGE),
                miniRockParameters.floatValue(ParameterKey.ATTACK_INTERVAL)));
        gameObject.setElement(ElementType.ROCK);
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
