package com.wordonline.server.game.domain.object.prefab.implement.subprefab;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.OnStartAttacker;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FireworkShellPrefabInitializerTest {

    @Test
    void advertisesFireworkShellPrefabType() {
        FireworkShellPrefabInitializer initializer = new FireworkShellPrefabInitializer(mock(Parameters.class));

        assertThat(initializer.prefabType).isEqualTo(PrefabType.FireworkShell);
    }

    @Test
    void wiresRadiusDurationAndElementFromParameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters fireworkShellParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.FIREWORK_SHELL)).thenReturn(fireworkShellParameters);
        when(fireworkShellParameters.floatValue(ParameterKey.RADIUS)).thenReturn(2.5f);
        when(fireworkShellParameters.intValue(ParameterKey.DAMAGE)).thenReturn(40);
        when(fireworkShellParameters.floatValue(ParameterKey.DURATION)).thenReturn(1.5f);

        GameObject shell = new GameObject(Master.LeftPlayer, PrefabType.FireworkShell, Vector3.ZERO, mock(GameContext.class));

        new FireworkShellPrefabInitializer(parameters).initialize(shell);

        CircleCollider collider = shell.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(2.5f);
        assertThat(collider.isTrigger()).isTrue();

        TimedSelfDestroyer destroyer = (TimedSelfDestroyer) shell.getComponentsToAdd().stream()
                .filter(TimedSelfDestroyer.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(destroyer.getGauge().maxValue()).isEqualTo(1.5f);

        assertThat(shell.getElement().has(ElementType.FIRE)).isTrue();
        assertThat(shell.getComponentsToAdd()).anyMatch(OnStartAttacker.class::isInstance);
    }

    @Test
    void onStartAttackerUsesTheSameRadiusAsTheColliderToDamageEnemiesInRange() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters fireworkShellParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.FIREWORK_SHELL)).thenReturn(fireworkShellParameters);
        when(fireworkShellParameters.floatValue(ParameterKey.RADIUS)).thenReturn(2f);
        when(fireworkShellParameters.intValue(ParameterKey.DAMAGE)).thenReturn(40);
        when(fireworkShellParameters.floatValue(ParameterKey.DURATION)).thenReturn(1.5f);

        GameContext gameContext = mock(GameContext.class);
        GameObject shell = new GameObject(Master.LeftPlayer, PrefabType.FireworkShell, new Vector3(0f, 0f, 0f), gameContext);

        new FireworkShellPrefabInitializer(parameters).initialize(shell);

        GameObject enemyInRange = new GameObject(Master.RightPlayer, PrefabType.MiniRock, new Vector3(1f, 0f, 0f), gameContext);
        DummyMob enemyMob = new DummyMob(enemyInRange, 100);
        enemyInRange.getComponents().add(enemyMob);
        enemyInRange.setStatus(Status.Idle);

        GameSessionData sessionData = new GameSessionData(null, null);
        sessionData.gameObjects.add(shell);
        sessionData.gameObjects.add(enemyInRange);
        when(gameContext.getGameSessionData()).thenReturn(sessionData);

        OnStartAttacker attacker = (OnStartAttacker) shell.getComponentsToAdd().stream()
                .filter(OnStartAttacker.class::isInstance)
                .findFirst()
                .orElseThrow();

        attacker.start();

        assertThat(enemyMob.getHp()).isEqualTo(60);
    }
}
