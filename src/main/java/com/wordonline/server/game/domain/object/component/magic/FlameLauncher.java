package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.DistanceSelfDestroyer;
import com.wordonline.server.game.domain.object.component.IntervalAttacker;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import lombok.Getter;

/**
 * Launches a {@link PrefabType#DragonFlame} straight forward every attack interval and never looks
 * for a target. The flame leaves whether or not anything stands in front of it, explodes on the
 * first enemy it touches, and is destroyed once it has flown {@code attackRange} if it touches
 * nothing.
 *
 * <p>The range lives here rather than on the flame so that one number, dragon_tower.attack_range,
 * is both what the server flies and what the client indicator draws.
 *
 * <p>Forward comes from the owner's side, the same left and right convention
 * {@link com.wordonline.server.game.domain.object.component.build.WindPushComponent} uses, since a
 * GameObject carries no facing vector.
 */
public class FlameLauncher extends MagicComponent implements IntervalAttacker {

    @Getter
    private final Stat attackInterval;
    private final float attackRange;
    private float timer;

    public FlameLauncher(GameObject gameObject, float attackInterval, float attackRange) {
        super(gameObject);
        this.attackInterval = new Stat(attackInterval);
        this.attackRange = attackRange;
    }

    @Override
    public void start() {
        Vector3 forward = forwardDirection();
        if (forward == null) {
            return;
        }

        gameObject.drawBox(
                forward.multiply(attackRange / 2f),
                new Vector3(attackRange, 0f, 0f),
                GizmoCategory.AttackRange);
    }

    @Override
    public void update() {
        timer += getGameContext().getDeltaTime();
        if (timer < attackInterval.total()) {
            return;
        }

        timer = 0f;
        launchFlame();
    }

    private void launchFlame() {
        Vector3 forward = forwardDirection();
        if (forward == null) {
            // Master.None has no forward side to aim at, so there is nothing to launch
            return;
        }

        Vector3 origin = new Vector3(gameObject.getPosition());
        GameObject flame = new GameObject(
                gameObject.getMaster(),
                PrefabType.DragonFlame,
                origin,
                getGameContext());
        flame.getComponent(Shot.class).setTarget(origin.plus(forward.multiply(attackRange)));
        flame.addComponent(new DistanceSelfDestroyer(flame, origin, attackRange));
        gameObject.setStatus(Status.Attack);
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
}
