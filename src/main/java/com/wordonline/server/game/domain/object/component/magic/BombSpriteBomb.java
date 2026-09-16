package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Status;

import java.util.List;

public class BombSpriteBomb extends MagicComponent {

    private final int damage;
    private final float fallSpeed;
    private final float explosionRadius;

    private Vector3 startPosition;
    private Vector3 targetPosition;
    private float duration;
    private float elapsed;
    private boolean exploded;

    public BombSpriteBomb(GameObject gameObject, int damage, float fallSpeed, float explosionRadius) {
        super(gameObject);
        this.damage = damage;
        this.fallSpeed = fallSpeed;
        this.explosionRadius = explosionRadius;
    }

    public void setTarget(Vector3 targetPosition) {
        this.startPosition = new Vector3(gameObject.getPosition());
        this.targetPosition = targetPosition.grounded();
        this.duration = Math.max(
                0.1f,
                (float) startPosition.distance(this.targetPosition) / fallSpeed);
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
        if (exploded || targetPosition == null) {
            return;
        }

        elapsed += getGameContext().getDeltaTime();
        float progress = Math.clamp(elapsed / duration, 0f, 1f);
        gameObject.setPosition(Vector3.lerp(startPosition, targetPosition, progress));

        if (progress >= 1f) {
            explode();
        }
    }

    private void explode() {
        if (exploded) {
            return;
        }
        exploded = true;

        gameObject.setStatus(Status.Attack);
        new GameObject(
                gameObject.getMaster(),
                PrefabType.BombSpriteExplosion,
                new Vector3(gameObject.getPosition()),
                getGameContext());

        AttackInfo attackInfo = new AttackInfo(damage, gameObject.getElement().total())
                .withAttacker(gameObject);
        List<GameObject> nearbyObjects = getGameContext().overlapSphereAll(gameObject, explosionRadius);
        for (GameObject nearbyObject : nearbyObjects) {
            if (!nearbyObject.isActive() || !TargetRelation.canAttack(gameObject, nearbyObject)) {
                continue;
            }

            List<Damageable> damageables = nearbyObject.getComponents(Damageable.class);
            if (damageables.isEmpty()) {
                continue;
            }

            nearbyObject.setStatus(Status.Damaged);
            damageables.forEach(damageable -> damageable.onDamaged(attackInfo));
        }

        gameObject.destroy();
    }

    @Override
    public void onDestroy() {
    }
}
