package com.wordonline.server.game.domain.object.prefab.implement.water;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.PathSpawner;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.SeaSerpentMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

@Component("sea_serpent_prefab")
public class SeaSerpentPrefabInitializer extends PrefabInitializer {

    private static final float WATER_FIELD_SPACING = 1f;

    private final Parameters parameters;

    public SeaSerpentPrefabInitializer(Parameters parameters) {
        super(PrefabType.SeaSerpent);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var seaSerpent = parameters.object(GameObjectKey.SEA_SERPENT);

        gameObject.addComponent(new RigidBody(gameObject, seaSerpent.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(
                gameObject,
                seaSerpent.floatValue(ParameterKey.RADIUS),
                false));
        gameObject.addComponent(new SeaSerpentMob(
                gameObject,
                seaSerpent.intValue(ParameterKey.HP),
                seaSerpent.floatValue(ParameterKey.SPEED),
                TargetMask.ANY.bit,
                seaSerpent.intValue(ParameterKey.DAMAGE),
                seaSerpent.floatValue(ParameterKey.ATTACK_INTERVAL),
                seaSerpent.floatValue(ParameterKey.ATTACK_RANGE),
                seaSerpent.floatValue(ParameterKey.BEAM_WIDTH)));
        gameObject.addComponent(new PathSpawner(gameObject, PrefabType.WaterField, WATER_FIELD_SPACING));
        gameObject.setElement(ElementType.WATER);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
