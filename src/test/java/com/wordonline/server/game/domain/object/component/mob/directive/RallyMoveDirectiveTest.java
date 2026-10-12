package com.wordonline.server.game.domain.object.component.mob.directive;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BehaviorMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.dto.Status;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RallyMoveDirectiveTest {

    private static final float ZONE_RADIUS = 3f;
    private static final float MELEE_RANGE = 0.5f;
    private static final float RANGED_RANGE = 5f;

    private GameObject totem() {
        GameObject rallyingTotem = mock(GameObject.class);
        when(rallyingTotem.getStatus()).thenReturn(Status.Idle);
        when(rallyingTotem.getPosition()).thenReturn(Vector3.ZERO);
        return rallyingTotem;
    }

    private GameObject mobWithAttackRange(float attackRange) {
        GameObject mob = mock(GameObject.class);
        BehaviorMob behaviorMob = mock(BehaviorMob.class);
        when(behaviorMob.getAttackRange()).thenReturn(attackRange);
        when(mob.getComponent(BehaviorMob.class)).thenReturn(behaviorMob);
        return mob;
    }

    private GameObject targetAt(float x, float radius) {
        GameObject target = mock(GameObject.class);
        when(target.getPosition()).thenReturn(new Vector3(x, 0f, 0f));
        if (radius > 0f) {
            CircleCollider collider = mock(CircleCollider.class);
            when(collider.getRadius()).thenReturn(radius);
            when(target.getFirstCircleCollider(false)).thenReturn(Optional.of(collider));
        }
        return target;
    }

    @Test
    void arrivalDistanceIsTheZoneRadius() {
        RallyMoveDirective directive = new RallyMoveDirective(mock(GameObject.class), totem(), ZONE_RADIUS);

        assertThat(directive.getArrivalDistance()).isEqualTo(ZONE_RADIUS);
    }

    @Test
    void meleeMobFightsOnlyTargetsItReachesFromTheZoneEdge() {
        GameObject melee = mobWithAttackRange(MELEE_RANGE);
        RallyMoveDirective directive = new RallyMoveDirective(melee, totem(), ZONE_RADIUS);

        assertThat(directive.allowsCombatTarget(melee, targetAt(ZONE_RADIUS + MELEE_RANGE, 0f))).isTrue();
        assertThat(directive.allowsCombatTarget(melee, targetAt(ZONE_RADIUS + MELEE_RANGE + 0.01f, 0f))).isFalse();
    }

    @Test
    void rangedMobFightsTheSameTargetThatAMeleeMobIsRefused() {
        GameObject melee = mobWithAttackRange(MELEE_RANGE);
        GameObject ranged = mobWithAttackRange(RANGED_RANGE);
        GameObject target = targetAt(ZONE_RADIUS + MELEE_RANGE + 1f, 0f);
        RallyMoveDirective directive = new RallyMoveDirective(melee, totem(), ZONE_RADIUS);

        assertThat(directive.allowsCombatTarget(melee, target)).isFalse();
        assertThat(directive.allowsCombatTarget(ranged, target)).isTrue();
    }

    @Test
    void targetRadiusExtendsWhatTheMobCanHitFromTheZone() {
        GameObject melee = mobWithAttackRange(MELEE_RANGE);
        RallyMoveDirective directive = new RallyMoveDirective(melee, totem(), ZONE_RADIUS);

        assertThat(directive.allowsCombatTarget(melee, targetAt(ZONE_RADIUS + MELEE_RANGE + 0.8f, 1f))).isTrue();
    }

    @Test
    void disallowsCombatWhenTheRallyTotemIsDestroyed() {
        GameObject mob = mobWithAttackRange(RANGED_RANGE);
        GameObject rallyingTotem = mock(GameObject.class);
        when(rallyingTotem.getStatus()).thenReturn(Status.Destroyed);
        when(rallyingTotem.getPosition()).thenReturn(Vector3.ZERO);

        RallyMoveDirective directive = new RallyMoveDirective(mob, rallyingTotem, ZONE_RADIUS);

        assertThat(directive.allowsCombatTarget(mob, targetAt(0f, 0f))).isFalse();
    }
}
