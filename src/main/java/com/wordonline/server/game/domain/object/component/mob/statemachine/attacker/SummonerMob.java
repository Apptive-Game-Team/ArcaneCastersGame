package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.util.CombatRange;

public class SummonerMob extends BehaviorMob {

    public SummonerMob(
            GameObject gameObject,
            int maxHp,
            float speed,
            int targetMask,
            float attackInterval,
            float attackRange,
            PrefabType prefabType) {
        super(gameObject, maxHp, speed, targetMask, attackInterval, attackRange, (target) -> {
            Vector3 summonPosition = target.getPosition();
            Vector3 offset = summonPosition.subtract(gameObject.getPosition()).grounded();
            // The range that let this attack start was measured from the summoner's own collider
            // edge, so the point it may summon on is that far past the edge, not past the centre.
            // Clamping to the bare range drops the summon inside the summoner's own body and
            // leaves it short of the target by exactly the summoner's radius.
            float maxSummonDistance = CombatRange.reachFrom(gameObject, attackRange);
            if (offset.distance(Vector3.ZERO) > maxSummonDistance) {
                summonPosition = gameObject.getPosition()
                        .plus(offset.normalize().multiply(maxSummonDistance));
            }

            new GameObject(
                    gameObject.getMaster(),
                    prefabType,
                    summonPosition,
                    gameObject.getGameContext()
            );
            return true;
        });
    }
}
