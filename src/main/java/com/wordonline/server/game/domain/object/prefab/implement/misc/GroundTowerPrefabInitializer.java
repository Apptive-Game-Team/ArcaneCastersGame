package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.mob.simple.Tower;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("ground_tower_prefab")
public class GroundTowerPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public GroundTowerPrefabInitializer(Parameters parameters) {
        super(PrefabType.GroundTower);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var groundTowerParameters = parameters.object(GameObjectKey.GROUND_TOWER);
        gameObject.getComponents().add(new RigidBody(gameObject, groundTowerParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, groundTowerParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new DummyMob(gameObject, groundTowerParameters.intValue(ParameterKey.HP)));
        gameObject.getComponents().add(
                new Tower(
                        gameObject,
                        groundTowerParameters.intValue(ParameterKey.DAMAGE),
                        TargetMask.AIR.bit,
                        groundTowerParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                        groundTowerParameters.floatValue(ParameterKey.ATTACK_RANGE)
                ));
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, groundTowerParameters.floatValue(ParameterKey.DURATION)));
        gameObject.addComponent(new RockDeathRemnant(gameObject));
        gameObject.setElement(ElementType.ROCK);
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
