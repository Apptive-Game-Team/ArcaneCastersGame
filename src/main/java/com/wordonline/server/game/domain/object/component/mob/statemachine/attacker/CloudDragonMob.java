package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.Shot;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.IntervalTimer;

public class CloudDragonMob extends ProjectileRangeAttackMob {

    private final float chainLightningCooldown;
    private float chainLightningTimer;

    public CloudDragonMob(GameObject gameObject,
                          int maxHp,
                          float speed,
                          int targetMask,
                          int damage,
                          float attackInterval,
                          float attackRange,
                          float chainLightningCooldown) {
        super(
                gameObject,
                maxHp,
                speed,
                targetMask,
                damage,
                attackInterval,
                attackRange,
                "WaterShot",
                0.5f);
        this.chainLightningCooldown = chainLightningCooldown;
    }

    @Override
    public void update() {
        super.update();

        float deltaTime = getGameContext().getDeltaTime();
        chainLightningTimer = Math.min(
                chainLightningCooldown + deltaTime,
                chainLightningTimer + deltaTime);
        if (chainLightningTimer < chainLightningCooldown || !isValidTarget(target)) {
            return;
        }

        fireChainLightning(target);
        chainLightningTimer = IntervalTimer.carryOver(chainLightningTimer, chainLightningCooldown, deltaTime);
    }

    protected void fireChainLightning(GameObject target) {
        GameObject chainLightning = new GameObject(
                gameObject.getMaster(),
                PrefabType.ChainLightning,
                new Vector3(gameObject.getPosition()),
                getGameContext());
        chainLightning.getComponent(Shot.class).setTarget(target.getPosition());
        gameObject.setStatus(Status.Attack);
    }
}
