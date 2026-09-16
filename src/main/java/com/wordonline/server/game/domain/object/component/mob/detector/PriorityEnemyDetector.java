package com.wordonline.server.game.domain.object.component.mob.detector;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.util.CombatRange;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class PriorityEnemyDetector implements Detector {
    private final GameContext gameContext;
    private final int targetMask;
    private final double maxRange;
    private final List<TargetCategory> priority;

    public PriorityEnemyDetector(GameContext gameContext, int targetMask, double maxRange, List<TargetCategory> priority) {
        this.gameContext = gameContext;
        this.targetMask = targetMask;
        this.maxRange = maxRange;
        this.priority = List.copyOf(priority);
    }

    @Override
    public GameObject detect(GameObject self) {
        return detect(self, target -> true);
    }

    @Override
    public GameObject detect(GameObject self, Predicate<GameObject> filter) {
        return gameContext.getGameSessionData().gameObjects.stream()
                .filter(target -> isCandidate(self, target))
                .filter(filter)
                .min(Comparator
                        .comparingInt((GameObject target) -> priorityIndex(TargetCategory.of(target)))
                        .thenComparingDouble(target -> self.getPosition().distance(target.getPosition())))
                .orElse(null);
    }

    private boolean isCandidate(GameObject self, GameObject target) {
        if (target.getStatus() == Status.Destroyed) return false;
        if (!TargetRelation.canAttack(self, target)) return false;
        if (!target.hasComponent(Damageable.class)) return false;
        if ((TargetMask.of(target) & targetMask) == 0) return false;
        if (!CombatRange.contains(self, target, maxRange)) return false;

        return priority.contains(TargetCategory.of(target));
    }

    private int priorityIndex(TargetCategory category) {
        int index = priority.indexOf(category);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }
}
