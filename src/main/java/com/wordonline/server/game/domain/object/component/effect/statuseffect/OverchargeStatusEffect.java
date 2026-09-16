package com.wordonline.server.game.domain.object.component.effect.statuseffect;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.util.CombatRange;

import java.util.Comparator;

public class OverchargeStatusEffect extends BaseStatusEffect {
    private static final float SPEED_MULTIPLIER = 1.5f;
    private static final float PROJECTILE_RANGE = 2f;
    private static final float PROJECTILE_INTERVAL = 1f;
    private static final float PROJECTILE_DURATION = 0.2f;
    private static final String PROJECTILE_TYPE = "ElectricShot";

    private boolean speedBonusApplied;
    private float projectileTimer;

    public static void apply(GameObject owner, float duration) {
        for (OverchargeStatusEffect existing : owner.getComponents(OverchargeStatusEffect.class)) {
            existing.extendAndReactivate(duration);
            return;
        }
        for (Component component : owner.getComponentsToAdd()) {
            if (component instanceof OverchargeStatusEffect existing) {
                existing.extend(duration);
                return;
            }
        }

        owner.addComponent(new OverchargeStatusEffect(owner, duration));
    }

    private void extendAndReactivate(float duration) {
        boolean pendingRemoval = gameObject.getComponentsToRemove().remove(this);
        if (pendingRemoval) {
            remaining = Math.max(remaining, 0f);
            gameObject.addEffect(Effect.Overcharge);
            start();
        }
        extend(duration);
    }

    public OverchargeStatusEffect(GameObject owner, float duration) {
        super(owner, duration, StatusEffectKey.Overcharge_Receive, Effect.Overcharge);
    }

    @Override
    public void start() {
        Mob mob = gameObject.getComponent(Mob.class);
        if (mob == null || speedBonusApplied) {
            return;
        }

        mob.getSpeed().setMultiplier(this, SPEED_MULTIPLIER);
        speedBonusApplied = true;
    }

    @Override
    public void update() {
        float activeDelta = Math.min(getGameContext().getDeltaTime(), Math.max(remaining, 0f));
        super.update();

        projectileTimer += activeDelta;
        while (projectileTimer >= PROJECTILE_INTERVAL) {
            projectileTimer -= PROJECTILE_INTERVAL;
            emitProjectile();
        }
    }

    private void emitProjectile() {
        findClosestEnemy().ifPresent(target -> {
            getGameContext().getObjectsInfoDtoBuilder()
                    .createProjection(gameObject, target, PROJECTILE_TYPE, PROJECTILE_DURATION);

            int damage = getGameContext().getParameters()
                    .object(GameObjectKey.ELECTRIC_SHOT)
                    .intValue(ParameterKey.DAMAGE);
            AttackInfo attackInfo = new AttackInfo(damage, ElementType.LIGHTNING);
            target.getComponents(Damageable.class)
                    .forEach(damageable -> damageable.onDamaged(attackInfo, PROJECTILE_DURATION));
        });
    }

    private java.util.Optional<GameObject> findClosestEnemy() {
        return getGameContext().getActiveGameObjects().stream()
                .filter(target -> target.getMaster() != gameObject.getMaster())
                .filter(target -> !target.getComponents(Damageable.class).isEmpty())
                .filter(target -> CombatRange.contains(gameObject, target, PROJECTILE_RANGE))
                .min(Comparator.comparingDouble(
                        target -> target.getPosition().distance(gameObject.getPosition())));
    }

    @Override
    public void onAttacked(ElementType attackType) {
    }

    @Override
    protected void expire() {
        removeSpeedBonus();
        super.expire();
    }

    @Override
    public void onDestroy() {
        removeSpeedBonus();
        super.onDestroy();
    }

    private void removeSpeedBonus() {
        Mob mob = gameObject.getComponent(Mob.class);
        if (mob == null || !speedBonusApplied) {
            return;
        }

        mob.getSpeed().removeMultiplier(this);
        speedBonusApplied = false;
    }
}
