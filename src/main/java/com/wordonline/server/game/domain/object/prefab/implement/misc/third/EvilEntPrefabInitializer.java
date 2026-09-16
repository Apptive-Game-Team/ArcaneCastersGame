package com.wordonline.server.game.domain.object.prefab.implement.misc.third;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.EvilEntMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

@Component("evil_ent_prefab")
public class EvilEntPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public EvilEntPrefabInitializer(Parameters parameters) {
        super(PrefabType.EvilEnt);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var evilEntParameters = parameters.object(GameObjectKey.EVIL_ENT);
        gameObject.addComponent(new RigidBody(gameObject, evilEntParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, evilEntParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new EvilEntMob(gameObject,
                evilEntParameters.intValue(ParameterKey.HP),
                evilEntParameters.floatValue(ParameterKey.SPEED),
                TargetMask.ANY.bit,
                evilEntParameters.intValue(ParameterKey.DAMAGE),
                evilEntParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                evilEntParameters.floatValue(ParameterKey.ATTACK_RANGE),
                evilEntParameters.floatValue(ParameterKey.PROJECTILE_SPEED),
                evilEntParameters.intValue(ParameterKey.SUB_DAMAGE),
                evilEntParameters.floatValue(ParameterKey.SUB_ATTACK_RANGE),
                evilEntParameters.floatValue(ParameterKey.SUB_ATTACK_INTERVAL),
                evilEntParameters.floatValue(ParameterKey.PULL_MASS_LIMIT)
        ));

        // A world tree spirit burnt through by the hellfire dimension: it keeps the nature it grew
        // from and carries the fire that dried it out.
        gameObject.setElement(EnumSet.of(ElementType.NATURE, ElementType.FIRE));
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
