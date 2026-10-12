package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;
import com.wordonline.server.game.util.IntervalTimer;

// The fortress wall golem, a PVE boss that only shows its upper body above the ground. It never
// moves, so it does not use the BehaviorMob state machine (idle, move, attack) at all: it keeps
// two timers and fires two arm attacks against ground enemies.
//
// Slam: every attack_interval it picks the closest ground enemy inside attack_range and marks that
// spot with a warning projection. SLAM_DELAY seconds later the arm lands there and every enemy mob
// within effect_radius of the marked spot takes damage. The spot is fixed when the warning
// starts, so a unit that walks away in that second is not hit.
//
// Sweep: every sub_attack_interval, if a ground enemy is inside sub_attack_range, the other arm
// swings through an arc toward the closest one. Every enemy mob inside the arc takes sub_damage
// and is knocked away from the golem.
//
// The projection type names and durations are a contract with the client, which loads a prefab of
// the same name from Resources/Projectiles.
public class PveBrickGolemMob extends BehaviorMob implements PveObjectiveTarget {

    private static final float STATIONARY_SPEED = 0f;

    static final String SLAM_WARNING_PROJECTILE = "BrickGolemSlamWarning";
    static final String SLAM_PROJECTILE = "BrickGolemSlam";
    static final String SWEEP_PROJECTILE = "BrickGolemSweep";

    static final float SLAM_WARNING_DURATION = 1.0f;
    static final float SLAM_DURATION = 0.3f;
    static final float SWEEP_DURATION = 0.4f;

    /** Seconds between the warning and the damage; the warning projection lasts exactly this long. */
    static final float SLAM_DELAY = SLAM_WARNING_DURATION;

    /** Half of the sweep arc, in degrees. The sweep covers 120 degrees in front of the golem. */
    static final float SWEEP_HALF_ANGLE_DEGREES = 60f;

    /**
     * Knockback strength handed to the effect receiver, which scales a 2 unit push by it. RockGolemMob
     * uses 0.25 for a melee hit; a boss arm swinging through a crowd pushes three times as far.
     */
    static final float SWEEP_KNOCKBACK_STRENGTH = 0.75f;

    private final int damage;
    private final float effectRadius;
    private final int subDamage;
    private final float subAttackInterval;
    private final float subAttackRange;

    private final List<PendingSlam> pendingSlams = new ArrayList<>();
    private float slamTimer;
    private float sweepTimer;

    public PveBrickGolemMob(GameObject gameObject,
                            int maxHp,
                            int damage,
                            float attackInterval,
                            float attackRange,
                            float effectRadius,
                            int subDamage,
                            float subAttackInterval,
                            float subAttackRange) {
        super(gameObject, maxHp, STATIONARY_SPEED, TargetMask.GROUND.bit, attackInterval, attackRange, null);
        this.damage = damage;
        this.effectRadius = effectRadius;
        this.subDamage = subDamage;
        this.subAttackInterval = subAttackInterval;
        this.subAttackRange = subAttackRange;
    }

    @Override
    public void start() {
        // No state is entered: BehaviorMob.start would put the golem into the idle state, which
        // walks toward a target.
        if (attackRange > 0f) {
            gameObject.drawCircle(Vector3.ZERO, attackRange, GizmoCategory.AttackRange);
        }
    }

    // A boss is not stunned, knocked back or ordered around, and every state of the base class
    // ends in a move toward the target. Ignoring them keeps the golem on its spot.
    @Override
    public void setState(State newState) {
    }

    @Override
    public void update() {
        super.update();
        if (isTerminal()) {
            return;
        }

        float deltaTime = getGameContext().getDeltaTime();
        landPendingSlams(deltaTime);

        slamTimer += deltaTime;
        if (slamTimer >= attackInterval.total()) {
            GameObject slamTarget = closestGroundEnemyWithin(attackRange);
            if (slamTarget != null) {
                slamTimer = IntervalTimer.carryOver(slamTimer, attackInterval.total(), deltaTime);
                startSlam(slamTarget);
            }
        }

        sweepTimer += deltaTime;
        if (sweepTimer >= subAttackInterval) {
            GameObject sweepTarget = closestGroundEnemyWithin(subAttackRange);
            if (sweepTarget != null) {
                sweepTimer = IntervalTimer.carryOver(sweepTimer, subAttackInterval, deltaTime);
                sweep(sweepTarget);
            }
        }
    }

