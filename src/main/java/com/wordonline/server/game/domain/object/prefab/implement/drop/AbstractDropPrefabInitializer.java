package com.wordonline.server.game.domain.object.prefab.implement.drop;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.Drop;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

public abstract class AbstractDropPrefabInitializer extends PrefabInitializer {

    private final Parameters parameters;
    private final ElementType elementType;

    public AbstractDropPrefabInitializer(
            ElementType elementType, Parameters parameters) {
        super(getPrefabType(elementType));
        this.elementType = elementType;
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var dropParameters = parameters.object(getGameObjectKey(elementType));
        gameObject.addCollider(new CircleCollider(gameObject, dropParameters.floatValue(ParameterKey.RADIUS), true));
        gameObject.setElement(elementType);
        gameObject.getComponents().add(new Drop(gameObject, dropParameters.intValue(ParameterKey.DAMAGE)));
    }

    private static GameObjectKey getGameObjectKey(ElementType elementType) {
        return switch (elementType) {
            case FIRE -> GameObjectKey.FIRE_DROP;
            case NATURE -> GameObjectKey.NATURE_DROP;
            case ROCK -> GameObjectKey.ROCK_DROP;
            case WIND -> GameObjectKey.WIND_DROP;
            default -> GameObjectKey.DROP;
        };
    }

    private static PrefabType getPrefabType(ElementType elementType) {
        return switch (elementType) {
            case ElementType.FIRE -> PrefabType.FireDrop;
            case ElementType.NATURE -> PrefabType.NatureDrop;
            case ElementType.ROCK -> PrefabType.RockDrop;
            case ElementType.WIND -> PrefabType.WindDrop;
            default -> null;
        };
    }
}
