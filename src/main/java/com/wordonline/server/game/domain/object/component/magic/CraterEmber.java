package com.wordonline.server.game.domain.object.component.magic;

import java.util.concurrent.ThreadLocalRandom;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;

public class CraterEmber extends MagicComponent implements Collidable {
    private static final float MIN_TRAVEL_DISTANCE = 1.0f;
    private static final float HORIZONTAL_SPEED = 4.0f;
    private static final float ARC_PEAK_HEIGHT = 3.0f;

    private final int damage;
    private final float maxTravelDistance;

    private Vector3 startPosition;
    private Vector3 horizontalDirection;
    private Vector3 landingPosition;
    private float travelDuration;
    private float elapsed;
    private boolean resolved;

    public CraterEmber(GameObject gameObject, int damage, float maxTravelDistance) {
        super(gameObject);
        this.damage = damage;
        this.maxTravelDistance = Math.max(MIN_TRAVEL_DISTANCE, maxTravelDistance);
    }

    @Override
    public void start() {
        startPosition = new Vector3(gameObject.getPosition());

        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        horizontalDirection = new Vector3((float) Math.cos(angle), 0f, (float) Math.sin(angle));

        float distance = ThreadLocalRandom.current().nextFloat(MIN_TRAVEL_DISTANCE, maxTravelDistance);
        landingPosition = startPosition.plus(horizontalDirection.multiply(distance));
        travelDuration = distance / HORIZONTAL_SPEED;
    }

    @Override
    public void update() {
        if (resolved) {
            return;
        }

        elapsed += getGameContext().getDeltaTime();

        if (elapsed >= travelDuration) {
            land();
            return;
        }

        float horizontalDistance = HORIZONTAL_SPEED * elapsed;
        float progress = elapsed / travelDuration;
        float y = 4f * ARC_PEAK_HEIGHT * progress * (1f - progress);
        Vector3 nextPosition = startPosition.plus(horizontalDirection.multiply(horizontalDistance));
        gameObject.setPosition(new Vector3(nextPosition.getX(), Math.max(y, 0f), nextPosition.getZ()));
    }

    @Override
    public void onCollisionWithEnemy(GameObject otherObject) {
        if (resolved || !isEnemySummon(otherObject)) {
            return;
        }

        Mob mob = otherObject.getComponent(Mob.class);
        if (mob == null) {
            return;
        }

        otherObject.setStatus(Status.Damaged);
        mob.onDamaged(new AttackInfo(damage, gameObject.getElement().total()).withAttacker(gameObject));
        resolved = true;
        gameObject.destroy();
    }

    private void land() {
        resolved = true;
        gameObject.setPosition(new Vector3(landingPosition.getX(), 0f, landingPosition.getZ()));
        new GameObject(Master.None, PrefabType.FireField, gameObject.getPosition(), getGameContext());
        gameObject.destroy();
    }

    private boolean isEnemySummon(GameObject otherObject) {
        if (otherObject == gameObject) {
            return false;
        }
        if (otherObject.getMaster() == gameObject.getMaster()) {
            return false;
        }
        if (!otherObject.hasComponent(Mob.class)) {
            return false;
        }
        return true;
    }
}
