package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

public class SeaSerpentMob extends BehaviorMob {

    static final String HYDRO_PUMP_PROJECTILE = "SeaSerpentHydroPump";
    static final float HYDRO_PUMP_DURATION = 0.35f;
    static final float HYDRO_PUMP_MUZZLE_HEIGHT = 1.6f;

    public SeaSerpentMob(GameObject gameObject,
                         int maxHp,
                         float speed,
                         int targetMask,
                         int damage,
                         float attackInterval,
                         float attackRange,
                         float beamWidth) {
        super(
                gameObject,
                maxHp,
                speed,
                targetMask,
                attackInterval,
                attackRange,
                target -> fireHydroPump(gameObject, target, damage, attackRange, beamWidth),
                true);
    }

    static boolean fireHydroPump(GameObject seaSerpent,
                                 GameObject primaryTarget,
                                 int damage,
                                 float attackRange,
                                 float beamWidth) {
        Vector3 groundOrigin = seaSerpent.getPosition().grounded();
        Vector3 direction = primaryTarget.getPosition().grounded().subtract(groundOrigin).normalize();
        if (direction.equals(Vector3.ZERO)) {
            return false;
        }

        float beamLength = CombatRange.reachFrom(seaSerpent, attackRange);
        Vector3 groundEnd = groundOrigin.plus(direction.multiply(beamLength));
        Vector3 visualOrigin = seaSerpent.getPosition().plus(0f, HYDRO_PUMP_MUZZLE_HEIGHT, 0f);
        Vector3 visualEnd = groundEnd.withY(primaryTarget.getPosition().getY());
        AttackInfo attackInfo = new AttackInfo(damage, seaSerpent.getElement().total())
                .withAttacker(seaSerpent);

        for (GameObject candidate : seaSerpent.getGameContext().getActiveGameObjects()) {
            if (!TargetRelation.canAttack(seaSerpent, candidate)) {
                continue;
            }

            Damageable damageable = candidate.getComponent(Damageable.class);
            if (damageable == null) {
                continue;
            }

            float targetRadius = candidate.getFirstCircleCollider()
                    .map(collider -> collider.getRadius())
                    .orElse(0f);
            if (distanceToSegment(candidate.getPosition().grounded(), groundOrigin, groundEnd)
                    > beamWidth + targetRadius) {
                continue;
            }

            damageable.onDamaged(attackInfo, HYDRO_PUMP_DURATION);
        }

        seaSerpent.getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(visualOrigin, visualEnd, HYDRO_PUMP_PROJECTILE, HYDRO_PUMP_DURATION);
        seaSerpent.setStatus(Status.Attack);
        return true;
    }

    static double distanceToSegment(Vector3 point, Vector3 start, Vector3 end) {
        Vector3 segment = end.subtract(start);
        float lengthSquared = segment.dot(segment);
        if (lengthSquared == 0f) {
            return point.distance(start);
        }

        float progress = Math.clamp(point.subtract(start).dot(segment) / lengthSquared, 0f, 1f);
        Vector3 closest = start.plus(segment.multiply(progress));
        return point.distance(closest);
    }
}
