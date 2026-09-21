package com.wordonline.server.game.domain.object.component.build;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.TimedMassPush;
import com.wordonline.server.game.dto.Master;

import java.util.List;

public class WindPushComponent extends Component {
    private static final float PUSH_INTERVAL = 0.5f;

    private final float pushForce;
    private final Vector3 boxSize;
    private float pushTimer;

    // pushRangeX/pushRangeZ are ground-plane extents (X-Z); the box is built here so a caller
    // can no longer hand the ground depth to the wrong axis of a raw Vector3.
    public WindPushComponent(GameObject gameObject, float pushForce, float pushRangeX, float pushRangeZ) {
        super(gameObject);
        this.pushForce = pushForce;
        // AERIAL_STANDARD_HEIGHT keeps the box under TargetMask's ground/air split so aerial
        // mobs stay unaffected; no mob ever hovers at exactly that height (aerial mobs hover at
        // AERIAL_MOB_INIT_HEIGHT instead), so the box's inclusive upper bound is not a boundary risk.
        this.boxSize = new Vector3(pushRangeX, GameConfig.AERIAL_STANDARD_HEIGHT, pushRangeZ);
    }

    @Override
    public void start() {
        gameObject.drawBox(centerOffset(direction()), boxSize, GizmoCategory.AreaOfEffect);
    }

    @Override
    public void update() {
        pushTimer -= getGameContext().getDeltaTime();
        if (pushTimer > 0f) return;
        pushTimer = PUSH_INTERVAL;

        Master master = gameObject.getMaster();
        if (master == Master.None) return;

        Vector3 direction = direction();
        Vector3 center = gameObject.getPosition().grounded().plus(centerOffset(direction));

        List<GameObject> targets = getGameContext().getPhysics().overlapBoxAll(center, boxSize);

        for (GameObject target : targets) {
            if (target == gameObject) continue;
            if (target.isDestroyed() || target.getMaster() == master) continue;

            if (!target.hasComponent(Mob.class)) {
                continue;
            }

            // Keep the push alive through the tick in which the next 0.5-second refresh occurs.
            float pushDuration = PUSH_INTERVAL + getGameContext().getDeltaTime();
            TimedMassPush.apply(target, gameObject, direction, pushForce, pushDuration);
        }
    }

    @Override
    public void onDestroy() {
    }

    private Vector3 direction() {
        return (gameObject.getMaster() == Master.LeftPlayer) ? Vector3.RIGHT : Vector3.LEFT;
    }

    // Shared by start()'s gizmo and update()'s overlap check so they can never drift apart:
    // the box sits ground-up (y in [0, boxSize.getY()]) and extends forward from the totem.
    private Vector3 centerOffset(Vector3 direction) {
        return direction.multiply(boxSize.getX() / 2).withY(boxSize.getY() / 2);
    }
}
