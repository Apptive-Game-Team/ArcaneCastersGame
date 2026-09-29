package com.wordonline.server.game.domain.object.component.magic;

import java.util.List;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;

public class OvergrowthExplosion extends MagicComponent {
    private static final float EFFECT_DELAY = 0.5f;
    private static final float SUMMON_SPACING = 0.5f;

    private final float effectRadius;
    private final int summonCount;
    private final int damage;
    private float counter = 0f;
    private boolean isRunning = true;

    public OvergrowthExplosion(GameObject gameObject, float effectRadius, int summonCount, int damage) {
        super(gameObject);
        this.effectRadius = effectRadius;
        this.summonCount = Math.max(1, summonCount);
        this.damage = damage;
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, effectRadius, GizmoCategory.AreaOfEffect);
    }

    @Override
    public void update() {
        if (!isRunning) {
            return;
        }

        if (counter < EFFECT_DELAY) {
            counter += getGameContext().getDeltaTime();
            return;
        }

        Master owner = gameObject.getMaster();
        bindEnemies(owner);

        int transformedCount = SeedSpiritEvolver.evolveAlliedSeedSpirits(
                gameObject,
                effectRadius,
                PrefabType.TreeGolem
        );

        if (transformedCount == 0) {
            summonSeedSpirits(owner);
        }

        isRunning = false;
        gameObject.destroy();
    }

    // 범위에 걸친 적을 속박하고 피해를 준다. 아군 SeedSpirit 진화와는 별개로 항상 일어난다.
    private void bindEnemies(Master owner) {
        if (owner == Master.None) {
            return;
        }

        AttackInfo attackInfo = new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);
        for (GameObject target : getGameContext().overlapSphereAll(gameObject, effectRadius)) {
            if (target == gameObject || target.isDestroyed()) {
                continue;
            }
            if (target.getMaster() == owner || target.getMaster() == Master.None) {
                continue;
            }

            List<Damageable> damageables = target.getComponents(Damageable.class);
            if (damageables.isEmpty()) {
                continue;
            }

            target.setStatus(Status.Damaged);
            damageables.forEach(damageable -> damageable.onDamaged(attackInfo));

            EffectReceiver effectReceiver = target.getComponent(EffectReceiver.class);
            if (effectReceiver != null) {
                effectReceiver.onReceive(Effect.Snared);
            }
        }
    }

    private void summonSeedSpirits(Master owner) {
        Vector3 basePosition = gameObject.getPosition();
        float centerOffset = (summonCount - 1) / 2f;

        for (int i = 0; i < summonCount; i++) {
            float offsetX = (i - centerOffset) * SUMMON_SPACING;
            Vector3 summonPosition = basePosition.plus(offsetX, 0, 0);
            new GameObject(owner, PrefabType.SeedSpirit, summonPosition, getGameContext());
        }
    }
}
