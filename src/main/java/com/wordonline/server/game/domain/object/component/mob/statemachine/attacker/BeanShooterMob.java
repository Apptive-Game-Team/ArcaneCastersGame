package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.magic.BeanProjectile;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Status;

public class BeanShooterMob extends BehaviorMob {

    /** A bean that misses flies this many attack ranges before it is destroyed. */
    private static final float BEAN_TRAVEL_RANGE_MULTIPLIER = 1.5f;

    private final int damage;

    public BeanShooterMob(
            GameObject gameObject,
            int maxHp,
            float speed,
            int damage,
            float attackInterval,
            float attackRange) {
        super(
                gameObject,
                maxHp,
                speed,
                TargetMask.GROUND.bit,
                attackInterval,
                attackRange,
                null);
        this.damage = damage;
        setBehavior(this::shootBean);
    }

    private boolean shootBean(GameObject target) {
        if (target == null || !target.isActive()) {
            return false;
        }

        GameObject bean = new GameObject(gameObject, PrefabType.Bean);
        bean.getComponent(BeanProjectile.class).launch(
                target.getPosition().grounded(),
                damage,
                attackRange * BEAN_TRAVEL_RANGE_MULTIPLIER);
        gameObject.setStatus(Status.Attack);
        return true;
    }
}
