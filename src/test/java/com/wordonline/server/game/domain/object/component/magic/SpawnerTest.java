package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpawnerTest {

    @Test
    void spawningDoesNotConsumeSpawnerHp() {
        GameContext gameContext = mock(GameContext.class);
        when(gameContext.getDeltaTime()).thenReturn(0.1f);
        GameObject gameObject = new GameObject(
                Master.LeftPlayer,
                PrefabType.SeedNest,
                Vector3.ZERO,
                gameContext
        );
        clearInvocations(gameContext);
        Spawner spawner = new Spawner(
                gameObject,
                20,
                PrefabType.SeedSpirit,
                0.1f,
                true,
                1
        );

        spawner.update();
        spawner.update();

        verify(gameContext).createGameObject(any(GameObject.class));
        assertThat(spawner.getHp()).isEqualTo(20);
    }
}
