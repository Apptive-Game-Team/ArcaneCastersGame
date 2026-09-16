package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetRelation;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Status;

import java.util.List;

public class TidalWarheadProjectile extends MagicComponent implements Collidable {

    private static final float GROUND_DAMAGE_MULTIPLIER = 0.5f;

    private final int baseDamage;
    private final float speed;
    private final float explosionRadius;

    private GameObject target;
    private Vector3 lastTargetPosition;
    private boolean aerialTarget;
    private boolean exploded;

    public TidalWarheadProjectile(GameObject gameObject, int baseDamage, float speed, float explosionRadius) {
        super(gameObject);
        this.baseDamage = baseDamage;
        this.speed = speed;
        this.explosionRadius = explosionRadius;
    }

    public void setTarget(GameObject target, boolean aerialTarget) {
        this.target = target;
        this.lastTargetPosition = new Vector3(target.getPosition());
        this.aerialTarget = aerialTarget;
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, explosionRadius, GizmoCategory.AreaOfEffect);
    }

    @Override
    public void update() {
        if (exploded || lastTargetPosition == null) {
            return;
        }

        if (target != null && target.isActive()) {
            lastTargetPosition = new Vector3(target.getPosition());
        }

        Vector3 position = gameObject.getPosition();
        Vector3 toTarget = lastTargetPosition.subtract(position);
        float travelDistance = speed * getGameContext().getDeltaTime();

        if (position.distance(lastTargetPosition) <= travelDistance) {
            gameObject.setPosition(new Vector3(lastTargetPosition));
            explode();
            return;
        }

        gameObject.setPosition(position.plus(toTarget.normalize().multiply(travelDistance)));
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (exploded || otherObject.getComponents(Damageable.class).isEmpty()) {
            return;
        }

        explode();
    }

    private void explode() {
        if (exploded) {
            return;
        }
        exploded = true;

        gameObject.setStatus(Status.Attack);
        new GameObject(
                gameObject.getMaster(),
                PrefabType.TidalWarheadExplosion,
                new Vector3(gameObject.getPosition()),
                getGameContext());

        int damage = aerialTarget
                ? baseDamage
                : Math.round(baseDamage * GROUND_DAMAGE_MULTIPLIER);
        AttackInfo attackInfo = new AttackInfo(damage, ElementType.WATER).withAttacker(gameObject);

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
