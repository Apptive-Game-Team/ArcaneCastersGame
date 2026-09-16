package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FireworkLauncherTest {

    private static final float ATTACK_INTERVAL_SEC = 2f;
    private static final float ATTACK_OFFSET = 3f;
    private static final float TICK_DELTA_SEC = 0.5f;
    // Mirrors FireworkLauncher.HORIZONTAL_SPEED (6.0f): attackOffset / horizontalSpeed.
    private static final float FLIGHT_DURATION_SEC = 0.5f;
    private static final float HALF_FLIGHT_DURATION_SEC = FLIGHT_DURATION_SEC / 2f;
    private static final Vector3 TOWER_POSITION = new Vector3(1f, 0f, 2f);
    private static final Vector3 LEFT_IMPACT_POSITION = new Vector3(4f, 0f, 2f);
    private static final Vector3 RIGHT_IMPACT_POSITION = new Vector3(-2f, 0f, 2f);

    private GameObject towerFor(Master master) {
        return new GameObject(master, PrefabType.FireworkTower, TOWER_POSITION, mock(GameContext.class));
    }

    private ObjectsInfoDtoBuilder stubObjectsInfoDtoBuilder(GameContext gameContext) {
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);
        return dtoBuilder;
    }

    @Test
    void launchTickSendsAProjectileButNoShellYetForLeftPlayer() {
        GameObject tower = towerFor(Master.LeftPlayer);
        GameContext gameContext = tower.getGameContext();
        ObjectsInfoDtoBuilder dtoBuilder = stubObjectsInfoDtoBuilder(gameContext);
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        new FireworkLauncher(tower, ATTACK_INTERVAL_SEC, ATTACK_OFFSET).update();

        verify(dtoBuilder).createProjection(TOWER_POSITION, LEFT_IMPACT_POSITION, "FireworkShell", FLIGHT_DURATION_SEC);
        verify(gameContext, never()).createGameObject(any(GameObject.class));
    }

    @Test
    void launchTickSendsAProjectileButNoShellYetForRightPlayer() {
        GameObject tower = towerFor(Master.RightPlayer);
        GameContext gameContext = tower.getGameContext();
        ObjectsInfoDtoBuilder dtoBuilder = stubObjectsInfoDtoBuilder(gameContext);
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        new FireworkLauncher(tower, ATTACK_INTERVAL_SEC, ATTACK_OFFSET).update();

        verify(dtoBuilder).createProjection(TOWER_POSITION, RIGHT_IMPACT_POSITION, "FireworkShell", FLIGHT_DURATION_SEC);
        verify(gameContext, never()).createGameObject(any(GameObject.class));
    }

    @Test
    void shellAppearsAtImpactPointOnlyAfterFlightDurationForLeftPlayer() {
        GameObject tower = towerFor(Master.LeftPlayer);
        GameContext gameContext = tower.getGameContext();
        stubObjectsInfoDtoBuilder(gameContext);
        when(gameContext.getDeltaTime())
                .thenReturn(ATTACK_INTERVAL_SEC, HALF_FLIGHT_DURATION_SEC, HALF_FLIGHT_DURATION_SEC);
        clearInvocations(gameContext);

        FireworkLauncher launcher = new FireworkLauncher(tower, ATTACK_INTERVAL_SEC, ATTACK_OFFSET);

        launcher.update(); // launch tick: shot is in flight, no shell yet
        verify(gameContext, never()).createGameObject(any(GameObject.class));

        launcher.update(); // half the flight duration has passed: still in flight
        verify(gameContext, never()).createGameObject(any(GameObject.class));

        launcher.update(); // the flight duration elapses: the shell lands at the impact point
        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(spawned.capture());
        assertThat(spawned.getValue().getType()).isEqualTo(PrefabType.FireworkShell);
        assertThat(spawned.getValue().getMaster()).isEqualTo(Master.LeftPlayer);
        assertThat(spawned.getValue().getPosition()).isEqualTo(LEFT_IMPACT_POSITION);
    }

    @Test
    void shellAppearsAtImpactPointOnlyAfterFlightDurationForRightPlayer() {
        GameObject tower = towerFor(Master.RightPlayer);
        GameContext gameContext = tower.getGameContext();
        stubObjectsInfoDtoBuilder(gameContext);
        when(gameContext.getDeltaTime())
                .thenReturn(ATTACK_INTERVAL_SEC, HALF_FLIGHT_DURATION_SEC, HALF_FLIGHT_DURATION_SEC);
        clearInvocations(gameContext);

        FireworkLauncher launcher = new FireworkLauncher(tower, ATTACK_INTERVAL_SEC, ATTACK_OFFSET);

        launcher.update(); // launch tick: shot is in flight, no shell yet
        verify(gameContext, never()).createGameObject(any(GameObject.class));

        launcher.update(); // half the flight duration has passed: still in flight
        verify(gameContext, never()).createGameObject(any(GameObject.class));

        launcher.update(); // the flight duration elapses: the shell lands at the impact point
        ArgumentCaptor<GameObject> spawned = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(spawned.capture());
        assertThat(spawned.getValue().getMaster()).isEqualTo(Master.RightPlayer);
        assertThat(spawned.getValue().getPosition()).isEqualTo(RIGHT_IMPACT_POSITION);
    }

    @Test
    void masterNoneProducesNeitherAProjectileNorAShell() {
        GameObject tower = towerFor(Master.None);
        GameContext gameContext = tower.getGameContext();
        ObjectsInfoDtoBuilder dtoBuilder = stubObjectsInfoDtoBuilder(gameContext);
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL_SEC);
        clearInvocations(gameContext);

        new FireworkLauncher(tower, ATTACK_INTERVAL_SEC, ATTACK_OFFSET).update();

        verify(dtoBuilder, never())
                .createProjection(any(Vector3.class), any(Vector3.class), anyString(), anyFloat());
        verify(gameContext, never()).createGameObject(any(GameObject.class));
    }

    @Test
    void launchesExactlyOneShotPerAttackInterval() {
        GameObject tower = towerFor(Master.LeftPlayer);
        GameContext gameContext = tower.getGameContext();
        ObjectsInfoDtoBuilder dtoBuilder = stubObjectsInfoDtoBuilder(gameContext);
        when(gameContext.getDeltaTime()).thenReturn(TICK_DELTA_SEC);
        clearInvocations(gameContext);

        FireworkLauncher launcher = new FireworkLauncher(tower, ATTACK_INTERVAL_SEC, ATTACK_OFFSET);

        // Interval is 2s and ticks are 0.5s: three ticks (1.5s) must not launch yet.
        launcher.update();
        launcher.update();
        launcher.update();
        verify(dtoBuilder, never())
                .createProjection(any(Vector3.class), any(Vector3.class), anyString(), anyFloat());

        // The fourth tick completes the 2s cooldown: exactly one shot launched.
        launcher.update();
        verify(dtoBuilder, times(1))
                .createProjection(any(Vector3.class), any(Vector3.class), anyString(), anyFloat());

        // Another 1.5s must not launch a second shot yet.
        launcher.update();
        launcher.update();
        launcher.update();
        verify(dtoBuilder, times(1))
                .createProjection(any(Vector3.class), any(Vector3.class), anyString(), anyFloat());

        // Completing the next 2s window launches exactly one more shot.
        launcher.update();
        verify(dtoBuilder, times(2))
                .createProjection(any(Vector3.class), any(Vector3.class), anyString(), anyFloat());
    }
}
