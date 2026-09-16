package com.wordonline.server.game.domain.object.component;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;

/**
 * Destroys the object once it has travelled a set distance from where it was launched, the way
 * {@link TimedSelfDestroyer} destroys it once a set time has passed.
 *
 * <p>The origin is handed in rather than read in {@link #start()}: a component attached to an
 * object that is already alive starts on the next tick, by which time the object has moved.
 */
public class DistanceSelfDestroyer extends Component {

    private final Vector3 origin;
    private final float maxDistance;

    public DistanceSelfDestroyer(GameObject gameObject, Vector3 origin, float maxDistance) {
        super(gameObject);
        this.origin = new Vector3(origin);
        this.maxDistance = maxDistance;
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
        if (gameObject.getPosition().distance(origin) >= maxDistance) {
            gameObject.destroy();
        }
    }

    @Override
    public void onDestroy() {
    }
}
