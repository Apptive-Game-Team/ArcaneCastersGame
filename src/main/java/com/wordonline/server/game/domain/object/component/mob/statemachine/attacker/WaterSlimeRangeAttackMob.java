package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;

public class WaterSlimeRangeAttackMob extends ProjectileRangeAttackMob {

    public WaterSlimeRangeAttackMob(GameObject gameObject,
            int maxHp, float speed, int targetMask, int damage, float attackInterval, float attackRange) {
        super(gameObject, maxHp, speed, targetMask, damage, attackInterval, attackRange, "WaterShot", 0.5f);
    }
}
