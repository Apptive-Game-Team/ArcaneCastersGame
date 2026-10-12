package com.wordonline.server.game.domain.object.prefab.implement.pve;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PveBrickGolemMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

// The fortress wall golem as a PVE boss: it stands where the scenario installed it and fights
// with a ground slam and an arm sweep. It carries no Spawner of its own; the scenario's
// SetSpawner action adds one later if the scenario wants the golem to summon.
@Component("pve_brick_golem_prefab")
public class PveBrickGolemPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public PveBrickGolemPrefabInitializer(Parameters parameters) {
        super(PrefabType.PveBrickGolem);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var golemParameters = parameters.object(GameObjectKey.PVE_BRICK_GOLEM);
        gameObject.addComponent(new RigidBody(gameObject, golemParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        // A trigger collider, like the other PVE bosses: nothing can push the boss off its spot.
        gameObject.addCollider(new CircleCollider(gameObject, golemParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.setElement(EnumSet.of(ElementType.ROCK));
        // Only objects with a Collidable take part in collisions; without the receiver
        // projectiles fly through the boss.
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
        gameObject.addComponent(new PveBrickGolemMob(gameObject,
                golemParameters.intValue(ParameterKey.HP),
                golemParameters.intValue(ParameterKey.DAMAGE),
                golemParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                golemParameters.floatValue(ParameterKey.ATTACK_RANGE),
                golemParameters.floatValue(ParameterKey.EFFECT_RADIUS),
                golemParameters.intValue(ParameterKey.SUB_DAMAGE),
                golemParameters.floatValue(ParameterKey.SUB_ATTACK_INTERVAL),
                golemParameters.floatValue(ParameterKey.SUB_ATTACK_RANGE)));
    }
}
