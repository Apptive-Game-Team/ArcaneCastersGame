package com.wordonline.server.game.domain.object.prefab.implement.pve;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.MeleeAttackMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

// A ground melee variant of VineSpirit that only PVE scenarios spawn. It has its own parameter
// object, so tuning it never touches the PVP vine_spirit row.
@Component("pve_corrupted_vine_spirit_prefab")
public class PveCorruptedVineSpiritPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public PveCorruptedVineSpiritPrefabInitializer(Parameters parameters) {
        super(PrefabType.PveCorruptedVineSpirit);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var corruptedParameters = parameters.object(GameObjectKey.PVE_CORRUPTED_VINE_SPIRIT);
        gameObject.addComponent(new RigidBody(gameObject, corruptedParameters.intValue(ParameterKey.MASS)));
        gameObject.addComponent(new ZPhysics(gameObject));
        gameObject.addCollider(new CircleCollider(gameObject, corruptedParameters.floatValue(ParameterKey.RADIUS), false));
        gameObject.addComponent(new MeleeAttackMob(gameObject,
                corruptedParameters.intValue(ParameterKey.HP),
                corruptedParameters.floatValue(ParameterKey.SPEED),
                TargetMask.GROUND.bit,
                corruptedParameters.intValue(ParameterKey.DAMAGE),
                corruptedParameters.floatValue(ParameterKey.ATTACK_INTERVAL)));
        gameObject.setElement(ElementType.NATURE);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
