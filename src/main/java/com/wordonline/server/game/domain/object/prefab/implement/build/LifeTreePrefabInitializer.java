package com.wordonline.server.game.domain.object.prefab.implement.build;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.simple.Totem;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("life_tree_prefab")
public class LifeTreePrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public LifeTreePrefabInitializer(Parameters parameters) {
        super(PrefabType.LifeTree);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var lifeTreeParameters = parameters.object(GameObjectKey.LIFE_TREE);
        gameObject.getComponents().add(new RigidBody(gameObject, lifeTreeParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, lifeTreeParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.getComponents().add(new Totem(gameObject,
                lifeTreeParameters.intValue(ParameterKey.HP),
                lifeTreeParameters.intValue(ParameterKey.DAMAGE),
                lifeTreeParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                lifeTreeParameters.floatValue(ParameterKey.EFFECT_RADIUS),
                TargetMask.GROUND.bit));
        gameObject.setElement(EnumSet.of(ElementType.NATURE));
        gameObject.getComponents().add(new TimedSelfDestroyer(gameObject, lifeTreeParameters.intValue(ParameterKey.DURATION)));
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
