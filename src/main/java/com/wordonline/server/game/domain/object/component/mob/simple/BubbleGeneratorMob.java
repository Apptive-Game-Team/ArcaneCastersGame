package com.wordonline.server.game.domain.object.component.mob.simple;

import java.util.Comparator;

import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.BaseStatusEffect;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.util.CombatRange;

public class BubbleGeneratorMob extends TimedBehaviorMob {

    private static final String PROJECTILE_TYPE = "WaterShot";

    private final float attackRange;
    private final float projectileSpeed;

    public BubbleGeneratorMob(GameObject gameObject, int maxHp, float attackInterval, float attackRange, float projectileSpeed) {
        super(gameObject, maxHp, 0, attackInterval, null);
        this.attackRange = attackRange;
        this.projectileSpeed = projectileSpeed;
        setBehavior(this::shootBubble);
    }

    private boolean shootBubble() {
        GameObject target = findTarget();
        if (target == null) {
            return false;
        }

        float projectileDuration = (float) (target.getPosition().distance(gameObject.getPosition()) / projectileSpeed);
        getGameContext().getObjectsInfoDtoBuilder()
                .createProjection(gameObject, target, PROJECTILE_TYPE, projectileDuration);

        target.getComponent(EffectReceiver.class).onReceive(Effect.Bubble);
        gameObject.setStatus(Status.Attack);
        return true;
    }

    private GameObject findTarget() {
        return getGameContext().getGameSessionData().gameObjects.stream()
                .filter(target -> target != gameObject)
                .filter(GameObject::isActive)
                .filter(target -> CombatRange.contains(gameObject, target, attackRange))
                .filter(target -> target.getMaster() == gameObject.getMaster())
                .filter(target -> target.getComponent(EffectReceiver.class) != null)
                .filter(target -> !target.getComponents(Damageable.class).isEmpty())
                .filter(target -> target.getComponents(BaseStatusEffect.class).stream()
                        .noneMatch(effect -> effect.getKey() == StatusEffectKey.Bubble_Receive))
                .min(Comparator.comparingDouble(target -> target.getPosition().distance(gameObject.getPosition())))
                .orElse(null);
    }

    @Override
    public void onDeath() {
        gameObject.destroy();
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, CombatRange.reachFrom(gameObject, attackRange), GizmoCategory.AttackRange);
    }

    @Override
    public void onDestroy() {
    }
}
