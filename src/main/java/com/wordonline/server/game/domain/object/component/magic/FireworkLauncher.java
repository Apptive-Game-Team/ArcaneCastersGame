package com.wordonline.server.game.domain.object.component.magic;

import java.util.ArrayList;
import java.util.List;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

// Fires PrefabType.FireworkShell at a fixed point in front of the tower every
// attackInterval seconds. Unlike CraterEmber, there is no target search and no
// randomness: the impact point is always gameObject.getPosition() + forward *
// attackOffset, where "forward" comes from the same left/right convention
// WindPushComponent uses (there is no facing vector on GameObject).
//
// A launch tick only sends a ProjectileDto so the client can render the flight
// from the tower to the impact point; the FireworkShell GameObject, and the
// explosion its OnStartAttacker fires on start, are created only once that
// flight duration has passed, so the shot lands instead of teleporting. A shot
// still in flight is dropped without creating a shell if the tower is
// destroyed first, since this component is destroyed along with it.
public class FireworkLauncher extends MagicComponent {
    private static final String PROJECTILE_TYPE = "FireworkShell";
    private static final float HORIZONTAL_SPEED = 6.0f;
    private static final float MIN_FLIGHT_DURATION = 0.05f;

    private final float attackInterval;
    private final float attackOffset;
    private float elapsed;
    private final List<InFlightShot> inFlightShots = new ArrayList<>();

    public FireworkLauncher(GameObject gameObject, float attackInterval, float attackOffset) {
        super(gameObject);
        this.attackInterval = Math.max(0.05f, attackInterval);
        this.attackOffset = attackOffset;
    }

    @Override
    public void update() {
        float deltaTime = getGameContext().getDeltaTime();

        // Shots already in flight advance before this tick launches new ones, so a
        // shot launched this tick is never advanced by this same deltaTime too.
        advanceInFlightShots(deltaTime);

        elapsed += deltaTime;
        while (elapsed >= attackInterval) {
            elapsed -= attackInterval;
            launchShell();
        }
    }

    private void advanceInFlightShots(float deltaTime) {
        List<InFlightShot> landed = null;
        for (InFlightShot shot : inFlightShots) {
            shot.remainingTime -= deltaTime;
            if (shot.remainingTime <= 0f) {
                if (landed == null) {
                    landed = new ArrayList<>();
                }
                landed.add(shot);
            }
        }
        if (landed == null) {
            return;
        }
        inFlightShots.removeAll(landed);
        for (InFlightShot shot : landed) {
            new GameObject(gameObject.getMaster(), PrefabType.FireworkShell, shot.impactPosition, getGameContext());
        }
    }

    private void launchShell() {
        Vector3 direction = forwardDirection();
        if (direction == null) {
            // Master.None has no forward side to aim at: skip this tick's shot
            // rather than guess a direction.
            return;
        }

        Vector3 launchPosition = new Vector3(gameObject.getPosition());
        Vector3 impactPosition = launchPosition.plus(direction.multiply(attackOffset));
        float flightDuration = Math.max(MIN_FLIGHT_DURATION, attackOffset / HORIZONTAL_SPEED);

        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(launchPosition, impactPosition, PROJECTILE_TYPE, flightDuration);
        inFlightShots.add(new InFlightShot(impactPosition, flightDuration));
    }

    private Vector3 forwardDirection() {
        Master master = gameObject.getMaster();
        if (master == Master.LeftPlayer) {
            return Vector3.RIGHT;
        }
        if (master == Master.RightPlayer) {
            return Vector3.LEFT;
        }
        return null;
    }

    private static final class InFlightShot {
        private final Vector3 impactPosition;
        private float remainingTime;

        private InFlightShot(Vector3 impactPosition, float remainingTime) {
            this.impactPosition = impactPosition;
            this.remainingTime = remainingTime;
        }
    }
}
