package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.effect.EffectApplication;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.dto.Effect;

public class SprayingAttacker extends BehaviorMob {

    private final int damage;
    private String projectile;
    private Effect effect;
    private Predicate<GameObject> behavior = (target) -> {

        Vector3 direction = target.getPosition().subtract(gameObject.getPosition()).normalize();

        float radius = attackRange / 2;

        Vector3 projectTileStart = getGameObject().getPosition().plus(direction.normalize().multiply(radius * 2));
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(
                        projectTileStart,
                        projectTileStart.plus(direction.normalize()),
                        projectile,
                        0.5f);

        AttackInfo attackInfo = createAttackInfo();
        Set<Mob> mobs = getCollidedMobs(radius, direction);

        if (mobs.isEmpty()) {
            return false;
        }

        mobs.forEach(collidedMob -> {
                    collidedMob.onDamaged(attackInfo);
                });

        mobs.stream().map(Component::getGameObject)
                .filter(gameObject1 -> gameObject1.hasComponent(EffectReceiver.class))
                .map(gameObject1 -> gameObject1.getComponent(EffectReceiver.class))
                .forEach(effectReceiver -> effectReceiver.onReceive(new EffectApplication(effect, gameObject)));
        return true;
    };

    public SprayingAttacker(GameObject gameObject, int maxHp,
            float speed, int targetMask, float attackInterval, float attackRange, int damage, Effect effect, String projectile) {
        super(gameObject, maxHp, speed, targetMask, attackInterval, attackRange, null);
        setBehavior(behavior);
        this.damage = damage;
        this.projectile = projectile;
        this.effect = effect;
    }

    private AttackInfo createAttackInfo() {
        return new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);
    }

    private Set<Mob> getCollidedMobs(float radius, Vector3 direction) {
        Set<Mob> collidedMobs = new HashSet<>(getMonoCollidedMobs(radius, direction, 0));
        collidedMobs.addAll(getMonoCollidedMobs(radius, direction, 1));
        return collidedMobs;
    }

    private List<Mob> getMonoCollidedMobs(float radius, Vector3 direction, int index) {
        return gameObject.getGameContext().getPhysics()
                .overlapSphereAll(
                        getColliderPosition(radius, direction, index),
                        radius
                ).stream().filter(gameObject1 -> gameObject1.hasComponent(Mob.class)).map(gameObject1 -> gameObject1.getComponent(
                        Mob.class)).toList();
    }

    private Vector3 getColliderPosition(float radius, Vector3 direction, int index) {
        float distance = radius + index * 2 * radius;
        return gameObject.getPosition().plus(direction.normalize().multiply(distance));
    }
}
