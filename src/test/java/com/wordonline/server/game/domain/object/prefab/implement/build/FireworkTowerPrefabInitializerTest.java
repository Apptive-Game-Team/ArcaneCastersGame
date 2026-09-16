package com.wordonline.server.game.domain.object.prefab.implement.build;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.magic.FireworkLauncher;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FireworkTowerPrefabInitializerTest {

    @Test
    void advertisesFireworkTowerPrefabType() {
        FireworkTowerPrefabInitializer initializer = new FireworkTowerPrefabInitializer(mock(Parameters.class));

        assertThat(initializer.prefabType).isEqualTo(PrefabType.FireworkTower);
    }

    @Test
    void wiresHpColliderAndDurationFromParameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters fireworkTowerParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.FIREWORK_TOWER)).thenReturn(fireworkTowerParameters);
        when(fireworkTowerParameters.intValue(ParameterKey.MASS)).thenReturn(-1);
        when(fireworkTowerParameters.floatValue(ParameterKey.RADIUS)).thenReturn(1.2f);
        when(fireworkTowerParameters.intValue(ParameterKey.HP)).thenReturn(500);
        when(fireworkTowerParameters.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(4f);
        when(fireworkTowerParameters.floatValue(ParameterKey.ATTACK_OFFSET)).thenReturn(3f);
        when(fireworkTowerParameters.floatValue(ParameterKey.DURATION)).thenReturn(60f);

        GameObject tower = new GameObject(Master.LeftPlayer, PrefabType.FireworkTower, Vector3.ZERO, mock(GameContext.class));

        new FireworkTowerPrefabInitializer(parameters).initialize(tower);

        CircleCollider collider = tower.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(1.2f);
        assertThat(collider.isTrigger()).isFalse();

        assertThat(tower.getComponents()).anyMatch(RigidBody.class::isInstance);

        DummyMob mob = (DummyMob) tower.getComponentsToAdd().stream()
                .filter(DummyMob.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(mob.getHp()).isEqualTo(500);
        assertThat(mob.getMaxHp()).isEqualTo(500);

        TimedSelfDestroyer destroyer = (TimedSelfDestroyer) tower.getComponentsToAdd().stream()
                .filter(TimedSelfDestroyer.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(destroyer.getGauge().maxValue()).isEqualTo(60f);

        assertThat(tower.getComponents()).anyMatch(BuildingEffectReceiver.class::isInstance);
        assertThat(tower.getElement().has(ElementType.FIRE)).isTrue();
    }

    @Test
    void fireworkLauncherUsesAttackIntervalAndAttackOffsetFromParameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters fireworkTowerParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.FIREWORK_TOWER)).thenReturn(fireworkTowerParameters);
        when(fireworkTowerParameters.intValue(ParameterKey.MASS)).thenReturn(-1);
        when(fireworkTowerParameters.floatValue(ParameterKey.RADIUS)).thenReturn(1.2f);
        when(fireworkTowerParameters.intValue(ParameterKey.HP)).thenReturn(500);
        when(fireworkTowerParameters.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(4f);
        when(fireworkTowerParameters.floatValue(ParameterKey.ATTACK_OFFSET)).thenReturn(3f);
        when(fireworkTowerParameters.floatValue(ParameterKey.DURATION)).thenReturn(60f);

        GameContext gameContext = mock(GameContext.class);
        GameObject tower = new GameObject(Master.LeftPlayer, PrefabType.FireworkTower, new Vector3(5f, 0f, 5f), gameContext);

        new FireworkTowerPrefabInitializer(parameters).initialize(tower);

        FireworkLauncher launcher = (FireworkLauncher) tower.getComponentsToAdd().stream()
                .filter(FireworkLauncher.class::isInstance)
                .findFirst()
                .orElseThrow();

        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(mock(ObjectsInfoDtoBuilder.class));
        // attack_offset (3) / FireworkLauncher's horizontal speed (6) = 0.5s of flight
        // after the launch tick before the shell appears at the impact point.
        when(gameContext.getDeltaTime()).thenReturn(4f, 0.5f);
        clearInvocations(gameContext);

        launcher.update(); // launch tick: shot is in flight, no shell yet
        verify(gameContext, never()).createGameObject(any(GameObject.class));

        launcher.update(); // flight duration elapses: the shell lands at the impact point

        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(spawned.capture());
        assertThat(spawned.getValue().getType()).isEqualTo(PrefabType.FireworkShell);
        // attack_offset = 3, tower faces +X as the left player, so impact is at x = 5 + 3 = 8.
        assertThat(spawned.getValue().getPosition()).isEqualTo(new Vector3(8f, 0f, 5f));
    }
}
