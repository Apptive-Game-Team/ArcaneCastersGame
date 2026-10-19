package com.wordonline.server.game.domain.object.component.mob.directive;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BehaviorMob;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

import java.util.Optional;

public class RallyMoveDirective extends Component implements MovementDirective {
    private static final int PRIORITY = 100;

    private final GameObject rallyTarget;
    private final float zoneRadius;

    public RallyMoveDirective(GameObject gameObject, GameObject rallyTarget, float zoneRadius) {
        super(gameObject);
        this.rallyTarget = rallyTarget;
        this.zoneRadius = zoneRadius;
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
        if (rallyTarget == null || rallyTarget.getStatus() == Status.Destroyed) {
            gameObject.removeComponent(this);
        }
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Optional<Vector3> getMoveTarget(GameObject self) {
        if (rallyTarget == null || rallyTarget.getStatus() == Status.Destroyed) {
            return Optional.empty();
        }

        return Optional.of(rallyTarget.getPosition());
    }

    @Override
    public float getArrivalDistance() {
        return zoneRadius;
    }

    @Override
    public boolean suppressCombat() {
        return true;
    }

    @Override
    public boolean allowsCombatTarget(GameObject self, GameObject target) {
        if (rallyTarget == null || rallyTarget.getStatus() == Status.Destroyed) {
            return false;
        }

        BehaviorMob mob = self.getComponent(BehaviorMob.class);
        if (mob == null) {
            return false;
        }

        double targetDistanceFromTotem = rallyTarget.getPosition().grounded()
                .distance(target.getPosition().grounded());
        double reachFromZoneEdge = zoneRadius + CombatRange.reachFrom(self, mob.getAttackRange());
        return targetDistanceFromTotem - CombatRange.radiusOf(target) <= reachFromZoneEdge;
    }
}
