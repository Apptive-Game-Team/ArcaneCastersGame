package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.physic.ForcedMovement;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

/**
 * A ranged damage dealer that fights with three separate extending arms.
 *
 * <p>The basic attack is a wooden arm punched out at range on the shared attack interval. On its
 * own longer cooldown the ent throws a second arm that hooks the victim and drags it inward, then
 * finishes with a third arm - a burning fist. All three arms are client-side visuals delivered as
 * projectiles; the server only owns the timing, the pull and the damage.
 *
 * <p>The burning fist is the payout for a drag that worked, never a hit the ent can collect on
 * its own. Anything too heavy to drag is never grabbed in the first place, and a grab that loses
 * its drag part way through lets go instead of finishing.
 */
public class EvilEntMob extends BehaviorMob {

    private static final String PUNCH_ARM_PROJECTILE = "EvilEntPunchArm";
    private static final String GRAB_ARM_PROJECTILE = "EvilEntGrabArm";
    private static final String FIRE_FIST_PROJECTILE = "EvilEntFireFist";

    /**
     * Fraction of a projectile's duration at which each arm is fully extended and its hit lands.
     * The client shapes the three arms differently on purpose, so the fraction is per arm rather
     * than shared, and each one mirrors an entry in the motion table at the top of the client's
     * StretchProjectile: PunchMotion, GrabMotion and FireFistMotion respectively. That table is
     * the authority; a change there has to be made here too, or the hit stops landing as the tip
     * arrives.
     */
    private static final float PUNCH_IMPACT_FRACTION = 0.55f;
    private static final float GRAB_IMPACT_FRACTION = 0.22f;
    private static final float FIRE_FIST_IMPACT_FRACTION = 0.45f;

    /** How long a hooked victim is held in place after being moved in front of the ent. */
    private static final float PULL_DURATION = 0.8f;

    private static final int MAX_PULL_CHARGES = 3;

    /** Distance at which a dragged victim is close enough for the fire fist to land early. */
    private static final float FIST_RANGE = 0.2f;

    /** Ceiling on one grab sequence stage, so a stalled projectile can never park the ent. */
    private static final float STAGE_TIMEOUT = 3f;

    /** Wind-down after the fire fist connects, before the ent looks for a target again. */
    private static final float FIST_RECOVERY = 0.4f;

    private final int damage;
    private final int subDamage;
    private final float projectileSpeed;
    private final float subAttackRange;
    private final float subAttackInterval;
    private final float pullMassLimit;

    private int pullCharges;
    private float pullRechargeTimer;

    public EvilEntMob(GameObject gameObject,
                      int maxHp,
                      float speed,
                      int targetMask,
                      int damage,
                      float attackInterval,
                      float attackRange,
                      float projectileSpeed,
                      int subDamage,
                      float subAttackRange,
                      float subAttackInterval,
                      float pullMassLimit) {
        super(gameObject, maxHp, speed, targetMask, attackInterval, attackRange, null);
        this.damage = damage;
        this.subDamage = subDamage;
        this.projectileSpeed = projectileSpeed;
        this.subAttackRange = subAttackRange;
        this.subAttackInterval = subAttackInterval;
        this.pullMassLimit = pullMassLimit;
        setBehavior(this::punch);
    }

