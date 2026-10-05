package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.magic.LimitedSequenceSpawner;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.PveGateKeeperMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

// The gate keeper version of the card's DimensionToad: it stays put, never attacks, and spawns
// the PVE tadpoles in turn. Every value comes from its own pve_* parameter objects.
@Component("pve_dimension_toad_prefab")
public class PveDimensionToadPrefabInitializer extends PrefabInitializer {

    private static final int INFINITE_TADPOLE_SPAWN_COUNT = 0;
    private static final boolean SPAWNS_TADPOLE_ON_SUMMON = true;

    private final Parameters parameters;

    public PveDimensionToadPrefabInitializer(Parameters parameters) {
        super(PrefabType.PveDimensionToad);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var toadParameters = parameters.object(GameObjectKey.PVE_DIMENSION_TOAD);
        gameObject.addComponent(new RigidBody(gameObject, toadParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        // A trigger collider, like the other PVE bosses: nothing can push the keeper off its spot.
        gameObject.addCollider(new CircleCollider(gameObject, toadParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.setElement(EnumSet.of(ElementType.FIRE, ElementType.LIGHTNING));
        // Only objects with a Collidable take part in collisions; without the receiver
        // projectiles fly through the keeper.
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
        // The mob goes on before the spawner so damage and max_hp overrides find it first.
        gameObject.addComponent(new PveGateKeeperMob(gameObject, toadParameters.intValue(ParameterKey.HP)));
        gameObject.addComponent(new LimitedSequenceSpawner(
                gameObject,
                toadParameters.floatValue(ParameterKey.SPAWN_INTERVAL),
                INFINITE_TADPOLE_SPAWN_COUNT,
                SPAWNS_TADPOLE_ON_SUMMON,
                PrefabType.PveFireTadpole,
                PrefabType.PveLightningTadpole));
    }
}
