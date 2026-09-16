package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.component.physic.TimedMassPush;
import com.wordonline.server.game.dto.Status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PushShot extends Shot implements Collidable {

    private static final float PUSH_DURATION = 0.5f;

    private final Set<Integer> damagedTargetIds = new HashSet<>();

    public PushShot(GameObject gameObject, int damage, float speed) {
        super(gameObject, damage, speed);
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (otherObject == gameObject) return;
        if (otherObject.getMaster() == gameObject.getMaster()) return;

        List<Damageable> damageables = otherObject.getComponents(Damageable.class);
        if (damageables.isEmpty()) return;

        Vector3 direction = getDirection();
        if (direction != null) {
            TimedMassPush.apply(otherObject, gameObject, direction, speed, PUSH_DURATION);
        }

        if (!damagedTargetIds.add(otherObject.getId())) return;

        AttackInfo info = new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);
        otherObject.setStatus(Status.Damaged);
        damageables.forEach(damageable -> damageable.onDamaged(info));
    }
}
