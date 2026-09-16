package com.wordonline.server.game.domain.object.prefab.implement.build;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.build.WindPushComponent;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

@Component("wind_totem_prefab")
public class WindTotemPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;
    
    public WindTotemPrefabInitializer(Parameters parameters) {
        super(PrefabType.WindTotem);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var windTotemParameters = parameters.object(GameObjectKey.WIND_TOTEM);
        gameObject.addComponent(new RigidBody(gameObject, windTotemParameters.intValue(ParameterKey.MASS)));
        gameObject.addCollider(new CircleCollider(gameObject, windTotemParameters.floatValue(ParameterKey.RADIUS), false));

        gameObject.addComponent(new DummyMob(gameObject, windTotemParameters.intValue(ParameterKey.HP)));
        gameObject.addComponent(new WindPushComponent(
                gameObject,
                windTotemParameters.floatValue(ParameterKey.PUSH_FORCE),
                new Vector3(
                        windTotemParameters.floatValue(ParameterKey.PUSH_RANGE_X),
                        windTotemParameters.floatValue(ParameterKey.PUSH_RANGE_Y),
                        1.0f
                )));

        gameObject.addComponent(new TimedSelfDestroyer(
                gameObject,
                (int)(windTotemParameters.doubleValue(ParameterKey.HP) * windTotemParameters.doubleValue(ParameterKey.ATTACK_INTERVAL) / windTotemParameters.doubleValue(ParameterKey.DAMAGE))
        ));
        gameObject.setElement(ElementType.WIND);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
    }
}
