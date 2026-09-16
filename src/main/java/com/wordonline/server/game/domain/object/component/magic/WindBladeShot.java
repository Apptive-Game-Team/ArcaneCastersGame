package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WindBladeShot extends Shot {

    private static final int DAMAGE_DECAY_NUMERATOR = 2;
    private static final int DAMAGE_DECAY_DENOMINATOR = 3;

    private final Set<Integer> piercedTargetIds = new HashSet<>();
    private int currentDamage;

    public WindBladeShot(GameObject gameObject, int damage, float speed) {
        super(gameObject, damage, speed);
        this.currentDamage = damage;
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (otherObject == gameObject) {
            return;
        }

        if (piercedTargetIds.contains(otherObject.getId())) {
            return;
        }

        Master owner = gameObject.getMaster();
        if (owner != Master.None && otherObject.getMaster() == owner) {
            return;
        }

        List<Damageable> damageables = otherObject.getComponents(Damageable.class);
        if (damageables.isEmpty()) {
            return;
        }

        piercedTargetIds.add(otherObject.getId());

        otherObject.setStatus(Status.Damaged);

        AttackInfo attackInfo = new AttackInfo(currentDamage, gameObject.getElement().total()).withAttacker(gameObject);
        damageables.forEach(damageable -> damageable.onDamaged(attackInfo));
        currentDamage = currentDamage * DAMAGE_DECAY_NUMERATOR / DAMAGE_DECAY_DENOMINATOR;
    }
}
