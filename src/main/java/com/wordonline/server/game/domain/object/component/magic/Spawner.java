package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.component.mob.Mob;

public class Spawner extends Mob {
    public static final float DEFAULT_SPAWN_INTERVAL_SEC = 2f;
    // Shared with PveScriptSystem's SpawnWave action, which spreads its wave the same way.
    public static final float DEFAULT_BURST_SPACING = 0.5f;

    private float counter = 0;
    private boolean isRunning = false;
    private final PrefabType prefabType;
    private final float spawnIntervalSec;
    private final boolean destructible;
    private final int spawnCount;

    @Override
    public void start() {

    }

    @Override
    public void update() {
        super.update();
        if (!isRunning) {
            return;
        }

        if (counter < spawnIntervalSec) {
            counter += getGameContext().getDeltaTime();
        } else {
            counter = 0;
            spawnPrefabs();
            if (destructible) {
                onDamaged(new AttackInfo(1, ElementType.NONE));
            }
        }
    }

    @Override
    public void onDestroy() {

    }

    public Spawner(GameObject gameObject, int maxHp, PrefabType prefabType) {
        this(gameObject, maxHp, prefabType, DEFAULT_SPAWN_INTERVAL_SEC, true, 1);
    }

    public Spawner(GameObject gameObject,
                   int maxHp,
                   PrefabType prefabType,
                   float spawnIntervalSec,
                   boolean destructible,
                   int spawnCount) {
        super(gameObject, maxHp, 0);
        this.prefabType = prefabType;
        this.spawnIntervalSec = Math.max(0.1f, spawnIntervalSec);
        this.destructible = destructible;
        this.spawnCount = Math.max(1, spawnCount);
        isRunning = true;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    @Override
    public void onDamaged(AttackInfo attackInfo) {
        if (!destructible) {
            return;
        }
        hp -= attackInfo.getDamage();
        getGameContext().updateGameObject(gameObject);
        if (hp <= 0) {
            gameObject.destroy();
        }
    }

    @Override
    public void onDeath() {

    }

    private void spawnPrefabs() {
        Vector3 basePosition = gameObject.getPosition();
        if (spawnCount == 1) {
            new GameObject(gameObject, prefabType);
            return;
        }

        float centerOffset = (spawnCount - 1) / 2f;
        for (int i = 0; i < spawnCount; i++) {
            float offsetX = (i - centerOffset) * DEFAULT_BURST_SPACING;
            Vector3 spawnPosition = new Vector3(
                    basePosition.getX() + offsetX,
                    basePosition.getY(),
                    basePosition.getZ()
            );
            new GameObject(gameObject.getMaster(), prefabType, spawnPosition, getGameContext());
        }
    }
}
