package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.pve.PveObjectiveTarget;

// The PVE boss version of the card's EvilEnt. The three arm attacks, the grab and the fire fist
// are inherited unchanged; this class pins the ent in place, ends it as an objective, and keeps
// its aggro on the closest enemy. It never walks, so everything it can hit has to be inside
// attack_range and sub_attack_range measured from where the scenario installed it. Unlike a unit
// it leaves no element field where it dies.
//
// Aggro follows distance. A mob keeps the target it picked until that target dies or leaves its
// range, and this boss picks its first target when the match starts, when the player is the only
// enemy on the field. With attack_range covering the whole arena it then punched the player
// until the player died, while every unit the player summoned stood closer. Every second it now
// looks for the closest enemy again, so units draw the punches and the grab, and the player is
// only hit when nothing else is left.
public class PveEvilEntMob extends EvilEntMob implements PveObjectiveTarget {

    private static final float STATIONARY_SPEED = 0f;

    private float retargetTimer;

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
    public void update() {
        retargetClosestEnemy();
        super.update();
    }

    // Skipped while the grab arm or the fire fist is out: both hold the victim they started on.
    private void retargetClosestEnemy() {
        if (target == null || currentState instanceof GrabState || currentState instanceof FistState) {
            return;
        }

        retargetTimer += getGameContext().getDeltaTime();
        if (retargetTimer < Detector.DETECTING_INTERVAL) {
            return;
        }
        retargetTimer = 0f;

        GameObject closest = detector.detect(gameObject);
        if (closest == null || closest == target || !isValidTarget(closest)) {
            return;
        }
        target = closest;
        targetRadius = closest.getFirstCircleCollider().map(CircleCollider::getRadius).orElse(0f);
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
