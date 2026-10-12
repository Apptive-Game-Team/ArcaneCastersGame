package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.SelfDestructMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

// An aerial self-destruct variant of WindSpirit that only PVE scenarios spawn. Unlike the player's
// WindSpirit, which only dives at air targets, it targets ground and air alike: the player's
// summons and master stand on the ground, so an air-only mask would leave it harmless.
@Component("pve_corrupted_wind_spirit_prefab")
public class PveCorruptedWindSpiritPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public PveCorruptedWindSpiritPrefabInitializer(Parameters parameters) {
        super(PrefabType.PveCorruptedWindSpirit);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var corruptedParameters = parameters.object(GameObjectKey.PVE_CORRUPTED_WIND_SPIRIT);
        gameObject.addComponent(new RigidBody(gameObject, corruptedParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject, GameConfig.AERIAL_MOB_INIT_HEIGHT));
        gameObject.addCollider(new CircleCollider(gameObject, corruptedParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new SelfDestructMob(gameObject,
                corruptedParameters.intValue(ParameterKey.HP),
                corruptedParameters.floatValue(ParameterKey.SPEED),
                TargetMask.ANY.bit,
                corruptedParameters.intValue(ParameterKey.DAMAGE),
                corruptedParameters.floatValue(ParameterKey.ATTACK_INTERVAL),
                corruptedParameters.floatValue(ParameterKey.ATTACK_RANGE),
                true,
                1f));
        gameObject.setElement(ElementType.WIND);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
