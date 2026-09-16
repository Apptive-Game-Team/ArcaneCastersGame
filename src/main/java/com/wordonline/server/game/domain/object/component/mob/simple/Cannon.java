package com.wordonline.server.game.domain.object.component.mob.simple;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.detector.ClosestEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

public class Cannon extends TimedBehaviorMob {

    private final static float DEFAULT_ATTACK_DURATION = 0.5f;

    private final AttackInfo attackInfo;
    private final int targetMask;
    private Detector detector;
    private final float attackDuration;
    private final float attackRange;

    public Cannon(GameObject gameObject, int maxHp, int damage, int targetMask, float attackInterval, float attackRange) {
        this(gameObject, maxHp, damage, targetMask, DEFAULT_ATTACK_DURATION, attackInterval, attackRange);
    }

    public Cannon(GameObject gameObject, int maxHp, int damage, int targetMask, float attackDuration, float attackInterval, float attackRange) {
        super(gameObject, maxHp, 0, attackInterval, null);
        setBehavior(this::attack);
        attackInfo = new AttackInfo(damage, ElementType.ROCK).withAttacker(gameObject);
        this.targetMask = targetMask;
        this.attackDuration = attackDuration;
        this.attackRange = attackRange;
    }

    private boolean attack() {
        GameObject target = detector.detect(
                gameObject,
                candidate -> CombatRange.contains(gameObject, candidate, attackRange)
        );

        if (target == null) {
            return false;
        }

        Damageable damageable = target.getComponent(Damageable.class);
        if (damageable == null) {
            return false;
        }

        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(gameObject, target, "RockShot", attackDuration);
        damageable.onDamaged(attackInfo, attackDuration);
        gameObject.setStatus(Status.Attack);
        return true;
    }


    @Override
    public void onDeath() {
        gameObject.destroy();
    }

    @Override
    public void start() {
        this.detector = new ClosestEnemyDetector(getGameContext(), targetMask);
        gameObject.drawCircle(Vector3.ZERO, CombatRange.reachFrom(gameObject, attackRange), GizmoCategory.AttackRange);
    }

    @Override
    public void onDestroy() {

    }
}
