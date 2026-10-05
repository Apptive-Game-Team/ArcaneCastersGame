package com.wordonline.server.game.domain.object.prefab.implement.pve;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PveEvilEntMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

// The corrupted world tree spirit as a PVE boss: it stands where the scenario installed it and
// keeps the card EvilEnt's punch, grab and fire fist against the player and the player's units.
// It reads the same parameter keys as evil_ent from its own pve_evil_ent object; the two range
// keys must reach across the arena because the boss never walks.
@Component("pve_evil_ent_prefab")
public class PveEvilEntPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public PveEvilEntPrefabInitializer(Parameters parameters) {
        super(PrefabType.PveEvilEnt);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var entParameters = parameters.object(GameObjectKey.PVE_EVIL_ENT);
        gameObject.addComponent(new RigidBody(gameObject, entParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        // A trigger collider, like the other PVE bosses: nothing can push the boss off its spot.
        gameObject.addCollider(new CircleCollider(gameObject, entParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.setElement(EnumSet.of(ElementType.NATURE, ElementType.FIRE));
        // Only objects with a Collidable take part in collisions; without the receiver
        // projectiles fly through the boss.
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
        gameObject.addComponent(new PveEvilEntMob(gameObject,
                entParameters.intValue(ParameterKey.HP),
                TargetMask.ANY.bit,
                entParameters.intValue(ParameterKey.DAMAGE),
                entParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                entParameters.floatValue(ParameterKey.ATTACK_RANGE),
                entParameters.floatValue(ParameterKey.PROJECTILE_SPEED),
                entParameters.intValue(ParameterKey.SUB_DAMAGE),
                entParameters.floatValue(ParameterKey.SUB_ATTACK_RANGE),
                entParameters.floatValue(ParameterKey.SUB_ATTACK_INTERVAL),
                entParameters.floatValue(ParameterKey.PULL_MASS_LIMIT)));
    }
}
