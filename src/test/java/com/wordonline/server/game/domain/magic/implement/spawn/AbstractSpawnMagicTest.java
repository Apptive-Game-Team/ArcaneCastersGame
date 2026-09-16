package com.wordonline.server.game.domain.magic.implement.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

class AbstractSpawnMagicTest {

    private final GameObjectParameters parameters = mock(GameObjectParameters.class);
    private final GameContext gameContext = mock(GameContext.class);
    private final AbstractSpawnMagic magic = new AbstractSpawnMagic(PrefabType.ZapMouse, parameters) {
    };

    @Test
    void spawnsOnceAtRequestedGroundPositionWhenQuantityIsMissing() {
        when(parameters.intValueOrDefault(ParameterKey.QUANTITY, 1)).thenReturn(1);

        magic.run(gameContext, Master.LeftPlayer, new Vector3(3, 7, 5));

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(created.capture());
        assertThat(created.getValue().getType()).isEqualTo(PrefabType.ZapMouse);
        assertThat(created.getValue().getPosition()).isEqualTo(new Vector3(3, 0, 5));
    }

    @Test
    void spawnsConfiguredQuantity() {
        when(parameters.intValueOrDefault(ParameterKey.QUANTITY, 1)).thenReturn(2);

        magic.run(gameContext, Master.LeftPlayer, Vector3.ZERO);

        verify(gameContext, times(2)).createGameObject(any(GameObject.class));
    }

    @Test
    void spawnsAerialUnitAtConfiguredHeight() {
        when(parameters.intValueOrDefault(ParameterKey.QUANTITY, 1)).thenReturn(1);
        AbstractSpawnMagic aerialMagic = new AbstractSpawnMagic(
                PrefabType.WindSpirit, parameters, GameConfig.AERIAL_MOB_INIT_HEIGHT) {
        };

        aerialMagic.run(gameContext, Master.LeftPlayer, new Vector3(3, 0, 5));

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(created.capture());
        assertThat(created.getValue().getPosition()).isEqualTo(new Vector3(3, 3, 5));
    }
}
