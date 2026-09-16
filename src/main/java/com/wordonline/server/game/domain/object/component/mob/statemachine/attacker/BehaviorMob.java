package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.IntervalAttacker;
import com.wordonline.server.game.domain.object.component.mob.detector.ClosestEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.component.mob.directive.MovementDirective;
import com.wordonline.server.game.domain.object.component.mob.pathfinder.PathFinder;
import com.wordonline.server.game.domain.object.component.mob.pathfinder.SimplePathFinder;
import com.wordonline.server.game.domain.object.component.mob.statemachine.StateMachineMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
public class BehaviorMob extends StateMachineMob implements IntervalAttacker {

    private static final float DIVE_ALTITUDE_EPSILON = 1e-4f;

    PathFinder pathFinder;
    Detector detector;
    GameObject target = null;
    float targetRadius;
    RigidBody rigidBody;
    @Getter Stat attackInterval;
    protected float attackRange;
    private final int targetMask;
    /**
     * Whether engagement ignores the vertical gap to the target. A mob that dives from a hover
     * height cannot close that gap before it commits, so testing it against the attack range would
     * leave it circling above ground targets forever. It stays false for ground mobs, where the
     * full cylindrical check costs nothing because both sides sit at y = 0.
     */
    private final boolean verticalRangeIgnored;
    private Master observedMaster;
    @Setter
    Predicate<GameObject> behavior = null;

    @Override
    public void onDeath() {
        gameObject.destroy();
    }

    @Override
    public void start() {
        observedMaster = gameObject.getMaster();
        setState(new IdleState());
        rigidBody = gameObject.getComponent(RigidBody.class);
        if (attackRange > 0f) {
            gameObject.drawCircle(Vector3.ZERO, CombatRange.reachFrom(gameObject, attackRange), GizmoCategory.AttackRange);
        }
    }

    public BehaviorMob(GameObject gameObject, int maxHp, float speed, int targetMask, float attackInterval, float attackRange, Predicate<GameObject> behavior) {
        this(gameObject, maxHp, speed, targetMask, attackInterval, attackRange, behavior, false);
    }

    public BehaviorMob(GameObject gameObject, int maxHp, float speed, int targetMask, float attackInterval, float attackRange, Predicate<GameObject> behavior, boolean verticalRangeIgnored) {
        super(gameObject, maxHp, speed);
        this.pathFinder = new SimplePathFinder();
        this.detector = new ClosestEnemyDetector(getGameContext(), targetMask);
        this.targetMask = targetMask;
        this.attackInterval = new Stat(attackInterval);
        this.attackRange = attackRange;
        this.behavior = behavior;
        this.verticalRangeIgnored = verticalRangeIgnored;
    }

    public void setStun(float duration)
    {
        setState(new StunState(duration));
    }
    public void setIdle()
    {
        setState(new IdleState());
    }

    protected void resetTarget() {
        target = null;
        targetRadius = 0f;
    }

    protected boolean isValidTarget(GameObject target) {
        return target != null
                && target.getStatus() != Status.Destroyed
                && TargetRelation.canAttack(gameObject, target)
                && (TargetMask.of(target) & targetMask) != 0;
    }

    /**
     * The directive with the highest priority that currently has a move target, with ties going to
     * the one added first. This runs every frame for every mob and the component list is usually
     * empty, so it is a plain scan: the stream it replaces sorted the whole list before filtering,
     * and sorted() buffers even for zero or one element.
     *
     * <p>The scan reaches getMoveTarget on a different set of directives than the sort did, which
     * is safe because the implementations are pure reads - RallyMoveDirective only inspects its
     * rally target's status and position. The winner is unchanged: a candidate is only asked for a
     * target once it beats the best priority seen so far, and an equal priority never displaces an
     * earlier candidate, which is what the stable sort followed by findFirst did.
     */
    MovementDirective resolveHighestPriorityDirective() {
        List<MovementDirective> directives = gameObject.getComponents(MovementDirective.class);
        MovementDirective best = null;
        for (int i = 0; i < directives.size(); i++) {
            MovementDirective candidate = directives.get(i);
            if (best != null && candidate.priority() <= best.priority()) {
                continue;
            }
            if (candidate.getMoveTarget(gameObject).isEmpty()) {
                continue;
            }
            best = candidate;
        }
        return best;
    }

