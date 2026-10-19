package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.mob.detector.PriorityEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetCategory;

import java.util.List;

public class BuildingPrioMob extends MeleeAttackMob {
    private static final List<TargetCategory> PRIORITY = List.of(
            TargetCategory.BUILDING,
            TargetCategory.PLAYER
    );

    private final int targetMask;

    public BuildingPrioMob(GameObject gameObject, int maxHp, float speed, int targetMask, int damage, float attackInterval) {
        super(gameObject, maxHp, speed, targetMask, damage, attackInterval);
        this.targetMask = targetMask;
    }

    @Override
    public void start() {
        super.start();
        detector = new PriorityEnemyDetector(getGameContext(), targetMask, Double.MAX_VALUE, PRIORITY);
    }
}
