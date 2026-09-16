package com.wordonline.server.game.domain.object.component.mob.simple;

import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.detector.ClosestEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

public class TitanRemnantMob extends TimedBehaviorMob {

    private final float attackRange;
    private Detector detector;

    public TitanRemnantMob(GameObject gameObject, int maxHp, float attackInterval, float attackRange) {
        super(gameObject, maxHp, 0f, attackInterval, null);
        this.attackRange = attackRange;
        setBehavior(this::attack);
    }

    @Override
    public void start() {
        detector = new ClosestEnemyDetector(getGameContext(), TargetMask.ANY.bit);
        gameObject.drawCircle(
                Vector3.ZERO,
                CombatRange.reachFrom(gameObject, attackRange),
                GizmoCategory.AttackRange
        );
    }

    private boolean attack() {
        GameObject target = detector.detect(
                gameObject,
                candidate -> CombatRange.contains(gameObject, candidate, attackRange)
        );
        if (target == null) {
            return false;
        }

        new GameObject(
                gameObject.getMaster(),
                PrefabType.TitanFist,
                new Vector3(target.getPosition()).grounded(),
                getGameContext()
        );
        gameObject.setStatus(Status.Attack);
        return true;
    }

    @Override
    public void onDeath() {
        gameObject.destroy();
    }

    @Override
    public void onDestroy() {
    }
}
