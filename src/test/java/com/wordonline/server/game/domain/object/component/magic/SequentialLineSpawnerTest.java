package com.wordonline.server.game.domain.object.component.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.config.GameTickProperties;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

class SequentialLineSpawnerTest {

    private static final int VINE_COUNT = 9;
    private static final float VINE_SPACING = 1f;
    private static final float VINE_SPAWN_INTERVAL = 0.12f;
    private static final int TICK_RATE = GameTickProperties.DEFAULT_TICK_RATE;
    // vine duration is 0.5s
    private static final int VINE_LIFETIME_FRAMES = TICK_RATE / 2;

    private final List<GameObject> created = new ArrayList<>();
    private final Map<GameObject, Integer> createdFrame = new HashMap<>();
    private int frameNum;

    @Test
    void finishesTheLineAfterTheFirstVineExpires() {
        GameContext gameContext = mockGameContext();
        GameObject firstVine = new GameObject(Master.LeftPlayer, PrefabType.Vine, Vector3.ZERO, gameContext);
        firstVine.addComponent(new SequentialLineSpawner(
                firstVine,
                Master.LeftPlayer,
                PrefabType.Vine,
                Vector3.ZERO,
                new Vector3(1, 0, 0),
                VINE_SPACING,
                VINE_SPAWN_INTERVAL,
                VINE_COUNT,
                new VineHitTracker()
        ));

        for (int frame = 0; frame < TICK_RATE * 2; frame++) {
            runFrame();
        }

        assertThat(created).hasSize(VINE_COUNT);
        assertThat(created.get(VINE_COUNT - 1).getPosition().getX())
                .isEqualTo(VINE_SPACING * (VINE_COUNT - 1));
    }

    @Test
    void spawnsOnTheCastScheduleEvenThoughTheSpawnerMovesBetweenVines() {
        GameContext gameContext = mockGameContext();
        GameObject firstVine = new GameObject(Master.LeftPlayer, PrefabType.Vine, Vector3.ZERO, gameContext);
        firstVine.addComponent(new SequentialLineSpawner(
                firstVine,
                Master.LeftPlayer,
                PrefabType.Vine,
                Vector3.ZERO,
                new Vector3(1, 0, 0),
                VINE_SPACING,
                VINE_SPAWN_INTERVAL,
                VINE_COUNT,
                new VineHitTracker()
        ));

        for (int frame = 0; frame < TICK_RATE * 2; frame++) {
            runFrame();
        }

        float frameTime = 1f / TICK_RATE;
        for (int index = 1; index < VINE_COUNT; index++) {
            float spawnTime = createdFrame.get(created.get(index)) * frameTime;
            assertThat(spawnTime).isBetween(
                    VINE_SPAWN_INTERVAL * index,
                    VINE_SPAWN_INTERVAL * index + frameTime * 2);
        }
    }

    private GameContext mockGameContext() {
        GameContext gameContext = mock(GameContext.class);
        when(gameContext.getDeltaTime()).thenReturn(1f / TICK_RATE);
        when(gameContext.getFrameNum()).thenAnswer(invocation -> frameNum);
        doAnswer(invocation -> {
            GameObject gameObject = invocation.getArgument(0);
            created.add(gameObject);
            createdFrame.put(gameObject, frameNum);
            return null;
        }).when(gameContext).createGameObject(any());
        return gameContext;
    }

    // update, then flush added and removed components, then expire vines: the order the loop runs
    private void runFrame() {
        frameNum++;
        List<GameObject> alive = created.stream().filter(gameObject -> !gameObject.isDestroyed()).toList();
        for (GameObject gameObject : alive) {
            new ArrayList<>(gameObject.getComponents()).forEach(component -> component.update());
        }
        for (GameObject gameObject : alive) {
            gameObject.flushComponents();
        }
        for (GameObject gameObject : alive) {
            if (frameNum - createdFrame.get(gameObject) >= VINE_LIFETIME_FRAMES) {
                gameObject.destroy();
            }
        }
    }
}
