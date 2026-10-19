package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

// The host is usually the first object of the line, and it can die before the line is finished:
// a vine lives 0.5s while nine vines 0.12s apart take 0.96s. So the spawner moves onto the object
// it spawned last. Spawn times count from the cast frame, so a move does not delay the next spawn.
public class SequentialLineSpawner extends Component {

    private final Master master;
    private final PrefabType prefabType;
    private final Vector3 startPosition;
    private final Vector3 direction;
    private final float spacing;
    private final float spawnInterval;
    private final int totalSpawnCount;
    private final VineHitTracker hitTracker;
    private final int startFrame;
    private int nextSpawnIndex;

    public SequentialLineSpawner(GameObject gameObject,
                                 Master master,
                                 PrefabType prefabType,
                                 Vector3 startPosition,
                                 Vector3 direction,
                                 float spacing,
                                 float spawnInterval,
                                 int totalSpawnCount,
                                 VineHitTracker hitTracker) {
        super(gameObject);
        this.master = master;
        this.prefabType = prefabType;
        this.startPosition = startPosition;
        this.direction = direction.normalize();
        this.spacing = spacing;
        this.spawnInterval = spawnInterval;
        this.totalSpawnCount = totalSpawnCount;
        this.hitTracker = hitTracker;
        this.startFrame = gameObject.getGameContext().getFrameNum();
        this.nextSpawnIndex = 1;
    }

    private SequentialLineSpawner(GameObject gameObject, SequentialLineSpawner previous) {
        super(gameObject);
        this.master = previous.master;
        this.prefabType = previous.prefabType;
        this.startPosition = previous.startPosition;
        this.direction = previous.direction;
        this.spacing = previous.spacing;
        this.spawnInterval = previous.spawnInterval;
        this.totalSpawnCount = previous.totalSpawnCount;
        this.hitTracker = previous.hitTracker;
        this.startFrame = previous.startFrame;
        this.nextSpawnIndex = previous.nextSpawnIndex;
    }

    @Override
    public void start() {
    }

    @Override
    public void update() {
        float elapsed = (getGameContext().getFrameNum() - startFrame) * getGameContext().getDeltaTime();
        GameObject lastSpawned = null;
        while (nextSpawnIndex < totalSpawnCount && elapsed >= spawnInterval * nextSpawnIndex) {
            Vector3 spawnPosition = startPosition.plus(direction.multiply(spacing * nextSpawnIndex));
            GameObject[] spawnedRef = new GameObject[1];
            VineSpawnContext.runWithTracker(hitTracker, () ->
                    spawnedRef[0] = new GameObject(master, prefabType, spawnPosition, getGameContext())
            );
            lastSpawned = spawnedRef[0];
            nextSpawnIndex++;
        }

        if (nextSpawnIndex >= totalSpawnCount) {
            gameObject.removeComponent(this);
            return;
        }

        if (lastSpawned != null && !lastSpawned.isDestroyed()) {
            lastSpawned.addComponent(new SequentialLineSpawner(lastSpawned, this));
            gameObject.removeComponent(this);
        }
    }

    @Override
    public void onDestroy() {
    }
}
