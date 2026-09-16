package com.wordonline.server.game.domain.object.component.magic;

import java.util.List;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.EffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;

public class Leafair extends Drop {
    private final int healAmount;
    private final float radius;

    public Leafair(GameObject gameObject, int damage, int healAmount, float radius) {
        super(gameObject, damage);
        this.healAmount = healAmount;
        this.radius = radius;
    }

    @Override
    public void update() {
        super.update();

        if (gameObject.isDestroyed()) {
            return;
        }

        for (GameObject target : getGameContext().overlapSphereAll(gameObject, radius)) {
            if (target == gameObject || target.isDestroyed()) {
                continue;
            }

            Master owner = gameObject.getMaster();
            if (owner != Master.None && target.getMaster() == owner) {
                if (handleAlly(target)) {
                    gameObject.setStatus(Status.Attack);
                    gameObject.destroy();
                    return;
                }
                continue;
            }

            if (handleEnemy(target)) {
                gameObject.destroy();
                return;
            }
        }
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        // Leafair uses overlapSphereAll in update because same-owner collisions are filtered out.
    }

    private boolean handleAlly(GameObject target) {
        boolean handled = false;

        List<Mob> mobs = target.getComponents(Mob.class);
        if (!mobs.isEmpty()) {
            AttackInfo heal = new AttackInfo(-healAmount, ElementType.NONE);
            mobs.forEach(mob -> mob.onDamaged(heal));
            handled = true;
        }

        TimedSelfDestroyer timedSelfDestroyer = target.getComponent(TimedSelfDestroyer.class);
        if (timedSelfDestroyer != null) {
            timedSelfDestroyer.recover();
            handled = true;
        }

        return handled;
    }

    private boolean handleEnemy(GameObject target) {
        List<Damageable> damageables = target.getComponents(Damageable.class);
        if (damageables.isEmpty()) {
            return false;
        }

        target.setStatus(Status.Damaged);

        AttackInfo attackInfo = new AttackInfo(getDamage(), gameObject.getElement().total()).withAttacker(gameObject);
        damageables.forEach(damageable -> damageable.onDamaged(attackInfo));

        EffectReceiver effectReceiver = target.getComponent(EffectReceiver.class);
        if (effectReceiver != null) {
            effectReceiver.onReceive(Effect.Snared);
        }

        gameObject.setStatus(Status.Attack);
        return true;
    }
}
