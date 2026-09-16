package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.simple.PlayerHealthComponent;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerPrefabInitializerTest {

    @Test
    void initializesPlayerHealthFromDatabaseParameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters playerParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.PLAYER)).thenReturn(playerParameters);
        when(playerParameters.intValue(ParameterKey.HP)).thenReturn(250);

        PlayerData leftPlayerData = new PlayerData(null);
        GameSessionData gameSessionData = new GameSessionData(
                leftPlayerData,
                new PlayerData(null)
        );
        GameContext gameContext = mock(GameContext.class);
        when(gameContext.getGameSessionData()).thenReturn(gameSessionData);
        GameObject player = new GameObject(
                Master.LeftPlayer,
                PrefabType.Player,
                Vector3.ZERO,
                gameContext
        );

        new PlayerPrefabInitializer(parameters).initialize(player);

        PlayerHealthComponent health = player.getComponentsToAdd().stream()
                .filter(PlayerHealthComponent.class::isInstance)
                .map(PlayerHealthComponent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(health.getHp()).isEqualTo(250);
        assertThat(health.getMaxHp()).isEqualTo(250);
        assertThat(leftPlayerData.hp).isEqualTo(250);
        verify(playerParameters).intValue(ParameterKey.HP);
    }
}
