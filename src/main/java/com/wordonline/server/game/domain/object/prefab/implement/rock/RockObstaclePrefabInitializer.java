package com.wordonline.server.game.domain.object.prefab.implement.rock;

import org.springframework.stereotype.Component;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.StaticObstacle;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

/**
 * Boulder fixed on the arena for the whole match. It only blocks ground movement and ground
 * summon placement: no movement, no attack, no hp, and no self destruction.
 */
@Component("rock_obstacle_prefab")
public class RockObstaclePrefabInitializer extends PrefabInitializer {

    // The Unity client draws and mirrors this footprint; changing it needs a client change.
    public static final float RADIUS = 0.6f;

    public RockObstaclePrefabInitializer() {
        super(PrefabType.RockObstacle);
    }

    @Override
    public void initialize(GameObject gameObject) {
        gameObject.addCollider(new CircleCollider(gameObject, RADIUS, false));
        gameObject.getComponents().add(new StaticObstacle(gameObject));
        gameObject.setElement(ElementType.ROCK);
    }
}
