package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.RockGolemMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("wall_golem_prefab")
public class WallGolemPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public WallGolemPrefabInitializer(Parameters parameters) {
        super(PrefabType.WallGolem);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var wallGolemParameters = parameters.object(GameObjectKey.WALL_GOLEM);
        gameObject.getComponents().add(new RigidBody(gameObject, wallGolemParameters.intValue(ParameterKey.MASS)));
        gameObject.getComponents().add(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, wallGolemParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.getComponents().add(new RockGolemMob(gameObject,
                wallGolemParameters.intValue(ParameterKey.HP),
                wallGolemParameters.floatValue(ParameterKey.SPEED),
                TargetMask.GROUND.bit,
                wallGolemParameters.intValue(ParameterKey.DAMAGE),
                wallGolemParameters.floatValue(ParameterKey.ATTACK_INTERVAL)
        ));
        gameObject.setElement(ElementType.ROCK);
        gameObject.addComponent(new RockDeathRemnant(gameObject));
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
