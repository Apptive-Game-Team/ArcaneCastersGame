package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.dto.Status;

import java.util.List;

/**
 * A bean fired by a bean shooter. It flies in a straight line along the ground, deals its damage to
 * the first grounded enemy it touches, and disappears. Aerial objects and allies are passed
 * through, and a bean that has flown {@code maxDistance} without hitting anything is destroyed.
 */
public class BeanProjectile extends MagicComponent implements Collidable {

    private final float speed;

    private Vector3 direction;
    private Vector3 origin;
    private int damage;
    private float maxDistance;
    private boolean spent;

    public BeanProjectile(GameObject gameObject, float speed) {
        super(gameObject);
        this.speed = speed;
    }

    /**
     * Aims the bean at {@code targetPosition} and hands it the shooter's damage. The origin is
     * taken here because the bean is created at the shooter and this runs before its first update.
     */
    public void launch(Vector3 targetPosition, int damage, float maxDistance) {
        this.origin = new Vector3(gameObject.getPosition());
        this.direction = targetPosition.subtract(gameObject.getPosition()).grounded().normalize();
        this.damage = damage;
        this.maxDistance = maxDistance;
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
        if (spent || direction == null) {
            return;
        }

        gameObject.setPosition(gameObject.getPosition()
                .plus(direction.multiply(speed * getGameContext().getDeltaTime())));

        if (gameObject.getPosition().distance(origin) >= maxDistance) {
            spent = true;
            gameObject.destroy();
        }
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (spent
                || !otherObject.isActive()
                || !TargetRelation.canAttack(gameObject, otherObject)
                || TargetMask.of(otherObject) != TargetMask.GROUND.bit) {
            return;
        }

        List<Damageable> damageables = otherObject.getComponents(Damageable.class);
        if (damageables.isEmpty()) {
            return;
        }

        spent = true;
        gameObject.setStatus(Status.Attack);
        otherObject.setStatus(Status.Damaged);
        AttackInfo attackInfo = new AttackInfo(damage, gameObject.getElement().total())
                .withAttacker(gameObject);
        damageables.forEach(damageable -> damageable.onDamaged(attackInfo));
        gameObject.destroy();
    }

    @Override
    public void onDestroy() {
    }
}
