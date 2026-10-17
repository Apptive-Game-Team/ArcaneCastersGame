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
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.CraterCarrierMob;
import java.util.EnumSet;

@Component("crater_carrier_prefab")
public class CraterCarrierPrefabInitializer extends PrefabInitializer {
    private final Parameters parameters;

    public CraterCarrierPrefabInitializer(Parameters parameters) {
        super(PrefabType.CraterCarrier);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject object) {
        var stats = parameters.object(GameObjectKey.CRATER_CARRIER);
        object.addComponent(new RigidBody(object, stats.intValue(ParameterKey.MASS)));
        object.addComponent(new ZPhysics(object));
        object.addCollider(new CircleCollider(object, stats.floatValue(ParameterKey.RADIUS), false));
        object.addComponent(new CraterCarrierMob(object, stats.intValue(ParameterKey.HP),
                stats.floatValue(ParameterKey.SPEED), stats.intValue(ParameterKey.DAMAGE),
                stats.floatValue(ParameterKey.ATTACK_INTERVAL), stats.floatValue(ParameterKey.ATTACK_RANGE),
                stats.floatValue(ParameterKey.SETUP_DURATION), stats.floatValue(ParameterKey.RECOVERY_DURATION)));
        object.setElement(EnumSet.of(ElementType.FIRE, ElementType.ROCK));
        object.addComponent(new CommonEffectReceiver(object));
        object.addComponent(new RockDeathRemnant(object));
    }
}
