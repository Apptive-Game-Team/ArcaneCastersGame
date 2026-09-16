package com.wordonline.server.game.domain.object.prefab.implement.build;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.TitanRemnantMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("titan_remnant_prefab")
public class TitanRemnantPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public TitanRemnantPrefabInitializer(Parameters parameters) {
        super(PrefabType.TitanRemnant);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var stats = parameters.object(GameObjectKey.TITAN_REMNANT);
        gameObject.addComponent(new RigidBody(gameObject, stats.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(
                gameObject,
                stats.floatValue(ParameterKey.RADIUS),
                false
        ));
        gameObject.addComponent(new TitanRemnantMob(
                gameObject,
                stats.intValue(ParameterKey.HP),
                stats.floatValue(ParameterKey.ATTACK_INTERVAL),
                stats.floatValue(ParameterKey.ATTACK_RANGE)
        ));
        gameObject.addComponent(new TimedSelfDestroyer(
                gameObject,
                stats.floatValue(ParameterKey.DURATION)
        ));
        gameObject.setElement(ElementType.ROCK);
        gameObject.addComponent(new BuildingEffectReceiver(gameObject));
    }
}
