package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.DistanceSelfDestroyer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.prefab.implement.fire.DragonFlamePrefabInitializer;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlameLauncherTest {

    private static final float ATTACK_INTERVAL = 1.5f;
    private static final float ATTACK_RANGE = 8f;
    private static final float FLAME_SPEED = 8f;
    private static final int FLAME_DAMAGE = 30;

    private final GameContext gameContext = mock(GameContext.class);
    private final List<GameObject> created = new ArrayList<>();

    @BeforeEach
    void wireGameContext() {
        DragonFlamePrefabInitializer flameInitializer =
                new DragonFlamePrefabInitializer(dragonFlameParameters());
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(mock(ObjectsInfoDtoBuilder.class));
        // GameObject.start() reaches the prefab initializer through a static Spring context that
        // a unit test has no way to populate, so stand in for that step here. The tower itself is
        // built by the test, so only the launched flame is initialized and collected.
        doAnswer(invocation -> {
            GameObject spawned = invocation.getArgument(0);
            if (spawned.getType() == PrefabType.DragonFlame) {
                flameInitializer.initialize(spawned);
                created.add(spawned);
            }
            return null;
        }).when(gameContext).createGameObject(org.mockito.ArgumentMatchers.any(GameObject.class));
    }

    @Test
    void launchesAFlameWithNothingInFrontOfIt() {
        GameObject tower = towerAt(new Vector3(4f, 0f, 5f), Master.LeftPlayer);
        FlameLauncher launcher = launcherOn(tower);

        launcher.update();

        assertThat(created).hasSize(1);
        GameObject flame = created.getFirst();
        assertThat(flame.getType()).isEqualTo(PrefabType.DragonFlame);
        assertThat(flame.getMaster()).isEqualTo(Master.LeftPlayer);
        assertThat(flame.getPosition()).isEqualTo(new Vector3(4f, 0f, 5f));
        assertThat(flame.getComponent(Shot.class).getDirection()).isEqualTo(Vector3.RIGHT);
    }

    // the flame stops at the range rather than at the field edge, and the range is the tower's
    // own attack_range, so one number is both what the server flies and what the client draws
    @Test
    void givesTheFlameTheTowersRangeToFly() {
        FlameLauncher launcher = launcherOn(towerAt(new Vector3(4f, 0f, 5f), Master.LeftPlayer));

        launcher.update();

        GameObject flame = created.getFirst();
        DistanceSelfDestroyer selfDestroyer = pendingComponent(flame, DistanceSelfDestroyer.class);
        assertThat(selfDestroyer).isNotNull();
        assertThat(ReflectionTestUtils.getField(selfDestroyer, "maxDistance")).isEqualTo(ATTACK_RANGE);
        assertThat(ReflectionTestUtils.getField(selfDestroyer, "origin")).isEqualTo(new Vector3(4f, 0f, 5f));
    }

    @Test
    void aimsTheOtherWayForTheRightPlayer() {
        FlameLauncher launcher = launcherOn(towerAt(new Vector3(14f, 0f, 5f), Master.RightPlayer));

        launcher.update();

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().getComponent(Shot.class).getDirection()).isEqualTo(Vector3.LEFT);
    }

    @Test
    void holdsItsFireUntilTheAttackIntervalHasPassed() {
        GameObject tower = towerAt(new Vector3(4f, 0f, 5f), Master.LeftPlayer);
        when(gameContext.getDeltaTime()).thenReturn(1f);
        FlameLauncher launcher = new FlameLauncher(tower, ATTACK_INTERVAL, ATTACK_RANGE);

        launcher.update();
        assertThat(created).isEmpty();

        launcher.update();
        assertThat(created).hasSize(1);
    }

    // an unowned tower has no side to face, so it must not guess one
    @Test
    void launchesNothingWithoutAnOwner() {
        FlameLauncher launcher = launcherOn(towerAt(new Vector3(9f, 0f, 5f), Master.None));

        launcher.update();

        assertThat(created).isEmpty();
    }

    private <T> T pendingComponent(GameObject gameObject, Class<T> clazz) {
        return gameObject.getComponentsToAdd().stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    private FlameLauncher launcherOn(GameObject tower) {
        when(gameContext.getDeltaTime()).thenReturn(ATTACK_INTERVAL);
        return new FlameLauncher(tower, ATTACK_INTERVAL, ATTACK_RANGE);
    }

    private GameObject towerAt(Vector3 position, Master master) {
        return new GameObject(master, PrefabType.DragonTower, position, gameContext);
    }

    private Parameters dragonFlameParameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters values = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.DRAGON_FLAME)).thenReturn(values);
        when(values.floatValue(ParameterKey.RADIUS)).thenReturn(0.3f);
        when(values.intValue(ParameterKey.DAMAGE)).thenReturn(FLAME_DAMAGE);
        when(values.floatValue(ParameterKey.SPEED)).thenReturn(FLAME_SPEED);
        return parameters;
    }
}
