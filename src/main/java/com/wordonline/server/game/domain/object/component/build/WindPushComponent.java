package com.wordonline.server.game.domain.object.component.build;

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

    // boxSize.y (push_range_y) stays at 1, well under AERIAL_STANDARD_HEIGHT (2): the box's y
    // range is [0, boxSize.y], so it only ever reaches ground mobs (y=0). Aerial mobs hover at
    // AERIAL_MOB_INIT_HEIGHT (3) via ZPhysics and are never inside that range. Raising
    // push_range_y towards or past AERIAL_STANDARD_HEIGHT would start pulling aerial mobs in.
    public WindPushComponent(GameObject gameObject, float pushForce, Vector3 boxSize) {
        super(gameObject);
        this.pushForce = pushForce;
        this.boxSize = boxSize;
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
