package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.AttackMob;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;

@Component("arcane_infantry_prefab")
public class ArcaneInfantryPrefabInitializer extends PrefabInitializer {
    private final Parameters parameters;

    public ArcaneInfantryPrefabInitializer(Parameters parameters) {
        super(PrefabType.ArcaneInfantry);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject object) {
        var stats = parameters.object(GameObjectKey.ARCANE_INFANTRY);
        object.addComponent(new RigidBody(object, stats.intValue(ParameterKey.MASS)));
        object.addComponent(new ZPhysics(object));
        object.addCollider(new CircleCollider(object, stats.floatValue(ParameterKey.RADIUS), false));
        object.addComponent(new AttackMob(object, stats.intValue(ParameterKey.HP),
                stats.floatValue(ParameterKey.SPEED), TargetMask.GROUND.bit,
                stats.intValue(ParameterKey.DAMAGE), stats.floatValue(ParameterKey.ATTACK_INTERVAL),
                stats.floatValue(ParameterKey.ATTACK_RANGE)));
        object.setElement(ElementType.NONE);
        object.addComponent(new CommonEffectReceiver(object));
    }
}
