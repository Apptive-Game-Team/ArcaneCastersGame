package com.wordonline.server.game.domain.object.prefab.implement.build;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.build.RepairAura;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;

@Component("repair_totem_prefab")
public class RepairTotemPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public RepairTotemPrefabInitializer(Parameters parameters) {
        super(PrefabType.RepairTotem);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var repairTotemParameters = parameters.object(GameObjectKey.REPAIR_TOTEM);
        float radius = repairTotemParameters.floatValue(ParameterKey.RADIUS);
        float effectRadius = repairTotemParameters.floatValue(ParameterKey.EFFECT_RADIUS);

        gameObject.addComponent(new RigidBody(gameObject, repairTotemParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, radius, false));
        gameObject.addComponent(new DummyMob(gameObject, repairTotemParameters.intValue(ParameterKey.HP)));
        gameObject.addComponent(new RepairAura(gameObject, effectRadius));
        gameObject.addComponent(new TimedSelfDestroyer(gameObject, repairTotemParameters.floatValue(ParameterKey.DURATION)));
        gameObject.addComponent(new BuildingEffectReceiver(gameObject));
        gameObject.setElement(ElementType.NATURE);
    }
}
