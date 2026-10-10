package com.wordonline.server.game.domain.object.component.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

class SpawnerTickRateTest {

    // An interval that is not a whole number of frames at either rate. Resetting the timer to zero
    // on each spawn used to stretch every period to the next whole frame, plus the spawn frame.
    private static final float SPAWN_INTERVAL = 0.7f;

    @ParameterizedTest
    @ValueSource(ints = {20, 60})
    void spawnsAsOftenOverThirtySecondsAtAnyTickRate(int tickRate) {
        GameContext gameContext = mock(GameContext.class);
        when(gameContext.getDeltaTime()).thenReturn(1f / tickRate);
        GameObject nest = new GameObject(Master.LeftPlayer, PrefabType.SeedNest, Vector3.ZERO, gameContext);
        Spawner spawner = new Spawner(nest, 20, PrefabType.SeedSpirit, SPAWN_INTERVAL, true, 1);
        clearInvocations(gameContext);

        for (int frame = 0; frame < 30 * tickRate; frame++) {
            spawner.update();
        }

        long spawned = mockingDetails(gameContext).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("createGameObject"))
                .count();
        // 30 / 0.7 = 42.9
        assertThat(spawned).isEqualTo(42);
    }
}
