package com.wordonline.server.game.util;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;

/**
 * The single measure of combat reach. A range is the clear space in front of the source's own
 * collider, not a distance from its centre: a range of 1 means one unit of ground the source can
 * cover, whatever size its body is. The far end is the target's own edge for the same reason, so
 * a range does not have to be inflated to reach a bulky target that the two colliders already
 * hold at arm's length.
 *
 * <p>Whatever places or draws something at a range - a summon point, a debug circle - has to add
 * the source's own radius back with {@link #reachFrom}, or it lands short by exactly that radius
 * and the source spends its attack interval on a target it can never touch.
 *
 * <p>A trigger collider is a hitbox, not a body, so it does not shift the near end: an object that
 * carries only a trigger measures from its centre.
 */
public final class CombatRange {

    private CombatRange() {
    }

    public static boolean contains(GameObject source, GameObject target, double range) {
        return verticalDistance(source, target) <= range
                && horizontalEdgeDistance(source, target) <= range;
    }

    public static double horizontalEdgeDistance(GameObject source, GameObject target) {
        double centerDistance = source.getPosition().grounded()
                .distance(target.getPosition().grounded());
        return Math.max(0d, centerDistance - radiusOf(source) - radiusOf(target));
    }

    public static double verticalDistance(GameObject source, GameObject target) {
        return Math.abs(source.getPosition().getY() - target.getPosition().getY());
    }

    /**
     * How far from the source's centre the given range reaches. Use this to position or draw
     * anything the range check is supposed to cover.
     */
    public static float reachFrom(GameObject source, float range) {
        return radiusOf(source) + range;
    }

    public static float radiusOf(GameObject gameObject) {
        return gameObject.getFirstCircleCollider(false)
                .map(CircleCollider::getRadius)
                .orElse(0f);
    }
}
