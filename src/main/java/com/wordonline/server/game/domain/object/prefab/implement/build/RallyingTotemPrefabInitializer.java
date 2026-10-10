package com.wordonline.server.game.domain.object.prefab.implement.build;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.component.Item;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.RallyingTotem;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import org.springframework.stereotype.Component;

@Component("rallying_totem_prefab")
public class RallyingTotemPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;

    public RallyingTotemPrefabInitializer(Parameters parameters) {
        super(PrefabType.RallyingTotem);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var rallyingTotemParameters = parameters.object(GameObjectKey.RALLYING_TOTEM);
        gameObject.addCollider(new CircleCollider(gameObject, rallyingTotemParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.addComponent(new Item(gameObject));
        gameObject.setElement(ElementType.FIRE);
        gameObject.addComponent(new RallyingTotem(
                gameObject,
                rallyingTotemParameters.floatValue(ParameterKey.DURATION),
                rallyingTotemParameters.floatValue(ParameterKey.RADIUS),
                rallyingTotemParameters.floatValue(ParameterKey.BUFF_DURATION),
                rallyingTotemParameters.floatValue(ParameterKey.EFFECT_RADIUS)));
    }
}
