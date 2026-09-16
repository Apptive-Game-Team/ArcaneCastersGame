package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.BombSpriteBomb;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.dto.Status;

public class BombSpriteMob extends BehaviorMob {

    public BombSpriteMob(
            GameObject gameObject,
            int maxHp,
            float speed,
            float attackInterval,
            float attackRange) {
        super(
                gameObject,
                maxHp,
                speed,
                TargetMask.GROUND.bit,
                attackInterval,
                attackRange,
                null,
                true);
        setBehavior(this::dropBomb);
    }

    private boolean dropBomb(GameObject target) {
        if (target == null || !target.isActive()) {
            return false;
        }

        GameObject bomb = new GameObject(gameObject, PrefabType.BombSpriteBomb);
        BombSpriteBomb bombComponent = bomb.getComponent(BombSpriteBomb.class);
        bombComponent.setTarget(new Vector3(target.getPosition()).grounded());
        gameObject.setStatus(Status.Attack);
        return true;
    }
}
