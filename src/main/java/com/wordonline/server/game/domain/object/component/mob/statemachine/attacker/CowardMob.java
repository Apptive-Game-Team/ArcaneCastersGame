package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.PanicStatusEffect;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.mob.detector.PriorityEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetCategory;
import com.wordonline.server.game.domain.object.component.mob.pathfinder.PathFinder;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import lombok.extern.slf4j.Slf4j;
import com.wordonline.server.game.util.CombatRange;

import java.util.List;
import com.wordonline.server.game.util.IntervalTimer;

@Slf4j
public class CowardMob extends AttackMob {
    private static final float DEFAULT_ATTACK_RANGE = 0.5f;
    private static final float PANIC_COOLDOWN = 10f;
    private static final List<TargetCategory> OBJECTIVE_PRIORITY = List.of(
            TargetCategory.BUILDING,
            TargetCategory.PLAYER
    );
    private static final List<TargetCategory> THREAT_PRIORITY = List.of(TargetCategory.UNIT);

    private final int targetMask;
    private final float detectionRange;
    private final float panicDuration;
    private final float panicSpeedMultiplier;
    private Detector objectiveDetector;
    private Detector threatDetector;
    private float panicCooldownRemaining;

    public CowardMob(GameObject gameObject, int maxHp, float speed, int targetMask, int damage,
            float attackInterval, float detectionRange, float panicDuration) {
        this(gameObject, maxHp, speed, targetMask, damage, attackInterval, detectionRange, panicDuration, 2f);
    }

    public CowardMob(GameObject gameObject, int maxHp, float speed, int targetMask, int damage,
            float attackInterval, float detectionRange, float panicDuration, float panicSpeedMultiplier) {
        super(gameObject, maxHp, speed, targetMask, damage, attackInterval, DEFAULT_ATTACK_RANGE);
        this.targetMask = targetMask;
        this.detectionRange = detectionRange;
        this.panicDuration = panicDuration;
        this.panicSpeedMultiplier = panicSpeedMultiplier;
    }

    @Override
    public void start() {
        super.start();
        objectiveDetector = new PriorityEnemyDetector(getGameContext(), targetMask, Double.MAX_VALUE, OBJECTIVE_PRIORITY);
        threatDetector = new PriorityEnemyDetector(getGameContext(), targetMask, detectionRange, THREAT_PRIORITY);
        detector = objectiveDetector;
        setState(new CowardIdleState());
    }

    @Override
    public void update() {
        if (panicCooldownRemaining > 0f) {
            panicCooldownRemaining = Math.max(0f, panicCooldownRemaining - getGameContext().getDeltaTime());
        }
        super.update();
    }

    private GameObject detectThreat() {
        if (panicCooldownRemaining > 0f) {
            return null;
        }
        return threatDetector.detect(gameObject);
    }

    private void moveToTargetOrAttack() {
        if (!isValidTarget(target)) {
            resetTarget();
            setState(new CowardIdleState());
            return;
        }

        targetRadius = target.getFirstCircleCollider()
                .map(CircleCollider::getRadius)
                .orElse(0f);

        if (CombatRange.contains(gameObject, target, attackRange)) {
            setState(new CowardAttackState());
        } else {
            setState(new CowardMoveState());
        }
    }

    private void addPanicEffect() {
        PanicStatusEffect existing = gameObject.getComponent(PanicStatusEffect.class);
        if (existing != null) {
            existing.refresh(panicDuration);
            return;
        }

        gameObject.getComponentsToAdd().stream()
                .filter(PanicStatusEffect.class::isInstance)
                .map(PanicStatusEffect.class::cast)
                .findFirst()
                .ifPresentOrElse(
                        effect -> effect.refresh(panicDuration),
                        () -> gameObject.addComponent(new PanicStatusEffect(
                                gameObject,
                                panicDuration,
                                StatusEffectKey.Panic_Receive))
                );
    }

    public class CowardIdleState extends State {
        private float timer;

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
            if (timer <= Detector.DETECTING_INTERVAL) {
                return;
            }

            GameObject threat = detectThreat();
            if (threat != null) {
                setState(new PanicState(threat));
                return;
            }

