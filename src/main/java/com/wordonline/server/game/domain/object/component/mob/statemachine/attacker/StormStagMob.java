package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.PanicStatusEffect;
import com.wordonline.server.game.domain.object.component.mob.MovementSpeedTracker;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.mob.detector.PriorityEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetCategory;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.component.mob.statemachine.StateMachineMob;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class StormStagMob extends StateMachineMob implements Collidable {
    private static final float PANIC_COOLDOWN = 10f;
    private static final float PANIC_SPEED_MULTIPLIER = 1.25f;
    private static final float IMPACT_RANGE = 0.05f;
    private static final float ELECTRIC_HIT_DURATION = 0.2f;
    private static final String ELECTRIC_HIT_PROJECTILE = "ElectricShot";
    private static final List<TargetCategory> OBJECTIVE_PRIORITY = List.of(
            TargetCategory.UNIT,
            TargetCategory.BUILDING,
            TargetCategory.PLAYER);
    private static final List<TargetCategory> THREAT_PRIORITY = List.of(
            TargetCategory.UNIT,
            TargetCategory.PLAYER);
    private static final float[] DAMAGE_MULTIPLIER = {0f, 1f, 1.25f, 1.5f, 2f};

    private final int targetMask;
    private final int damage;
    private final float acceleration;
    private final float detectionRange;
    private final float panicDuration;

    private PriorityEnemyDetector objectiveDetector;
    private PriorityEnemyDetector threatDetector;
    private RigidBody rigidBody;
    private MovementSpeedTracker speedTracker;
    private GameObject target;
    private float panicCooldownRemaining;

    public StormStagMob(GameObject gameObject, int maxHp, float maxSpeed, int targetMask,
            int damage, float acceleration, float detectionRange, float panicDuration) {
        super(gameObject, maxHp, maxSpeed);
        this.targetMask = targetMask;
        this.damage = damage;
        this.acceleration = acceleration;
        this.detectionRange = detectionRange;
        this.panicDuration = panicDuration;
    }

    @Override
    public void start() {
        rigidBody = gameObject.getComponent(RigidBody.class);
        speedTracker = gameObject.getComponent(MovementSpeedTracker.class);
        objectiveDetector = new PriorityEnemyDetector(
                getGameContext(), targetMask, Double.MAX_VALUE, OBJECTIVE_PRIORITY);
        threatDetector = new PriorityEnemyDetector(
                getGameContext(), targetMask, detectionRange, THREAT_PRIORITY);
        setState(new TargetSearchState());
    }

    @Override
    public void update() {
        if (panicCooldownRemaining > 0f) {
            panicCooldownRemaining = Math.max(
                    0f, panicCooldownRemaining - getGameContext().getDeltaTime());
        }
        super.update();
    }

    @Override
    public void onDeath() {
        gameObject.destroy();
    }

    @Override
    public void onCollision(GameObject otherObject) {
        if (otherObject.getType() == PrefabType.Wall
                && currentState instanceof PanicState) {
            handleWallCollision();
            return;
        }

    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (!(currentState instanceof ChargeState) || !isValidTarget(otherObject)) {
            return;
        }

        impactTarget(otherObject);
    }

    private void handleWallCollision() {
        panicCooldownRemaining = PANIC_COOLDOWN;
        if (isValidTarget(target)) {
            setState(new ChargeState());
        } else {
            setState(new TargetSearchState());
        }
    }

    private boolean isValidTarget(GameObject candidate) {
        return candidate != null
                && candidate.getStatus() != Status.Destroyed
                && TargetRelation.canAttack(gameObject, candidate);
    }

    private GameObject detectThreat() {
        if (panicCooldownRemaining > 0f) {
            return null;
        }
        return threatDetector.detect(gameObject);
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
                                StatusEffectKey.Panic_Receive)));
    }

    private void removePanicEffect() {
        PanicStatusEffect panic = gameObject.getComponent(PanicStatusEffect.class);
        if (panic != null) {
            gameObject.removeComponent(panic);
        }
        gameObject.getComponentsToAdd().removeIf(PanicStatusEffect.class::isInstance);
        gameObject.removeEffect(Effect.Panic);
    }

    private void impactTarget() {
        impactTarget(target);
    }

    private void impactTarget(GameObject impactTarget) {
        if (!isValidTarget(impactTarget)) {
            setState(new TargetSearchState());
            return;
        }

        Damageable damageable = impactTarget.getComponent(Damageable.class);
        if (damageable == null) {
            setState(new TargetSearchState());
            return;
        }

        int tier = speedTracker.getTier();
        int impactDamage = Math.round(damage * DAMAGE_MULTIPLIER[tier]);
        if (tier >= 3) {
            getGameContext().getObjectsInfoDtoBuilder().createProjection(
                    gameObject, target, ELECTRIC_HIT_PROJECTILE, ELECTRIC_HIT_DURATION);
        }
        damageable.onDamaged(new AttackInfo(impactDamage, gameObject.getElement().total())
                .withAttacker(gameObject));
        gameObject.setStatus(Status.Attack);
        target = impactTarget;
        setState(new PanicState(impactTarget));
    }

    public class TargetSearchState extends State {
        private float timer;

        @Override
        public void onEnter() {
            target = null;
            speedTracker.reset();
            gameObject.setStatus(Status.Idle);
            timer = Detector.DETECTING_INTERVAL;
        }

        @Override
        public void onExit() { }

        @Override
        public void onUpdate() {
            timer += getGameContext().getDeltaTime();
            if (timer < Detector.DETECTING_INTERVAL) {
                return;
            }

            GameObject threat = detectThreat();
            if (threat != null) {
                setState(new PanicState(threat));
                return;
            }

            target = objectiveDetector.detect(gameObject);
            if (target != null) {
                setState(new ChargeState());
                return;
            }
            timer = 0f;
        }
    }

    public class ChargeState extends State {
        private float currentSpeed;
        private float threatTimer;

        @Override
        public void onEnter() {
            currentSpeed = 0f;
            threatTimer = Detector.DETECTING_INTERVAL;
            speedTracker.begin();
            gameObject.setStatus(Status.Move);
        }

        @Override
        public void onExit() {
            speedTracker.reset();
        }

        @Override
        public void onUpdate() {
            if (!isValidTarget(target)) {
                setState(new TargetSearchState());
                return;
            }

            threatTimer += getGameContext().getDeltaTime();
            if (threatTimer >= Detector.DETECTING_INTERVAL) {
                GameObject threat = detectThreat();
                if (threat != null) {
                    setState(new PanicState(threat));
                    return;
                }
                threatTimer = 0f;
            }

            if (CombatRange.contains(gameObject, target, IMPACT_RANGE)) {
                impactTarget();
                return;
            }

            if (rigidBody == null) {
                log.warn("[StormStagChargeSkipped] {} has no RigidBody", gameObject.getType());
                setState(new TargetSearchState());
                return;
            }

            currentSpeed = Math.min(
                    speed.total(),
                    currentSpeed + acceleration * getGameContext().getDeltaTime());
            Vector3 direction = target.getPosition()
                    .subtract(gameObject.getPosition())
                    .grounded()
                    .normalize();
            rigidBody.addVelocity(direction.multiply(currentSpeed));
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
            speedTracker.reset();
            timer = 0f;
            fleeDirection = gameObject.getPosition()
                    .subtract(threat.getPosition())
                    .grounded()
                    .normalize();
            if (fleeDirection.distance(Vector3.ZERO) == 0) {
                fleeDirection = Vector3.randomUnitVector();
            }
            addPanicEffect();
            gameObject.setStatus(Status.Hindered);
        }

        @Override
        public void onExit() {
            removePanicEffect();
        }

        @Override
        public void onUpdate() {
            timer += getGameContext().getDeltaTime();
            if (rigidBody != null) {
                rigidBody.addVelocity(fleeDirection.multiply(speed.total() * PANIC_SPEED_MULTIPLIER));
            }
            if (timer < panicDuration) {
                return;
            }

            panicCooldownRemaining = PANIC_COOLDOWN;
            setState(new TargetSearchState());
        }
    }

}