    /**
     * The grab runs on a cooldown of its own rather than through the attack state, so it can start
     * from a longer range than the punch and hook a target the ent cannot yet reach. Charges keep
     * recovering while the sequence owns the state machine, so saved grabs can be spent in
     * consecutive sequences once the current fist has resolved.
     *
     * <p>Whether the sequence is running is read off the state machine rather than latched in a
     * field of our own, because we are not the only writer: the super call on the line above can
     * take the state away from the sequence when the ent changes master or a movement directive
     * such as a rallying totem claims it, and neither path gives the sequence a chance to clean up
     * after itself. A latch would stay set through that and the special would never fire again.
     * Derived this way the ent heals itself on the very next tick.
     */
    @Override
    public void update() {
        super.update();

        rechargePullCharges();

        if (currentState instanceof GrabState || currentState instanceof FistState) {
            return;
        }

        if (pullCharges == 0) {
            return;
        }
        if (!isValidTarget(target) || !CombatRange.contains(gameObject, target, subAttackRange)) {
            return;
        }
        // The fire fist is the payout for a successful drag, so a target that cannot be dragged is
        // not worth opening on: the ent just keeps punching, which is what it should be doing to a
        // tank anyway. Failing here leaves the cooldown full rather than spending it, so the grab
        // goes out the instant something draggable comes into range.
        if (!canBeDragged(target)) {
            return;
        }

        pullCharges--;
        setState(new GrabState(target));
    }

    private void rechargePullCharges() {
        if (pullCharges == MAX_PULL_CHARGES) {
            pullRechargeTimer = 0f;
            return;
        }

        pullRechargeTimer += getGameContext().getDeltaTime();
        while (pullRechargeTimer >= subAttackInterval && pullCharges < MAX_PULL_CHARGES) {
            pullRechargeTimer -= subAttackInterval;
            pullCharges++;
        }
        if (pullCharges == MAX_PULL_CHARGES) {
            pullRechargeTimer = 0f;
        }
    }

    private boolean punch(GameObject victim) {
        float reachTime = reachTime(victim);
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(gameObject, victim, PUNCH_ARM_PROJECTILE,
                        reachTime / PUNCH_IMPACT_FRACTION);
        gameObject.setStatus(Status.Attack);

        victim.getComponentOptional(Damageable.class)
                .ifPresent(damageable -> damageable.onDamaged(attackInfo(damage), reachTime));
        return true;
    }

    /** How long an arm takes to cross the gap to the victim at {@code projectile_speed}. */
    private float reachTime(GameObject victim) {
        return (float) (victim.getPosition().distance(gameObject.getPosition()) / projectileSpeed);
    }

    private AttackInfo attackInfo(int amount) {
        return new AttackInfo(amount, gameObject.getElement().total()).withAttacker(gameObject);
    }

    /**
     * Whether the grab can actually move this victim, and so whether it is worth opening on. The
     * mass limit keeps golems out, {@link ForcedMovement#massMultiplier(int)} keeps the immovables
     * out, and the missing rigid body keeps out everything that is not simulated at all - the
     * player among them, who has a collider and a health component but no body to shove.
     */
    private boolean canBeDragged(GameObject victim) {
        RigidBody rigidBody = victim.getComponent(RigidBody.class);
        return rigidBody != null
                && ForcedMovement.massMultiplier(rigidBody.getMass()) != 0f
                && rigidBody.getMass() <= pullMassLimit;
    }

    private Vector3 pullDestination(GameObject victim) {
        Vector3 directionToVictim = victim.getPosition().grounded()
                .subtract(gameObject.getPosition().grounded())
                .normalize();
        if (directionToVictim.getX() == 0f && directionToVictim.getZ() == 0f) {
            directionToVictim = Vector3.RIGHT;
        }
        float frontDistance = CombatRange.radiusOf(gameObject) + CombatRange.radiusOf(victim);
        return gameObject.getPosition().grounded()
                .plus(directionToVictim.multiply(frontDistance));
    }

    private void abortGrabSequence() {
        resetTarget();
        setState(new IdleState());
    }

    private void finishGrabSequence() {
        setState(new IdleState());
    }

    /**
     * Throws the grab arm, then drags whatever it caught. The launch waits for the first update
     * instead of happening in {@code onEnter}, because a state is not allowed to replace itself
     * while the machine is still entering it.
     */
    public class GrabState extends State {

        private final GameObject victim;
        private float timer;
        private boolean launched;
        private boolean pulled;
        private float reachTime;
        private float pullStartTime;
        private float pullEndTime;
        private Vector3 pullStartPosition;
        private Vector3 pullTargetPosition;

