package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.Cannon;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("ground_cannon_prefab")
public class GroundCannonPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public GroundCannonPrefabInitializer(Parameters parameters) {
        super(PrefabType.GroundCannon);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var groundCannonParameters = parameters.object(GameObjectKey.GROUND_CANNON);
        gameObject.getComponents().add(new RigidBody(gameObject, groundCannonParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, groundCannonParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.getComponents().add(new Cannon(gameObject,
                groundCannonParameters.intValue(ParameterKey.HP),
                groundCannonParameters.intValue(ParameterKey.DAMAGE), TargetMask.GROUND.bit,
                groundCannonParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                groundCannonParameters.floatValue(ParameterKey.ATTACK_RANGE)
                ));
        gameObject.addComponent(new TimedSelfDestroyer(
                gameObject,
                60
        ));
        gameObject.addComponent(new RockDeathRemnant(gameObject));
        gameObject.setElement(ElementType.ROCK);
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
