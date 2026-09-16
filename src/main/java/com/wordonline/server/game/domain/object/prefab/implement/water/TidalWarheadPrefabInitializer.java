package com.wordonline.server.game.domain.object.prefab.implement.water;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.TidalWarheadProjectile;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("tidal_warhead_prefab")
public class TidalWarheadPrefabInitializer extends PrefabInitializer {

    private static final float COLLIDER_RADIUS = 0.25f;

    private final Parameters parameters;

    @Autowired
    public TidalWarheadPrefabInitializer(Parameters parameters) {
        this(PrefabType.TidalWarhead, parameters);
    }

    protected TidalWarheadPrefabInitializer(PrefabType prefabType, Parameters parameters) {
        super(prefabType);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var warheadParameters = parameters.object(GameObjectKey.TIDAL_WARHEAD);

        gameObject.addCollider(new CircleCollider(gameObject, COLLIDER_RADIUS, true));
        gameObject.setElement(ElementType.WATER);
        gameObject.addComponent(new TidalWarheadProjectile(
                gameObject,
                warheadParameters.intValue(ParameterKey.DAMAGE),
                warheadParameters.floatValue(ParameterKey.SPEED),
                warheadParameters.floatValue(ParameterKey.RADIUS)));
    }
}
