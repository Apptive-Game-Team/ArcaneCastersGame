package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.component.Item;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.Totem;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component("healing_totem_prefab")
public class HealingTotemPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public HealingTotemPrefabInitializer(Parameters parameters) {
        super(PrefabType.HealingTotem);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var healingTotemParameters = parameters.object(GameObjectKey.HEALING_TOTEM);
        gameObject.getComponents().add(new RigidBody(gameObject, healingTotemParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, healingTotemParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.getComponents().add(new Totem(gameObject,
                healingTotemParameters.intValue(ParameterKey.HP),
                healingTotemParameters.intValue(ParameterKey.DAMAGE),
                healingTotemParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                healingTotemParameters.floatValue(ParameterKey.EFFECT_RADIUS),
                TargetMask.GROUND.bit));
        gameObject.addComponent(new Item(gameObject));
        gameObject.setElement(EnumSet.of(ElementType.NATURE,ElementType.WATER));
        gameObject.getComponents().add(new TimedSelfDestroyer(gameObject, healingTotemParameters.intValue(ParameterKey.DURATION)));
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}