    private GameObject closestGroundEnemyWithin(float range) {
        return detector.detect(gameObject, candidate -> CombatRange.contains(gameObject, candidate, range));
    }

    private void startSlam(GameObject slamTarget) {
        Vector3 spot = slamTarget.getPosition().grounded();
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(spot, spot, SLAM_WARNING_PROJECTILE, SLAM_WARNING_DURATION);
        gameObject.setStatus(Status.Attack);
        pendingSlams.add(new PendingSlam(spot, SLAM_DELAY));
    }

    private void landPendingSlams(float deltaTime) {
        Iterator<PendingSlam> iterator = pendingSlams.iterator();
        while (iterator.hasNext()) {
            PendingSlam slam = iterator.next();
            slam.remaining -= deltaTime;
            if (slam.remaining > 0f) {
                continue;
            }
            iterator.remove();
            land(slam.spot);
        }
    }

    private void land(Vector3 spot) {
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(gameObject.getPosition(), spot, SLAM_PROJECTILE, SLAM_DURATION);
        gameObject.setStatus(Status.Attack);

        AttackInfo attackInfo = attackInfo(damage);
        for (Mob mob : enemyMobsAround(spot, effectRadius)) {
            mob.onDamaged(attackInfo);
        }
    }

    private void sweep(GameObject sweepTarget) {
        Vector3 direction = sweepTarget.getPosition().grounded()
                .subtract(gameObject.getPosition().grounded())
                .normalize();
        if (direction.getX() == 0f && direction.getZ() == 0f) {
            direction = Vector3.RIGHT;
        }

        Vector3 end = gameObject.getPosition().grounded().plus(direction.multiply(subAttackRange));
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(gameObject.getPosition(), end, SWEEP_PROJECTILE, SWEEP_DURATION);
        gameObject.setStatus(Status.Attack);

        float minimumCosine = (float) Math.cos(Math.toRadians(SWEEP_HALF_ANGLE_DEGREES));
        AttackInfo attackInfo = attackInfo(subDamage);
        for (Mob mob : enemyMobsAround(gameObject.getPosition(), subAttackRange)) {
            GameObject victim = mob.getGameObject();
            Vector3 toVictim = victim.getPosition().grounded()
                    .subtract(gameObject.getPosition().grounded())
                    .normalize();
            // A victim standing on the golem's centre has no direction; the sweep catches it.
            boolean onCentre = toVictim.getX() == 0f && toVictim.getZ() == 0f;
            if (!onCentre && toVictim.dot(direction) < minimumCosine) {
                continue;
            }

            mob.onDamaged(attackInfo);
            Vector3 push = onCentre ? direction : toVictim;
            victim.getComponentOptional(EffectReceiver.class)
                    .ifPresent(receiver -> receiver.onReceive(Effect.Knockback, push, SWEEP_KNOCKBACK_STRENGTH));
        }
    }

    private List<Mob> enemyMobsAround(Vector3 centre, float radius) {
        return getGameContext().overlapSphereAll(centre, radius).stream()
                .filter(other -> TargetRelation.canAttack(gameObject, other))
                .filter(other -> other.hasComponent(Mob.class))
                .map(other -> other.getComponent(Mob.class))
                .toList();
    }

    private AttackInfo attackInfo(int amount) {
        return new AttackInfo(amount, gameObject.getElement().total()).withAttacker(gameObject);
    }

    @Override
    protected boolean leavesDeathField() {
        return false;
    }

    @Override
    public boolean isTerminal() {
        return gameObject.isDestroyed() || getHp() <= 0;
    }

    private static final class PendingSlam {
        private final Vector3 spot;
        private float remaining;

        private PendingSlam(Vector3 spot, float remaining) {
            this.spot = spot;
            this.remaining = remaining;
        }
    }
}
