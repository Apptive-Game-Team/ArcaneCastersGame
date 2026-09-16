package com.wordonline.server.game.domain.object.prefab.implement.rock;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.BoulderStrikeShot;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.springframework.stereotype.Component;

@Component("boulder_strike_prefab")
public class BoulderStrikePrefabInitializer extends PrefabInitializer {

    private static final float KNOCKBACK_DURATION = 0.65f;

    private final Parameters parameters;

    public BoulderStrikePrefabInitializer(Parameters parameters) {
        super(PrefabType.BoulderStrike);
        this.parameters = parameters;
    }

    @Override
    public void initialize(GameObject gameObject) {
        var strikeParameters = parameters.object(GameObjectKey.BOULDER_STRIKE);

        gameObject.addCollider(new CircleCollider(
                gameObject,
                strikeParameters.floatValue(ParameterKey.RADIUS),
                true));
        gameObject.setElement(ElementType.ROCK);
        gameObject.setElement(ElementType.WIND);
        gameObject.addComponent(new BoulderStrikeShot(
                gameObject,
                strikeParameters.intValue(ParameterKey.DAMAGE),
                strikeParameters.floatValue(ParameterKey.SPEED),
                strikeParameters.intValue(ParameterKey.SUB_DAMAGE),
                strikeParameters.floatValue(ParameterKey.PUSH_FORCE),
                KNOCKBACK_DURATION));
    }
}
