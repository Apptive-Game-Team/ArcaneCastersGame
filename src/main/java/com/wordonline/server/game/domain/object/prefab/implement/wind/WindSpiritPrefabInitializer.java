package com.wordonline.server.game.domain.object.prefab.implement.wind;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.SelfDestructMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.Slime;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("wind_spirit_prefab")
public class WindSpiritPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public WindSpiritPrefabInitializer(
            Parameters parameters) {
        super(PrefabType.WindSpirit);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var windSpiritParameters = parameters.object(GameObjectKey.WIND_SPIRIT);
        gameObject.addComponent(new RigidBody(gameObject, windSpiritParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject, GameConfig.AERIAL_MOB_INIT_HEIGHT));
        gameObject.addCollider(new CircleCollider(gameObject, windSpiritParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new SelfDestructMob(gameObject,
                windSpiritParameters.intValue(ParameterKey.HP),
                windSpiritParameters.floatValue(ParameterKey.SPEED),
                TargetMask.AIR.bit,
                windSpiritParameters.intValue(ParameterKey.DAMAGE),
                windSpiritParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                windSpiritParameters.floatValue(ParameterKey.ATTACK_RANGE),
                // The spirit hovers until it commits, so it engages on horizontal distance alone.
                true,
                1f));
        gameObject.setElement(ElementType.WIND);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
