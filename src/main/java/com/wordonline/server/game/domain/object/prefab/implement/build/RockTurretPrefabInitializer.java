package com.wordonline.server.game.domain.object.prefab.implement.build;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.simple.Turret;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("rock_turret_prefab")
public class RockTurretPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public RockTurretPrefabInitializer(Parameters parameters) {
        super(PrefabType.RockTurret);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var rockTurretParameters = parameters.object(GameObjectKey.ROCK_TURRET);
        int hp = rockTurretParameters.intValue(ParameterKey.HP);
        gameObject.getComponents().add(new RigidBody(gameObject, rockTurretParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, rockTurretParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.getComponents().add(new Turret(gameObject,
                hp,
                rockTurretParameters.intValue(ParameterKey.DAMAGE),
                TargetMask.GROUND.bit,
                0.2f,
                rockTurretParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                rockTurretParameters.floatValue(ParameterKey.ATTACK_RANGE)
        ));
        gameObject.addComponent(new TimedSelfDestroyer(
                gameObject,
                rockTurretParameters.floatValue(ParameterKey.DURATION)
        ));
        gameObject.addComponent(new RockDeathRemnant(gameObject));
        gameObject.setElement(ElementType.ROCK);
        gameObject.getComponents().add(new CommonEffectReceiver(gameObject));
    }
}