    private Optional<MovementDirective> resolveMovementDirective() {
        return Optional.ofNullable(resolveHighestPriorityDirective());
    }

    @Override
    public void update() {
        if (observedMaster == null) {
            observedMaster = gameObject.getMaster();
        } else if (observedMaster != gameObject.getMaster()) {
            observedMaster = gameObject.getMaster();
            resetTarget();
            setState(new IdleState());
        }

        Optional<MovementDirective> directive = resolveMovementDirective();
        if (directive.isPresent() && directive.get().suppressCombat()) {
            MovementDirective activeDirective = directive.get();
            GameObject directiveCombatTarget = detector.detect(
                    gameObject,
                    candidate -> activeDirective.allowsCombatTarget(gameObject, candidate));

            if (directiveCombatTarget != null) {
                boolean shouldEnterCombat = target != directiveCombatTarget
                        || !(currentState instanceof MoveState || currentState instanceof AttackState);
                if (shouldEnterCombat) {
                    target = directiveCombatTarget;
                    targetRadius = target.getFirstCircleCollider().get().getRadius();
                    setState(new MoveState());
                }
            } else if (!(currentState instanceof DirectiveMoveState)) {
                resetTarget();
                setState(new DirectiveMoveState(activeDirective));
            }
        }

        super.update();
    }

    /**
     * Whether the current target sits within {@code range}. See {@link #verticalRangeIgnored} for
     * why aerial mobs drop the vertical term of the check.
     */
    protected boolean withinAttackRange(float range) {
        if (verticalRangeIgnored) {
            return CombatRange.horizontalEdgeDistance(gameObject, target) <= range;
        }
        return CombatRange.contains(gameObject, target, range);
    }

    /**
     * Progress of a dive that started at {@code startPos} and ends at {@code targetY}, in the
     * range Vector3.lerp accepts. A target at the diver's own altitude leaves nothing to descend,
     * and dividing by that zero gap yields NaN, which {@code Math.clamp} does not filter out.
     */
    protected float diveProgress(Vector3 startPos, float targetY) {
        float altitudeGap = targetY - startPos.getY();
        if (Math.abs(altitudeGap) <= DIVE_ALTITUDE_EPSILON) {
            return 1f;
        }

        return (gameObject.getPosition().getY() - startPos.getY()) / altitudeGap;
    }

    public class StunState extends State {
        private final float duration;
        float timer;
        public StunState(float duration) {
            this.duration = duration;
        }
        @Override
        public void onEnter() {

        }

        @Override
        public void onExit() {

        }

        @Override
        public void onUpdate() {
            timer += getGameContext().getDeltaTime();
            if (timer > duration) {
                setState(new IdleState());
            }

        }
    }

    public class IdleState extends State {
        float timer;
        @Override
        public void onEnter() {
            timer = Detector.DETECTING_INTERVAL + 1;
        }

        @Override
        public void onExit() {
        }

        @Override
        public void onUpdate() {
            timer += getGameContext().getDeltaTime();
            if (timer > Detector.DETECTING_INTERVAL) {
                target = detector.detect(gameObject);
                if (target != null) {
                    targetRadius = target.getFirstCircleCollider().get().getRadius();
                    setState(new MoveState());
                    return;
                }
                timer = 0;
            }
        }
    }

    public class MoveState extends State {
        float timer;
        List<Vector3> path;
        @Override
        public void onEnter() {
            if (!isValidTarget(target)) {
                resetTarget();
                setState(new IdleState());
                return;
            }
            path = pathFinder.findPath(gameObject.getPosition().grounded(), target.getPosition().grounded());
        }

        @Override
        public void onExit() {

        }

