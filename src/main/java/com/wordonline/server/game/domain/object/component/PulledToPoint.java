package com.wordonline.server.game.domain.object.component;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;

/**
 * Drags its object in a straight line to a point on the ground plane over {@link #PULL_SECONDS},
 * then destroys it. Height is left to the object's own physics. It is added by the PVE
 * {@code PullPlayerUnits} action, which has already removed the object's mob components, so
 * nothing else moves, attacks, or takes damage while it is dragged.
 */
public class PulledToPoint extends Component {

    public static final float PULL_SECONDS = 1.5f;

    private final Vector3 destination;
    private final float durationSeconds;
    private Vector3 start;
    private float elapsedSeconds;

    public PulledToPoint(GameObject gameObject, Vector3 destination) {
        this(gameObject, destination, PULL_SECONDS);
    }

    public PulledToPoint(GameObject gameObject, Vector3 destination, float durationSeconds) {
        super(gameObject);
        this.destination = destination;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public void start() {
        start = new Vector3(gameObject.getPosition());
    }

    @Override
    public void update() {
        if (gameObject.isDestroyed()) {
            return;
        }
        elapsedSeconds += getGameContext().getDeltaTime();
        float progress = Math.min(1f, elapsedSeconds / durationSeconds);
        float x = start.getX() + (destination.getX() - start.getX()) * progress;
        float z = start.getZ() + (destination.getZ() - start.getZ()) * progress;
        gameObject.setPosition(new Vector3(x, gameObject.getPosition().getY(), z));
        gameObject.applyUpdate();
        if (progress >= 1f) {
            gameObject.destroy();
        }
    }

    @Override
    public void onDestroy() {
    }
}
