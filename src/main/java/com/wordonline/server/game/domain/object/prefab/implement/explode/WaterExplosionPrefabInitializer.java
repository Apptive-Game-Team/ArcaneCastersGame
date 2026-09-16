package com.wordonline.server.game.domain.object.prefab.implement.explode;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.EffectProvider;
import com.wordonline.server.game.domain.object.component.magic.WaterExplode;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;

@Component("water_explosion_prefab")
public class WaterExplosionPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;
    
    public WaterExplosionPrefabInitializer(Parameters parameters) {
        super(PrefabType.WaterExplosion);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var waterExplosionParameters = parameters.object(GameObjectKey.WATER_EXPLOSION);
        gameObject.addCollider(new CircleCollider(
                gameObject,
                waterExplosionParameters.floatValue(ParameterKey.RADIUS),
                true
        ));
        gameObject.setElement(ElementType.WATER);
        gameObject.addComponent(new EffectProvider(gameObject, Effect.Burn));
        gameObject.addComponent(new WaterExplode(
                gameObject,
                waterExplosionParameters.intValue(ParameterKey.DAMAGE),
                waterExplosionParameters.floatValue(ParameterKey.RADIUS),
                waterExplosionParameters.floatValue(ParameterKey.Z_FORCE)
        ));
    }
}
