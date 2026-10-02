package com.wordonline.server.game.domain.object.prefab.implement.drop;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;

@Component("rock_drop_prefab")
public class RockDropPrefabInitializer extends AbstractDropPrefabInitializer {

    public RockDropPrefabInitializer(Parameters parameters) {
        super(ElementType.ROCK, parameters);
    }

    @Override
    public void initialize(GameObject gameObject) {
        super.initialize(gameObject);
    }
}
