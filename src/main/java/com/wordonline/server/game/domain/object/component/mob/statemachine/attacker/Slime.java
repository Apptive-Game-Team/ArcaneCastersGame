package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;

public class Slime extends MeleeAttackMob {

    public Slime(GameObject gameObject, int maxHp, float speed, int targetMask, int damage, float attackInterval) {
        super(gameObject, maxHp, speed, targetMask, damage, attackInterval);
    }
}
