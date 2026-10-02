package com.wordonline.server.game.domain.object.prefab.implement.build;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.LightningTowerMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("electric_tower_prefab")
public class ElectricTowerPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public ElectricTowerPrefabInitializer(Parameters parameters) {
        super(PrefabType.ElectricTower);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var electricTowerParameters = parameters.object(GameObjectKey.ELECTRIC_TOWER);
        gameObject.addComponent(new RigidBody(gameObject, electricTowerParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, electricTowerParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new LightningTowerMob(
                gameObject,
                electricTowerParameters.intValue(ParameterKey.HP),
                electricTowerParameters.intValue(ParameterKey.DAMAGE),
                electricTowerParameters.intValue(ParameterKey.CHAIN_DAMAGE),
                electricTowerParameters.intValue(ParameterKey.CHAIN_COUNT),
                electricTowerParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                electricTowerParameters.floatValue(ParameterKey.ATTACK_RANGE),
                electricTowerParameters.floatValue(ParameterKey.CHAIN_RADIUS)
        ));
        gameObject.addComponent(new TimedSelfDestroyer(
                gameObject,
                electricTowerParameters.floatValue(ParameterKey.DURATION)
        ));
        gameObject.setElement(ElementType.LIGHTNING);
        gameObject.addComponent(new BuildingEffectReceiver(gameObject));
    }
}
