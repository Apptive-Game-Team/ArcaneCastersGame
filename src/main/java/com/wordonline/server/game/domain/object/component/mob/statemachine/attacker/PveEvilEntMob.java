package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;

// The PVE boss version of the card's EvilEnt. The three arm attacks, the grab and the fire fist
// are inherited unchanged; this class only pins the ent in place and ends it as an objective. It
// never walks, so everything it can hit has to be inside attack_range and sub_attack_range
// measured from where the scenario installed it. Unlike a unit it leaves no element field where
// it dies.
public class PveEvilEntMob extends EvilEntMob implements PveObjectiveTarget {

    private static final float STATIONARY_SPEED = 0f;

    public PveEvilEntMob(GameObject gameObject,
                         int maxHp,
                         int targetMask,
                         int damage,
                         float attackInterval,
                         float attackRange,
                         float projectileSpeed,
                         int subDamage,
                         float subAttackRange,
                         float subAttackInterval,
                         float pullMassLimit) {
        super(gameObject, maxHp, STATIONARY_SPEED, targetMask, damage, attackInterval, attackRange,
                projectileSpeed, subDamage, subAttackRange, subAttackInterval, pullMassLimit);
    }

    @Override
    protected boolean leavesDeathField() {
        return false;
    }

    @Override
    public boolean isTerminal() {
        return gameObject.isDestroyed() || getHp() <= 0;
    }
}