        @Override
        public void onUpdate() {
            if (!isValidTarget(target) || path == null || path.isEmpty()) {
                resetTarget();
                setState(new IdleState());
                return;
            }

            // Range is checked before the path bookkeeping: a mob that walks onto the last path
            // point would otherwise drop back to idle without ever testing whether it can attack.
            if (withinAttackRange(Math.max(0f, attackRange - 0.1f))) {
                setState(new AttackState());
                return;
            }

            log.trace("State : {}", currentState);
            Vector3 currentPosition = gameObject.getPosition().grounded();
            log.trace("Path Remain Distance : {}",currentPosition.distance(path.get(0)));
            log.trace("Target Edge Distance : {}", CombatRange.horizontalEdgeDistance(gameObject, target));
            // Check if we reached the next path point
            if (currentPosition.distance(path.get(0)) < PathFinder.REACH_THRESHOLD) {
                path.remove(0);
                if (path.isEmpty()) {
                    setState(new IdleState());
                    return;
                }
            }
          
            timer += getGameContext().getDeltaTime();
            if (timer > Detector.DETECTING_INTERVAL) {
                GameObject newTarget = detector.detect(gameObject);
                if (newTarget != null && newTarget != target) {
                    target = newTarget;
                    targetRadius = newTarget.getFirstCircleCollider().get().getRadius();
                    path = pathFinder.findPath(gameObject.getPosition().grounded(), target.getPosition().grounded());
                    if (path.isEmpty()) return;
                }
                timer = 0f;
            }

            Vector3 nextPoint = path.get(0);
            Vector3 direction = nextPoint.subtract(currentPosition).grounded().normalize();

            Vector3 velocity = direction.multiply(speed.total());

            if (rigidBody == null) {
                log.warn("[MobMoveSkipped] {} has no RigidBody; skipping move update", gameObject.getType());
                setState(new IdleState());
                return;
            }

            rigidBody.addVelocity(velocity);
        }
    }

    public class DirectiveMoveState extends State {
        private MovementDirective directive;
        private Vector3 destination;
        private List<Vector3> path;
        private float timer;

        public DirectiveMoveState(MovementDirective directive) {
            this.directive = directive;
        }

        @Override
        public void onEnter() {
            updateDestination();
        }

        @Override
        public void onExit() {
        }

        @Override
        public void onUpdate() {
            timer += getGameContext().getDeltaTime();
            if (timer > Detector.DETECTING_INTERVAL) {
                Optional<MovementDirective> latestDirective = resolveMovementDirective();
                if (latestDirective.isEmpty() || !latestDirective.get().suppressCombat()) {
                    setState(new IdleState());
                    return;
                }

                directive = latestDirective.get();
                updateDestination();
                timer = 0f;
            }

            if (destination == null) {
                setState(new IdleState());
                return;
            }

            if (gameObject.getPosition().distance(destination) <= directive.getArrivalDistance()) {
                return;
            }

            if (path == null || path.isEmpty()) {
                setState(new IdleState());
                return;
            }

            Vector3 currentPosition = gameObject.getPosition().grounded();
            if (currentPosition.distance(path.get(0)) < PathFinder.REACH_THRESHOLD) {
                path.remove(0);
                if (path.isEmpty()) {
                    setState(new IdleState());
                    return;
                }
            }

            Vector3 nextPoint = path.get(0);
            Vector3 direction = nextPoint.subtract(currentPosition).grounded().normalize();
            Vector3 velocity = direction.multiply(speed.total());

            if (rigidBody == null) {
                log.warn("[MobDirectiveMoveSkipped] {} has no RigidBody; skipping directive move", gameObject.getType());
                setState(new IdleState());
                return;
            }

            rigidBody.addVelocity(velocity);
        }

        private void updateDestination() {
            Optional<Vector3> moveTarget = directive.getMoveTarget(gameObject);
            if (moveTarget.isEmpty()) {
                destination = null;
                path = null;
                return;
            }

            destination = moveTarget.get();
            path = pathFinder.findPath(gameObject.getPosition().grounded(), destination.grounded());
        }
    }

    public class AttackState extends State {
        private float timer = 0;
        @Override
        public void onEnter() {

        }

        @Override
        public void onExit() {

        }

        @Override
        public void onUpdate() {
            if (!isValidTarget(target)) {
                resetTarget();
                setState(new IdleState());
                return;
            }
            timer += getGameContext().getDeltaTime();
            if (!withinAttackRange(attackRange)) {
                setState(new MoveState());
            } else if (timer > attackInterval.total()) {
                timer = 0;

                if (!behavior.test(target)) {
                    setState(new IdleState());
                    return;
                }
            }
        }
    }
}
