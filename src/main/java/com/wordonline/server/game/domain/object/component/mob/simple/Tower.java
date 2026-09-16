package com.wordonline.server.game.domain.object.component.mob.simple;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.IntervalAttacker;
import com.wordonline.server.game.domain.object.component.mob.detector.ClosestEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;
import lombok.Getter;

import java.util.List;

public class Tower extends Component implements IntervalAttacker {

    private static final float DEFAULT_ATTACK_DURATION = 0.2f;
    private static final float SPLASH_RADIUS = 1f;

    @Getter
    private final Stat attackInterval;
    private final AttackInfo attackInfo;
    private final int targetMask;
    private final float attackDuration;
    private final float attackRange;
    private Detector detector;
    private float timer;

    public Tower(GameObject gameObject, int damage, int targetMask, float attackInterval, float attackRange) {
        this(gameObject, damage, targetMask, DEFAULT_ATTACK_DURATION, attackInterval, attackRange);
    }

    public Tower(GameObject gameObject, int maxHp, int damage, int targetMask, float attackInterval, float attackRange) {
        this(gameObject, damage, targetMask, DEFAULT_ATTACK_DURATION, attackInterval, attackRange);
    }

    public Tower(GameObject gameObject, int maxHp, int damage, int targetMask, float attackDuration, float attackInterval, float attackRange) {
        this(gameObject, damage, targetMask, attackDuration, attackInterval, attackRange);
    }

    public Tower(GameObject gameObject, int damage, int targetMask, float attackDuration, float attackInterval, float attackRange) {
        super(gameObject);
        this.attackInfo = new AttackInfo(damage, ElementType.ROCK).withAttacker(gameObject);
        this.targetMask = targetMask;
        this.attackDuration = attackDuration;
        this.attackInterval = new Stat(attackInterval);
        this.attackRange = attackRange;
    }

    @Override
    public void start() {
        detector = new ClosestEnemyDetector(getGameContext(), targetMask);
        gameObject.drawCircle(Vector3.ZERO, CombatRange.reachFrom(gameObject, attackRange), GizmoCategory.AttackRange);
    }

    @Override
    public void update() {
        timer += getGameContext().getDeltaTime();
        if (timer < attackInterval.total()) {
            return;
        }

        if (attack()) {
            timer = 0f;
        }
    }

    @Override
    public void onDestroy() {
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

        applySplashDamage(target);
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(gameObject, target, "RockShot", attackDuration);
        gameObject.setStatus(Status.Attack);
        return true;
    }

    private void applySplashDamage(GameObject centerTarget) {
        List<GameObject> victims = getGameContext().overlapSphereAll(centerTarget, SPLASH_RADIUS);
        for (GameObject candidate : victims) {
            if (!TargetRelation.canAttack(gameObject, candidate)) {
                continue;
            }

            Damageable damageable = candidate.getComponent(Damageable.class);
            if (damageable == null) {
                continue;
            }

            damageable.onDamaged(attackInfo, attackDuration);
        }
    }
}
