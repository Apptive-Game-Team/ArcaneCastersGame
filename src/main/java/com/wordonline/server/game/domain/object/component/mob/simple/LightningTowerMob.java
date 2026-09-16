package com.wordonline.server.game.domain.object.component.mob.simple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.ClosestEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

public class LightningTowerMob extends TimedBehaviorMob {

    private static final float ATTACK_DURATION = 0.2f;

    private final int damage;
    private final int chainDamage;
    private final int chainCount;
    private final float attackRange;
    private final float chainRadius;
    private Detector detector;

    public LightningTowerMob(GameObject gameObject,
                             int maxHp,
                             int damage,
                             int chainDamage,
                             int chainCount,
                             float attackInterval,
                             float attackRange,
                             float chainRadius) {
        super(gameObject, maxHp, 0, attackInterval, null);
        this.damage = damage;
        this.chainDamage = chainDamage;
        this.chainCount = chainCount;
        this.attackRange = attackRange;
        this.chainRadius = chainRadius;
        setBehavior(this::attack);
    }

    private boolean attack() {
        GameObject target = detector.detect(
                gameObject,
                candidate -> CombatRange.contains(gameObject, candidate, attackRange)
        );
        if (target == null) {
            return false;
        }

        strike(gameObject, target, damage);
        List<GameObject> hitTargets = new ArrayList<>();
        hitTargets.add(target);

        GameObject chainSource = target;
        for (int i = 0; i < chainCount; i++) {
            GameObject next = findNextTarget(chainSource, hitTargets);
            if (next == null) {
                break;
            }
            strike(chainSource, next, chainDamage);
            hitTargets.add(next);
            chainSource = next;
        }

        gameObject.setStatus(Status.Attack);
        return true;
    }

    private void strike(GameObject source, GameObject target, int strikeDamage) {
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(source, target, "ElectricShot", ATTACK_DURATION);
        AttackInfo attackInfo = new AttackInfo(strikeDamage, ElementType.LIGHTNING).withAttacker(gameObject);
        target.getComponents(Damageable.class)
                .forEach(damageable -> damageable.onDamaged(attackInfo, ATTACK_DURATION));
    }

    private GameObject findNextTarget(GameObject source, List<GameObject> hitTargets) {
        return getGameContext().overlapSphereAll(source, chainRadius).stream()
                .filter(target -> TargetRelation.canAttack(gameObject, target))
                .filter(target -> !hitTargets.contains(target))
                .filter(target -> !target.getComponents(Damageable.class).isEmpty())
                .min(Comparator.comparingDouble(target -> target.getPosition().distance(source.getPosition())))
                .orElse(null);
    }

    @Override
    public void onDeath() {
        gameObject.destroy();
    }

    @Override
    public void start() {
        detector = new ClosestEnemyDetector(getGameContext(), TargetMask.ANY.bit);
        gameObject.drawCircle(Vector3.ZERO, CombatRange.reachFrom(gameObject, attackRange), GizmoCategory.AttackRange);
        gameObject.drawCircle(Vector3.ZERO, chainRadius, GizmoCategory.DetectionRange);
    }

    @Override
    public void onDestroy() {
    }
}
