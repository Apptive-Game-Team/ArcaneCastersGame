package com.wordonline.server.game.domain.object.component.physic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Component;

/**
 * Makes an immovable object take part in collision resolution.
 * <p>
 * {@code PhysicSystem} only pairs objects that own at least one {@link Collidable} component,
 * so a bare collider alone never blocks anything. This component adds no collision reaction of
 * its own: without a {@link RigidBody} the object has zero inverse mass, so every impulse is
 * absorbed by the other object.
 */
public class StaticObstacle extends Component implements Collidable {

    public StaticObstacle(GameObject gameObject) {
        super(gameObject);
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
    }

    @Override
    public void onDestroy() {
    }
}
