package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.effect.RockRemnantSize;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.RockGolemMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("rock_golem_prefab")
public class RockGolemPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public RockGolemPrefabInitializer(Parameters parameters) {
        super(PrefabType.RockGolem);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var rockGolemParameters = parameters.object(GameObjectKey.ROCK_GOLEM);
        gameObject.getComponents().add(new RigidBody(gameObject, rockGolemParameters.intValue(ParameterKey.MASS)));
        gameObject.getComponents().add(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, rockGolemParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.getComponents().add(new RockGolemMob(gameObject,
                rockGolemParameters.intValue(ParameterKey.HP),
                rockGolemParameters.floatValue(ParameterKey.SPEED),
                TargetMask.GROUND.bit,
                rockGolemParameters.intValue(ParameterKey.DAMAGE),
                rockGolemParameters.floatValue(ParameterKey.ATTACK_INTERVAL)
        ));
        gameObject.setElement(ElementType.ROCK);
        gameObject.addComponent(new RockDeathRemnant(gameObject, RockRemnantSize.SMALL));
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
