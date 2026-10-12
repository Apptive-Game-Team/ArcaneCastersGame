package com.wordonline.server.game.domain.object.component.physic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Component;

/**
 * Marks a river cell whose {@link EdgeCollider}s keep ground bodies out of the water.
 * <p>
 * It implements {@link Collidable} only because the physics broad phase ignores objects without a
 * collision handler; every method is empty. {@code PhysicSystem} treats an object carrying it
 * differently from a map wall: it tests the cell only against ground bodies (alive, not dying,
 * with hp, below {@code GameConfig.AERIAL_STANDARD_HEIGHT}), so flyers, projectiles, drops,
 * explosions and magic bodies pass over the water untouched, and it never calls the collision
 * handlers of either side, so the cell neither damages nor triggers anything.
 */
public class WaterBarrier extends Component implements Collidable {

    public WaterBarrier(GameObject gameObject) {
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
