package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.EffectApplication;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Status;

public class EffectProvideProjectileRangeAttackMob extends BehaviorMob {

    public EffectProvideProjectileRangeAttackMob(
            GameObject gameObject,
            int maxHp, float speed, int targetMask, int damage, float attackInterval, float attackRange, Effect effect, String projectileType, float projectileDuration) {
        super(gameObject, maxHp, speed, targetMask, attackInterval, attackRange, (target) -> {
            gameObject.getGameContext()
                    .getObjectsInfoDtoBuilder()
                    .createProjection(gameObject, target, projectileType, projectileDuration);

            target.getComponentOptional(Damageable.class)
                    .ifPresent(damageable -> {
                        damageable.onDamaged(new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject));
                        gameObject.setStatus(Status.Attack);
                    });

            target.getComponentOptional(EffectReceiver.class)
                    .ifPresent(effectReceiver -> {
                        effectReceiver.onReceive(new EffectApplication(effect, gameObject));
                    });

            return true;
        });
    }
}
