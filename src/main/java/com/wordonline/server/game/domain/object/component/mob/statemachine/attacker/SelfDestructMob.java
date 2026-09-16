package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
import lombok.RequiredArgsConstructor;

import java.util.function.Predicate;

public class SelfDestructMob extends BehaviorMob implements Collidable {

    private static final float ATTACK_THRESHOLD = 0.3f;
    private final int damage;
    private final float explosionRange;
    private final float explosionAltitudeThreshold;
    private float selfRadius;
    private boolean isExploded = false;

    public SelfDestructMob(GameObject gameObject, int maxHp,
                           float speed, int targetMask, int damage, float attackInterval, float attackRange,
                           boolean verticalRangeIgnored) {
        this(gameObject, maxHp, speed, targetMask, damage, attackInterval, attackRange,
                verticalRangeIgnored, Float.NaN);
    }

    public SelfDestructMob(GameObject gameObject, int maxHp,
                           float speed, int targetMask, int damage, float attackInterval, float attackRange,
                           boolean verticalRangeIgnored, float explosionAltitudeThreshold) {
        // Self-destruct mobs should commit as soon as they can collide, so they do not use the
        // shared attack interval or explosion radius as their attack-state trigger distance.
        super(gameObject, maxHp, speed, targetMask, 0f, 0f, null, verticalRangeIgnored);
        setBehavior(predicate);
        this.damage = damage;
        this.explosionRange = attackRange;
        this.explosionAltitudeThreshold = explosionAltitudeThreshold;
    }

    private final Predicate<GameObject> predicate = (target) -> {
        Mob mob = target.getComponent(Mob.class);

        if (mob == null) return false;

        // The target filtering based on ground/air is now handled by the ClosestEnemyDetector using targetMask
        // This predicate now only checks if the target is a Mob and transitions to AttackingState.
        setState(new AttackingState(mob));
        return true;
    };

    @Override
    public void start() {
        super.start();
        selfRadius = gameObject.getFirstCircleCollider()
                .orElseThrow()
                .getRadius();
        attackRange = selfRadius + ATTACK_THRESHOLD;
        gameObject.drawCircle(Vector3.ZERO, explosionRange, GizmoCategory.AreaOfEffect);
    }

    // The blast belongs at the point of self-destruction, so an aerial self-destruct mob dies
    // where it was hit instead of drifting down and exploding on the ground.
    @Override
    protected boolean fallsOnDeath() {
        return false;
    }

    @Override
    public void onDeath() {
        explode();
        super.onDeath();
    }

    @Override
    public void update() {
        super.update();
        if (!Float.isNaN(explosionAltitudeThreshold)
                && gameObject.getPosition().getY() <= explosionAltitudeThreshold) {
            explode();
            gameObject.destroy();
        }
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (otherObject.getComponent(Mob.class) != null && TargetRelation.canAttack(gameObject, otherObject)) {
            explode();
            gameObject.destroy();
        }
    }



    private void explode() {
        if (isExploded) return;
        isExploded = true;
        AttackInfo attackInfo = new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);
        getGameContext().getPhysics()
                .overlapSphereAll(gameObject, explosionRange)
                .stream()
                .filter(target -> target != gameObject)
                .filter(GameObject::isActive)
                .filter(target -> TargetRelation.canAttack(gameObject, target))
                .filter(target -> target.hasComponent(Mob.class))
                .map(target -> target.getComponent(Mob.class))
                .forEach(mob -> mob.onDamaged(attackInfo));
    }

    @RequiredArgsConstructor
    public class AttackingState extends State {

        private final Mob target;
        private float targetRadius;

        private Vector3 startPos;

        @Override
        public void onEnter() {
            gameObject.getComponentOptional(ZPhysics.class)
                    .ifPresent(zPhysics -> zPhysics.lockHover(this));
            startPos = new Vector3(gameObject.getPosition());
            targetRadius = target.gameObject.getFirstCircleCollider()
                    .orElseThrow()
                    .getRadius();
        }

        @Override
        public void onExit() {
            gameObject.getComponentOptional(ZPhysics.class)
                    .ifPresent(zPhysics -> zPhysics.unlockHover(this));
        }

        @Override
        public void onUpdate() {
            if (target.gameObject.isDestroyed()) {
                setState(new IdleState());
                return;
            }

            // Move towards target
            float lastY = gameObject.getPosition().getY();
            float t = diveProgress(startPos, target.gameObject.getPosition().getY());

            Vector3 nextPos = Vector3.lerp(startPos, target.gameObject.getPosition(), t);
            nextPos.setY(lastY);
            gameObject.setPosition(nextPos);

            // Check if mob is close enough to the target to explode (fallback in case collision event doesn't fire immediately)
            if (gameObject.getPosition().distance(target.gameObject.getPosition()) - targetRadius - selfRadius <= ATTACK_THRESHOLD) {
                 explode();
                 gameObject.destroy();
            }
        }
    }
}
