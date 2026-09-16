package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import lombok.Getter;

import java.util.List;

public class Shot extends MagicComponent implements Collidable {
    protected float speed;

    private static final float IMPACT_EXPLOSION_RADIUS = 1f;

    @Getter
    private Vector3 direction;
    protected final int damage;

    private boolean explosionTriggered = false;

    public Shot(GameObject gameObject, int damage, float speed) {
        super(gameObject);
        this.damage = damage;
        this.speed = speed;
    }

    public void setTarget(Vector3 targetPosition) {
        direction = (targetPosition.subtract(gameObject.getPosition())).normalize();
    }

    @Override
    public void start() {
        gameObject.drawCircle(Vector3.ZERO, IMPACT_EXPLOSION_RADIUS, GizmoCategory.AreaOfEffect);
    }

    @Override
    public void update() {
        if (direction == null) return;
        gameObject.setPosition(
                gameObject.getPosition()
                        .plus(direction.multiply(speed * getGameContext().getDeltaTime()))
        );
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        // 이미 폭발 시도했다면 무시(다중 충돌 방지)
        if (explosionTriggered) return;

        List<Damageable> attackables = otherObject.getComponents(Damageable.class);
        if (attackables.isEmpty()) return;

        explosionTriggered = true;

        direction = Vector3.ZERO;
        gameObject.setStatus(Status.Attack);

        List<GameObject> gameObjects = getGameContext().overlapSphereAll(gameObject, IMPACT_EXPLOSION_RADIUS);

        for (GameObject go : gameObjects) {
            if (go == gameObject || go.getMaster() == gameObject.getMaster()) continue;

            List<Damageable> attackable = go.getComponents(Damageable.class);
            if (attackable.isEmpty()) continue;

            go.setStatus(Status.Damaged);
            AttackInfo info = new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);
            attackable.forEach(a -> a.onDamaged(info));
        }
        gameObject.destroy();
    }
}
