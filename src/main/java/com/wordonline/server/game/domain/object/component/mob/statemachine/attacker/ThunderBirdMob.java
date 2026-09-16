package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import java.util.function.Predicate;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;

import lombok.RequiredArgsConstructor;

public class ThunderBirdMob extends BehaviorMob {

    private final int ATTACKABLE_HEIGHT = 3;
    private final float ATTACK_THRESHOLD = 0.5f;
    private final int ATTACK_DURATION = 1;
    private final int damage;
    private float selfRadius;

    public ThunderBirdMob(GameObject gameObject, int maxHp,
            float speed, int targetMask, int damage, float attackInterval, float attackRange) {
        // The bird engages from its hover height and only then dives, so the vertical gap to a
        // ground target must not gate the engagement.
        super(gameObject, maxHp, speed, targetMask, attackInterval, attackRange, null, true);
        setBehavior(predicate);
        this.damage = damage;
    }

    private final Predicate<GameObject> predicate = (target) -> {
        Mob mob = target.getComponent(Mob.class);

        if (mob == null) return false;

        if (gameObject.getPosition().getY() < ATTACKABLE_HEIGHT) {
            setState(new FloatingState());
            return true;
        }

        setState(new AttackingState(mob));
        return true;
    };

    @Override
    public void start() {
        super.start();
        selfRadius = gameObject.getFirstCircleCollider()
                .orElseThrow()
                .getRadius();
    }

    @RequiredArgsConstructor
    public class AttackingState extends State {

        private final Mob target;
        private float targetRadius;

        private Vector3 startPos;

        @Override
        public void onEnter() {
            gameObject.getComponentOptional(ZPhysics.class)
                    .ifPresent(zPhysics -> zPhysics.lockHover(this));
            startPos = new Vector3(gameObject.getPosition());
            targetRadius = target.gameObject.getFirstCircleCollider()
                    .orElseThrow()
                    .getRadius();
        }

        @Override
        public void onExit() {
            gameObject.getComponentOptional(ZPhysics.class)
                    .ifPresent(zPhysics -> zPhysics.unlockHover(this));
        }

        @Override
        public void onUpdate() {
            if (target.gameObject.isDestroyed()) {
                setState(new FloatingState());
                return;
            }

            if (target.gameObject.getPosition().distance(gameObject.getPosition()) - targetRadius - selfRadius > ATTACK_THRESHOLD) {

                float lastY = gameObject.getPosition().getY();
                float t = diveProgress(startPos, target.gameObject.getPosition().getY());

                Vector3 nextPos = Vector3.lerp(startPos, target.gameObject.getPosition(), t);
                nextPos.setY(lastY);
                gameObject.setPosition(nextPos);
                return;
            }

            target.onDamaged(new AttackInfo(calculateDamage(startPos), gameObject.getElement().total()).withAttacker(gameObject));
            setState(new FloatingState());
        }
    }

    private int calculateDamage(Vector3 startPos) {
        return (int) (damage * (startPos.getY() - gameObject.getPosition().getY()) / (ATTACKABLE_HEIGHT - ATTACK_THRESHOLD));
    }

    public class FloatingState extends State {

        @Override
        public void onEnter() {

        }

        @Override
        public void onExit() {

        }

        @Override
        public void onUpdate() {
            if (gameObject.getPosition().getY() < ATTACKABLE_HEIGHT) {
                return;
            }

            setState(new IdleState());
        }
    }
}
