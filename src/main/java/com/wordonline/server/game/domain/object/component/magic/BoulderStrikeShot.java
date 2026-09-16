package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.BoulderStrikeKnockback;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.dto.Status;

import java.util.List;

public class BoulderStrikeShot extends Shot implements Collidable {

    private final int collisionDamage;
    private final float knockbackSpeed;
    private final float knockbackDuration;
    private boolean hit;

    public BoulderStrikeShot(
            GameObject gameObject,
            int damage,
            float projectileSpeed,
            int collisionDamage,
            float knockbackSpeed,
            float knockbackDuration) {
        super(gameObject, damage, projectileSpeed);
        this.collisionDamage = collisionDamage;
        this.knockbackSpeed = knockbackSpeed;
        this.knockbackDuration = knockbackDuration;
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (hit || otherObject == gameObject) {
            return;
        }

        List<Damageable> damageables = otherObject.getComponents(Damageable.class);
        if (damageables.isEmpty()) {
            return;
        }
        hit = true;

        AttackInfo attackInfo = new AttackInfo(damage, gameObject.getElement().total())
                .withAttacker(gameObject);
        otherObject.setStatus(Status.Damaged);
        damageables.forEach(damageable -> damageable.onDamaged(attackInfo));

        if (getDirection() != null) {
            BoulderStrikeKnockback.apply(
                    otherObject,
                    gameObject,
                    getDirection(),
                    knockbackSpeed,
                    knockbackDuration,
                    collisionDamage);
        }

        gameObject.setStatus(Status.Attack);
        gameObject.destroy();
    }
}
