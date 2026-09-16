package com.wordonline.server.game.domain.object.prefab.implement.fire;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.EffectProvider;
import com.wordonline.server.game.domain.object.component.magic.Shot;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Effect;
import org.springframework.stereotype.Component;

/**
 * The projectile a dragon_tower launches. Built like fire_shot, which is the same kind of object:
 * a trigger collider carrying a {@link Shot}, which flies the direction it is given, explodes on
 * the first enemy it touches, and is destroyed by the field bounds when it touches nothing.
 *
 * <p>It burns what it hits, the same as fire_shot.
 */
@Component("dragon_flame_prefab")
public class DragonFlamePrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public DragonFlamePrefabInitializer(Parameters parameters) {
        super(PrefabType.DragonFlame);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var dragonFlameParameters = parameters.object(GameObjectKey.DRAGON_FLAME);
        gameObject.addCollider(new CircleCollider(gameObject, dragonFlameParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.setElement(ElementType.FIRE);
        gameObject.getComponents().add(new EffectProvider(gameObject, Effect.Burn));
        gameObject.getComponents().add(new Shot(
                gameObject,
                dragonFlameParameters.intValue(ParameterKey.DAMAGE),
                dragonFlameParameters.floatValue(ParameterKey.SPEED)
        ));
    }
}
