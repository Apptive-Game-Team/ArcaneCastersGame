package com.wordonline.server.game.domain.object.component.mob.simple;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TitanRemnantMobTest {

    @Test
    void createsFistAtServerSelectedEnemyPosition() {
        GameContext gameContext = mock(GameContext.class);
        GameSessionData sessionData = new GameSessionData(
                mock(PlayerData.class),
                mock(PlayerData.class)
        );
        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getDeltaTime()).thenReturn(1f);

        GameObject remnant = object(
                Master.LeftPlayer,
                PrefabType.TitanRemnant,
                Vector3.ZERO,
                gameContext
        );
        GameObject enemy = object(
                Master.RightPlayer,
                PrefabType.Player,
                new Vector3(2f, 0f, 1f),
                gameContext
        );
        enemy.getComponents().add(new DummyMob(enemy, 20));
        sessionData.gameObjects.add(remnant);
        sessionData.gameObjects.add(enemy);
        clearInvocations(gameContext);

        TitanRemnantMob mob = new TitanRemnantMob(remnant, 40, 1f, 4f);
        mob.start();
        mob.update();

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(created.capture());
        assertThat(created.getValue().getType()).isEqualTo(PrefabType.TitanFist);
        assertThat(created.getValue().getMaster()).isEqualTo(Master.LeftPlayer);
        assertThat(created.getValue().getPosition()).isEqualTo(new Vector3(2f, 0f, 1f));
        assertThat(remnant.getStatus()).isEqualTo(Status.Attack);
    }

    private GameObject object(
            Master master,
            PrefabType type,
            Vector3 position,
            GameContext context
    ) {
        GameObject object = new GameObject(master, type, position, context);
        object.addCollider(new CircleCollider(object, 0.5f, false));
        object.setStatus(Status.Idle);
        return object;
    }
}