            target = objectiveDetector.detect(gameObject);
            if (target != null) {
                moveToTargetOrAttack();
                return;
            }

            timer = 0f;
        }
    }

    public class CowardMoveState extends State {
        private float timer;
        private List<Vector3> path;

        @Override
        public void onEnter() {
            if (!isValidTarget(target)) {
                resetTarget();
                setState(new CowardIdleState());
                return;
            }
            path = pathFinder.findPath(gameObject.getPosition().grounded(), target.getPosition().grounded());
        }

        @Override
        public void onExit() {
        }

        @Override
        public void onUpdate() {
            GameObject threat = detectThreat();
            if (threat != null) {
                setState(new PanicState(threat));
                return;
            }

            if (!isValidTarget(target) || path == null || path.isEmpty()) {
                resetTarget();
                setState(new CowardIdleState());
                return;
            }

            Vector3 currentPosition = gameObject.getPosition().grounded();
            if (currentPosition.distance(path.get(0)) < PathFinder.REACH_THRESHOLD) {
                path.remove(0);
                if (path.isEmpty()) {
                    setState(new CowardIdleState());
                    return;
                }
            }

            if (CombatRange.contains(gameObject, target, Math.max(0f, attackRange - 0.1f))) {
                setState(new CowardAttackState());
                return;
            }

            timer += getGameContext().getDeltaTime();
            if (timer > Detector.DETECTING_INTERVAL) {
                GameObject newTarget = objectiveDetector.detect(gameObject);
                if (newTarget != null && newTarget != target) {
                    target = newTarget;
                    targetRadius = newTarget.getFirstCircleCollider().map(CircleCollider::getRadius).orElse(0f);
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
                setState(new CowardIdleState());
                return;
            }

            rigidBody.addVelocity(velocity);
        }
    }

    public class CowardAttackState extends State {
        private float timer = 0f;

        @Override
        public void onEnter() {
        }

        @Override
        public void onExit() {
        }

        @Override
        public void onUpdate() {
            GameObject threat = detectThreat();
            if (threat != null) {
                setState(new PanicState(threat));
                return;
            }

            if (!isValidTarget(target)) {
                resetTarget();
                setState(new CowardIdleState());
                return;
            }

            float deltaTime = getGameContext().getDeltaTime();
            timer += deltaTime;
            if (!CombatRange.contains(gameObject, target, attackRange)) {
                setState(new CowardMoveState());
            } else if (timer > attackInterval.total()) {
                timer = IntervalTimer.carryOver(timer, attackInterval.total(), deltaTime);

                if (!behavior.test(target)) {
                    setState(new CowardIdleState());
                }
            }
        }
    }

    public class PanicState extends State {
        private final GameObject threat;
        private Vector3 fleeDirection;
        private float timer;

        public PanicState(GameObject threat) {
            this.threat = threat;
        }

        @Override
        public void onEnter() {
            timer = 0f;
            fleeDirection = gameObject.getPosition()
                    .subtract(threat.getPosition())
                    .grounded()
                    .normalize();
            if (fleeDirection.distance(Vector3.ZERO) == 0) {
                fleeDirection = Vector3.randomUnitVector();
            }
            addPanicEffect();
        }

        @Override
        public void onExit() {
            PanicStatusEffect panic = gameObject.getComponent(PanicStatusEffect.class);
            if (panic != null) {
                gameObject.removeComponent(panic);
            }
        }

        @Override
        public void onUpdate() {
            timer += getGameContext().getDeltaTime();
            RigidBody rb = gameObject.getComponent(RigidBody.class);
            if (rb != null) {
                Vector3 velocity = fleeDirection.multiply(speed.total() * panicSpeedMultiplier);
                rb.addVelocity(velocity);
            } else {
                log.warn("[CowardPanicMoveSkipped] {} has no RigidBody; skipping panic move", gameObject.getType());
            }

            if (timer < panicDuration) {
                return;
            }

            panicCooldownRemaining = PANIC_COOLDOWN;
            target = objectiveDetector.detect(gameObject);
            if (target == null) {
                resetTarget();
                setState(new CowardIdleState());
                return;
            }

            moveToTargetOrAttack();
        }
    }
}
