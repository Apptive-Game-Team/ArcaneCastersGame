package com.wordonline.server.game.domain.object.prefab.implement.lightning;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.AllyOverchargeProvider;
import com.wordonline.server.game.domain.object.component.effect.EffectProvider;
import com.wordonline.server.game.domain.object.component.magic.Shot;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import org.springframework.stereotype.Component;

@Component("electric_shot_prefab")
public class ElectricShotPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public ElectricShotPrefabInitializer(Parameters parameters) {
        super(PrefabType.ElectricShot);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var shootParameters = parameters.object(GameObjectKey.ELECTRIC_SHOT);
        gameObject.addCollider(new CircleCollider(gameObject, shootParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.setElement(ElementType.LIGHTNING);
        gameObject.getComponents().add(new EffectProvider(gameObject, Effect.Shock));
        gameObject.getComponents().add(new AllyOverchargeProvider(gameObject));
        gameObject.getComponents().add(new Shot(gameObject,
                shootParameters.intValue(ParameterKey.DAMAGE),
                shootParameters.floatValue(ParameterKey.SPEED)
        ));
    }
}
