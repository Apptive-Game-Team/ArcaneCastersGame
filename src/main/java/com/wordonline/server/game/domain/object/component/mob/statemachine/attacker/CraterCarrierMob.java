package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.magic.CraterShell;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Status;

/** A siege weapon must finish packing before shared movement AI can take over. */
public class CraterCarrierMob extends BehaviorMob {
    private static final float MUZZLE_HEIGHT = 0.8f;
    private final int damage;
    private final float setupDuration;
    private final float recoveryDuration;
    private boolean deployed;
    private boolean finishingStun;

    public CraterCarrierMob(GameObject object, int hp, float speed, int damage,
            float interval, float range, float setupDuration, float recoveryDuration) {
        super(object, hp, speed, TargetMask.GROUND.bit, interval, range, null);
        this.damage = damage;
        this.setupDuration = setupDuration;
        this.recoveryDuration = recoveryDuration;
        setBehavior(this::fire);
    }

    @Override
    public void setState(State next) {
        // BehaviorMob checks rally directives before advancing the current state. Those requests
        // must neither release a stun nor restart the packing timer every tick.
        if (currentState instanceof StunState && !finishingStun && !(next instanceof StunState)) {
            return;
        }
        if (currentState instanceof RecoveryState && !(next instanceof StunState)) {
            return;
        }
        if (next instanceof AttackState && !(next instanceof SiegeAttackState)) {
            next = new DeploymentState();
        }
        if (deployed && !(next instanceof DeploymentState || next instanceof SiegeAttackState
                || next instanceof RecoveryState || next instanceof StunState)) {
            next = new RecoveryState();
        }
        super.setState(next);
    }

    @Override
    public void setStun(float duration) {
        setState(new CarrierStunState(duration));
    }

    @Override
    public void setIdle() {
        finishingStun = true;
        try {
            super.setIdle();
        } finally {
            finishingStun = false;
        }
    }

    private boolean fire(GameObject enemy) {
        if (!gameObject.isActive() || gameObject.isDying()
                || !isValidTarget(enemy) || !withinAttackRange(attackRange)) {
            return false;
        }
        Vector3 position = gameObject.getPosition();
        GameObject shell = new GameObject(gameObject.getMaster(), PrefabType.CraterShell,
                new Vector3(position.getX(), position.getY() + MUZZLE_HEIGHT, position.getZ()),
                getGameContext());
        shell.getComponent(CraterShell.class).launch(new Vector3(enemy.getPosition()), damage, gameObject);
        gameObject.setStatus(Status.Attack);
        return true;
    }

    private class DeploymentState extends State {
        private float elapsed;

        @Override
        public void onEnter() {
            deployed = true;
            gameObject.setStatus(Status.Deploying);
        }

        @Override
        public void onUpdate() {
            gameObject.setStatus(Status.Deploying);
            if (!isValidTarget(target) || !withinAttackRange(attackRange)) {
                setState(new IdleState());
                return;
            }
            elapsed += getGameContext().getDeltaTime();
            if (elapsed >= setupDuration) {
                CraterCarrierMob.super.setState(new SiegeAttackState());
                fire(target);
            }
        }

        @Override
        public void onExit() {}
    }

    private class SiegeAttackState extends AttackState {
        @Override
        public void onEnter() {
            gameObject.setStatus(Status.Deployed);
        }

        @Override
        public void onUpdate() {
            // Restore the persistent pose after transient Attack/Damaged, but keep a new Attack
            // emitted by super.onUpdate visible in this frame.
            gameObject.setStatus(Status.Deployed);
            super.onUpdate();
        }
    }

    private class RecoveryState extends State {
        private float elapsed;

        @Override
        public void onEnter() {
            gameObject.setStatus(Status.Recovering);
        }

        @Override
        public void onUpdate() {
            gameObject.setStatus(Status.Recovering);
            elapsed += getGameContext().getDeltaTime();
            if (elapsed >= recoveryDuration) {
                deployed = false;
                gameObject.setStatus(Status.Idle);
                CraterCarrierMob.super.setState(new IdleState());
            }
        }

        @Override
        public void onExit() {}
    }

    private class CarrierStunState extends StunState {
        private CarrierStunState(float duration) {
            super(duration);
        }

        @Override
        public void onEnter() {
            gameObject.setStatus(deployed ? Status.Recovering : Status.Idle);
        }

        @Override
        public void onUpdate() {
            gameObject.setStatus(deployed ? Status.Recovering : Status.Idle);
            finishingStun = true;
            try {
                super.onUpdate();
            } finally {
                finishingStun = false;
            }
        }
    }
}
