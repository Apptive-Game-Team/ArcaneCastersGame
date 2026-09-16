package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.debug.GizmoCategory;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.dto.Status;

import java.util.ArrayList;
import java.util.List;

public class ChainShot extends Shot implements Collidable {
    private int damage;
    private final float speed;
    private final float chainRadius;
    private final int minDamage;

    private Vector3 direction;
    private final List<GameObject> hitList = new ArrayList<>();
    private int chainCount;
    private static final float CHAIN_DELAY = 0.12f;
    private static final int CHAIN_DAMAGE_DIVISOR = 2;
    private static final int CHAIN_COUNT_CAP = 5;
    private boolean isActive = true;
    private float activeTimer;

    public ChainShot(GameObject gameObject, int damage, float speed, float chainRadius, int minDamage) {
        super(gameObject, damage, speed);
        this.damage = damage;
        this.speed = speed;
        this.chainRadius = chainRadius;
        this.minDamage = minDamage;
    }

    public void setTarget(Vector3 targetPosition) {
        this.direction = targetPosition.subtract(gameObject.getPosition()).normalize();
    }

    @Override
    public void start() {
        super.start();
        gameObject.drawCircle(Vector3.ZERO, chainRadius, GizmoCategory.DetectionRange);
    }

    @Override
    public void update() {
        if(!isActive)
        {
            if(activeTimer >= CHAIN_DELAY) {
                isActive = true;
                activeTimer = 0;
                return;
            }
            activeTimer += getGameContext().getDeltaTime();
            return;
        }

        if (direction == null || direction.equals(Vector3.ZERO)) return;
        gameObject.setPosition(
                gameObject.getPosition()
                        .plus(direction.multiply(speed * getGameContext().getDeltaTime()))
        );
    }

    @Override
    public void onCollisionWithEnemy(GameObject other) {
        if(!isActive) return;
        if (other.getMaster() == gameObject.getMaster()) return;

        List<Damageable> parts = other.getComponents(Damageable.class);
        if (parts.isEmpty()) return;
        if (hitList.contains(other)) return;

        other.setStatus(Status.Damaged);
        AttackInfo info = new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject);
        parts.forEach(p -> p.onDamaged(info));
        damage = Math.max(damage / CHAIN_DAMAGE_DIVISOR, minDamage);
        chainCount++;
        isActive = false;
        if(chainCount >= CHAIN_COUNT_CAP) gameObject.destroy();
        hitList.add(other);

        GameObject next = findNextTarget();
        if (next == null) {
            gameObject.destroy();
            return;
        }

        gameObject.setPosition(other.getPosition());
        setTarget(next.getPosition());
    }

    private GameObject findNextTarget() {
        List<GameObject> around = getGameContext().overlapSphereAll(gameObject, chainRadius);
        return around.stream()
                .filter(go -> go != gameObject)
                .filter(go -> go.getMaster() != gameObject.getMaster())
                .filter(go -> !hitList.contains(go))
                .filter(go -> !go.getComponents(Damageable.class).isEmpty())
                .findFirst()
                .orElse(null);
    }
}
