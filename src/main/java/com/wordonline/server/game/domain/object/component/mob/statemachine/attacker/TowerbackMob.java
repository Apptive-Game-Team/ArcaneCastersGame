package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.ClosestEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

import java.util.List;
import java.util.function.Predicate;

/**
 * A walking anti-air tower. It holds one target at a time, picks an air enemy over a ground one,
 * walks until the target sits inside the range of the weapon that target calls for, then stands
 * and fires. An air target that flies off is followed, but only while it stays within
 * {@link #AIR_CHASE_RANGE_MULTIPLIER} times the air range, so a fast flyer cannot drag the tower
 * across the map.
 */
public class TowerbackMob extends BehaviorMob {

    static final float AIR_CHASE_RANGE_MULTIPLIER = 1.5f;
    private static final float AIR_SHOT_DURATION = 0.2f;
    private static final float AIR_SHOT_SPLASH_RADIUS = 1f;

    private final float airAttackRange;
    private final float airChaseRange;
    private final int airDamage;
    private final Detector airDetector;
    private float airCheckTimer;

    public TowerbackMob(GameObject gameObject, int maxHp, float speed,
            int groundDamage, float groundAttackRange,
            int airDamage, float airAttackRange,
            float attackInterval) {
        super(gameObject, maxHp, speed, TargetMask.ANY.bit, attackInterval, groundAttackRange, null);
        this.airAttackRange = airAttackRange;
        this.airChaseRange = airAttackRange * AIR_CHASE_RANGE_MULTIPLIER;
        this.airDamage = airDamage;
        this.airDetector = new ClosestEnemyDetector(getGameContext(), TargetMask.AIR.bit);
        this.detector = new AirFirstDetector();
        setBehavior(target -> isAir(target) ? shootAir(target) : strikeGround(target, groundDamage));
    }

    @Override
    public void start() {
        super.start();
        gameObject.drawCircle(Vector3.ZERO, CombatRange.reachFrom(gameObject, airAttackRange), GizmoCategory.AttackRange);
    }

    /**
     * The attack state never re-detects, so a tower trading blows with a ground unit would ignore a
     * flyer passing overhead. Check for one on the detection interval and break off to chase it.
     */
    @Override
    public void update() {
        airCheckTimer += getGameContext().getDeltaTime();
        if (airCheckTimer > Detector.DETECTING_INTERVAL) {
            airCheckTimer = 0f;
            switchToAirTarget();
        }
        super.update();
    }

    private void switchToAirTarget() {
        if (!(currentState instanceof AttackState) || target == null || isAir(target)) {
            return;
        }
        GameObject air = airDetector.detect(gameObject, this::withinAirChaseRange);
        if (air == null) {
            return;
        }
        target = air;
        targetRadius = CombatRange.radiusOf(air);
        setState(new MoveState());
    }

    @Override
    protected float attackRangeFor(GameObject target) {
        return isAir(target) ? airAttackRange : attackRange;
    }

    /**
     * An air target past the chase range stops being a target, which sends the move or attack
     * state back to idle instead of walking after it.
     */
    @Override
    protected boolean isValidTarget(GameObject target) {
        if (!super.isValidTarget(target)) {
            return false;
        }
        return !isAir(target) || withinAirChaseRange(target);
    }

    private boolean isAir(GameObject target) {
        return TargetMask.of(target) == TargetMask.AIR.bit;
    }

    private boolean withinAirChaseRange(GameObject target) {
        return CombatRange.horizontalEdgeDistance(gameObject, target) <= airChaseRange;
    }

    private boolean strikeGround(GameObject target, int damage) {
        Damageable damageable = target.getComponent(Damageable.class);
        if (damageable == null) {
            return false;
        }

        damageable.onDamaged(new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject));
        gameObject.setStatus(Status.Attack);
        return true;
    }

    private boolean shootAir(GameObject target) {
        if (target.getComponent(Damageable.class) == null) {
            return false;
        }

        List<GameObject> victims = getGameContext().overlapSphereAll(target, AIR_SHOT_SPLASH_RADIUS);
        for (GameObject victim : victims) {
            if (!TargetRelation.canAttack(gameObject, victim)) {
                continue;
            }
            Damageable damageable = victim.getComponent(Damageable.class);
            if (damageable != null) {
                damageable.onDamaged(airAttackInfo(), AIR_SHOT_DURATION);
            }
        }
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(gameObject, target, "RockShot", AIR_SHOT_DURATION);
        gameObject.setStatus(Status.Attack);
        return true;
    }

    private AttackInfo airAttackInfo() {
        return new AttackInfo(airDamage, gameObject.getElement().total()).withAttacker(gameObject);
    }

    /**
     * The closest air enemy inside the chase range, or the closest ground enemy when there is none.
     * The move state re-detects every interval, so this ordering is also what pulls the tower off a
     * ground target the moment a flyer comes within reach, and what keeps it on a flyer rather than
     * a nearer ground unit.
     */
    private class AirFirstDetector implements Detector {
        private final Detector groundDetector = new ClosestEnemyDetector(getGameContext(), TargetMask.GROUND.bit);

        @Override
        public GameObject detect(GameObject self) {
            return detect(self, candidate -> true);
        }

        @Override
        public GameObject detect(GameObject self, Predicate<GameObject> filter) {
            GameObject air = airDetector.detect(self, candidate -> withinAirChaseRange(candidate) && filter.test(candidate));
            if (air != null) {
                return air;
            }
            return groundDetector.detect(self, filter);
        }
    }
}
