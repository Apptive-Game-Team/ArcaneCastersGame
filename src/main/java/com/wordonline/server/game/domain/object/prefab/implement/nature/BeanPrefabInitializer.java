package com.wordonline.server.game.domain.object.prefab.implement.nature;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.BeanProjectile;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("bean_prefab")
public class BeanPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public BeanPrefabInitializer(Parameters parameters) {
        super(PrefabType.Bean);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var beanParameters = parameters.object(GameObjectKey.BEAN);
        gameObject.addCollider(new CircleCollider(gameObject, beanParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.setElement(ElementType.NATURE);
        gameObject.addComponent(new BeanProjectile(gameObject, beanParameters.floatValue(ParameterKey.SPEED)));
    }
}
