package com.wordonline.server.game.domain.object.component.physic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BehaviorMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Status;

import java.util.EnumSet;
import java.util.List;

public class BoulderStrikeKnockback extends Component implements Collidable {

    public static final String IMPACT_PROJECTILE = "BoulderStrikeImpact";

    private GameObject source;
    private Vector3 direction;
    private float speed;
    private float remaining;
    private int collisionDamage;
    private boolean resolved;

    private BoulderStrikeKnockback(
            GameObject target,
            GameObject source,
            Vector3 direction,
            float speed,
            float duration,
            int collisionDamage) {
        super(target);
        refresh(source, direction, speed, duration, collisionDamage);
    }

    public static boolean apply(
            GameObject target,
            GameObject source,
            Vector3 direction,
            float speed,
            float duration,
            int collisionDamage) {
        RigidBody rigidBody = target == null ? null : target.getComponent(RigidBody.class);
        if (target == null
                || source == null
                || direction == null
                || rigidBody == null
                || ForcedMovement.massMultiplier(rigidBody.getMass()) == 0f) {
            return false;
        }

        BoulderStrikeKnockback existing = target.getComponents(BoulderStrikeKnockback.class)
                .stream()
                .findFirst()
                .orElseGet(() -> target.getComponentsToAdd().stream()
                        .filter(BoulderStrikeKnockback.class::isInstance)
                        .map(BoulderStrikeKnockback.class::cast)
                        .findFirst()
                        .orElse(null));

        if (existing != null) {
            existing.refresh(source, direction, speed, duration, collisionDamage);
        } else {
            target.addComponent(new BoulderStrikeKnockback(
                    target,
                    source,
                    direction,
                    speed,
                    duration,
                    collisionDamage));
        }
        return true;
    }

    private void refresh(
            GameObject source,
            Vector3 direction,
            float speed,
            float duration,
            int collisionDamage) {
        this.source = source;
        this.direction = direction.grounded().normalize();
        this.speed = speed;
        this.remaining = duration;
        this.collisionDamage = collisionDamage;
        this.resolved = false;
    }

    @Override
    public void start() {
        BehaviorMob behaviorMob = gameObject.getComponent(BehaviorMob.class);
        if (behaviorMob != null) {
            behaviorMob.setStun(remaining);
        }
    }

    @Override
    public void update() {
        float deltaTime = getGameContext().getDeltaTime();
        if (resolved || deltaTime <= 0f || remaining <= 0f) {
            expire();
            return;
        }

        RigidBody rigidBody = gameObject.getComponent(RigidBody.class);
        if (rigidBody == null) {
            expire();
            return;
        }

        float activeFraction = Math.min(remaining / deltaTime, 1f);
        float velocity = speed * ForcedMovement.massMultiplier(rigidBody.getMass()) * activeFraction;
        rigidBody.addVelocity(direction.multiply(velocity));

        remaining -= deltaTime;
        if (remaining <= 0f) {
            expire();
        }
    }

    @Override
    public void onCollision(GameObject otherObject) {
        if (resolved || !isCollisionDamageTarget(otherObject)) {
            return;
        }
        resolved = true;

        AttackInfo attackInfo = new AttackInfo(
                collisionDamage,
                EnumSet.of(ElementType.ROCK, ElementType.WIND))
                .withAttacker(source);
        applyDamage(gameObject, attackInfo);

        if (isEnemyUnit(otherObject)) {
            applyDamage(otherObject, attackInfo);
        }

        Vector3 impactPosition = Vector3.lerp(
                gameObject.getPosition(),
                otherObject.getPosition(),
                0.5f);
        getGameContext().getObjectsInfoDtoBuilder().createProjection(
                impactPosition,
                impactPosition,
                IMPACT_PROJECTILE,
                0.35f);
        expire();
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
    }

    private boolean isCollisionDamageTarget(GameObject otherObject) {
        if (otherObject == null || otherObject == gameObject) {
            return false;
        }

        return otherObject.getType() == PrefabType.Wall
                || otherObject.getType() == PrefabType.RockRemnant
                || isEnemyUnit(otherObject);
    }

    private boolean isEnemyUnit(GameObject otherObject) {
        return otherObject.isActive()
                && TargetRelation.canAttack(source, otherObject)
                && !otherObject.getComponents(Damageable.class).isEmpty();
    }

    private void applyDamage(GameObject target, AttackInfo attackInfo) {
        List<Damageable> damageables = target.getComponents(Damageable.class);
        if (damageables.isEmpty()) {
            return;
        }

        target.setStatus(Status.Damaged);
        damageables.forEach(damageable -> damageable.onDamaged(attackInfo));
    }

    private void expire() {
        gameObject.removeComponent(this);
    }

    @Override
    public void onDestroy() {
    }
}