        public GrabState(GameObject victim) {
            this.victim = victim;
        }

        @Override
        public void onEnter() {
        }

        @Override
        public void onExit() {
        }

        @Override
        public void onUpdate() {
            if (!isValidTarget(victim)) {
                abortGrabSequence();
                return;
            }

            if (!launched) {
                launched = true;
                // The grab arm has to still be out when the drag ends. The client extends it over
                // the first GRAB_IMPACT_FRACTION of the duration it is handed, then holds it at
                // full extension and re-aims every frame, so the arm visibly shortens as the
                // victim is reeled in. Whichever of the two needs longer sets the duration: the
                // arm crossing the gap, or the whole drag fitting into what is left afterwards.
                float armDuration = Math.max(
                        reachTime(victim) / GRAB_IMPACT_FRACTION,
                        PULL_DURATION / (1f - GRAB_IMPACT_FRACTION));
                reachTime = armDuration * GRAB_IMPACT_FRACTION;
                getGameContext().getObjectsInfoDtoBuilder()
                        .createProjection(gameObject, victim, GRAB_ARM_PROJECTILE, armDuration);
                gameObject.setStatus(Status.Attack);
            }

            timer += getGameContext().getDeltaTime();

            if (!pulled) {
                if (timer < reachTime && timer < STAGE_TIMEOUT) {
                    return;
                }
                pulled = true;
                // No drag, no fire fist. Let go at once rather than holding the arm out for a
                // pull that is not happening and then cashing in the heavy hit for free.
                if (!canBeDragged(victim)) {
                    abortGrabSequence();
                    return;
                }
                BehaviorMob victimBehavior = victim.getComponent(BehaviorMob.class);
                if (victimBehavior != null) {
                    victimBehavior.setStun(PULL_DURATION);
                }
                pullStartTime = timer;
                pullEndTime = timer + PULL_DURATION;
                pullStartPosition = victim.getPosition();
                pullTargetPosition = pullDestination(victim);
            }

            float pullProgress = (timer - pullStartTime) / PULL_DURATION;
            victim.setPosition(Vector3.lerp(pullStartPosition, pullTargetPosition, pullProgress));

            if (CombatRange.contains(gameObject, victim, FIST_RANGE)
                    || timer >= pullEndTime
                    || timer >= STAGE_TIMEOUT) {
                setState(new FistState(victim));
            }
        }
    }

    /** Lands the burning fist on the dragged victim, then hands the ent back to the idle loop. */
    public class FistState extends State {

        private final GameObject victim;
        private float timer;
        private boolean launched;
        private boolean landed;
        private float impactTime;

        public FistState(GameObject victim) {
            this.victim = victim;
        }

        @Override
        public void onEnter() {
        }

        @Override
        public void onExit() {
        }

        @Override
        public void onUpdate() {
            if (!landed && !isValidTarget(victim)) {
                abortGrabSequence();
                return;
            }

            if (!launched) {
                launched = true;
                impactTime = reachTime(victim);
                getGameContext().getObjectsInfoDtoBuilder()
                        .createProjection(gameObject, victim, FIRE_FIST_PROJECTILE,
                                impactTime / FIRE_FIST_IMPACT_FRACTION);
                gameObject.setStatus(Status.Attack);
            }

            timer += getGameContext().getDeltaTime();

            if (!landed) {
                if (timer < impactTime && timer < STAGE_TIMEOUT) {
                    return;
                }
                landed = true;
                strike(victim);
            }

            if (timer >= impactTime + FIST_RECOVERY || timer >= STAGE_TIMEOUT) {
                finishGrabSequence();
            }
        }
    }

    private void strike(GameObject victim) {
        victim.getComponentOptional(Damageable.class)
                .ifPresent(damageable -> damageable.onDamaged(attackInfo(subDamage)));
        victim.getComponentOptional(EffectReceiver.class)
                .ifPresent(effectReceiver -> effectReceiver.onReceive(Effect.Burn));
    }
}
