package com.wordonline.server.game.domain.object.prefab.implement.build;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.magic.FlameLauncher;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("dragon_tower_prefab")
public class DragonTowerPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public DragonTowerPrefabInitializer(Parameters parameters) {
        super(PrefabType.DragonTower);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var dragonTowerParameters = parameters.object(GameObjectKey.DRAGON_TOWER);

        gameObject.getComponents().add(new RigidBody(gameObject, dragonTowerParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, dragonTowerParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new DummyMob(gameObject, dragonTowerParameters.intValue(ParameterKey.HP)));
        gameObject.getComponents().add(new FlameLauncher(
                gameObject,
                dragonTowerParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                dragonTowerParameters.floatValue(ParameterKey.ATTACK_RANGE)));
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, dragonTowerParameters.floatValue(ParameterKey.DURATION)));
        gameObject.addComponent(new RockDeathRemnant(gameObject));
        gameObject.setElement(ElementType.FIRE);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
