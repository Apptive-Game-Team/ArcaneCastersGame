package com.wordonline.server.game.domain.object.prefab;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.prefab.implement.build.DragonTowerPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.build.ElectricTowerPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.build.RockTurretPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.build.TitanRemnantPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.drop.RockDropPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.explode.RockExplodePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.GroundCannonPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.GroundTowerPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.MagmaSpiritPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.RockGolemPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.WallGolemPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.TowerbackPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.RockMagePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.rock.MiniRockPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.rock.RockRemnantPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.rock.RockRollingPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.rock.RockSlimePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.rock.RockSummonPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.rune.RockRunePrefabInitializer;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RockDeathRemnantPrefabAllowlistTest {

    @Test
    void attachesConfiguredRemnantSizesOnlyToEligibleRockSummonsAndBuildings() {
        Parameters parameters = parameters();
        List<PrefabInitializer> mediumRemnantSources = List.of(
                new WallGolemPrefabInitializer(parameters),
                new MagmaSpiritPrefabInitializer(parameters),
                new TitanRemnantPrefabInitializer(parameters)
        );
        List<PrefabInitializer> smallRemnantSources = List.of(
                new RockGolemPrefabInitializer(parameters),
                new GroundCannonPrefabInitializer(parameters),
                new GroundTowerPrefabInitializer(parameters)
        );
        List<PrefabInitializer> excluded = List.of(
                new RockMagePrefabInitializer(parameters),
                new RockSlimePrefabInitializer(parameters),
                new MiniRockPrefabInitializer(parameters),
                new RockDropPrefabInitializer(parameters),
                new RockTurretPrefabInitializer(parameters),
                new DragonTowerPrefabInitializer(parameters),
                new ElectricTowerPrefabInitializer(parameters),
                new TowerbackPrefabInitializer(parameters),
                new RockRemnantPrefabInitializer(parameters),
                new RockSummonPrefabInitializer(parameters),
                new RockRollingPrefabInitializer(parameters),
                new RockExplodePrefabInitializer(parameters),
                new RockRunePrefabInitializer(parameters)
        );

        for (PrefabInitializer initializer : mediumRemnantSources) {
            assertCreatesRockRemnant(initializer, PrefabType.MediumRockRemnant);
        }
        for (PrefabInitializer initializer : smallRemnantSources) {
            assertCreatesRockRemnant(initializer, PrefabType.RockRemnant);
        }
        for (PrefabInitializer initializer : excluded) {
            assertThat(hasRockDeathRemnant(initializer))
                    .as(initializer.getClass().getSimpleName())
                    .isFalse();
        }
    }

    private void assertCreatesRockRemnant(PrefabInitializer initializer, PrefabType expectedType) {
        GameContext gameContext = mock(GameContext.class);
        GameObject gameObject = initialized(initializer, gameContext);
        RockDeathRemnant deathRemnant = gameObject.getComponentsToAdd().stream()
                .filter(RockDeathRemnant.class::isInstance)
                .map(RockDeathRemnant.class::cast)
                .findFirst()
                .orElseGet(() -> gameObject.getComponents().stream()
                        .filter(RockDeathRemnant.class::isInstance)
                        .map(RockDeathRemnant.class::cast)
                        .findFirst()
                        .orElseThrow());
        clearInvocations(gameContext);

        deathRemnant.onCombatDeath();

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(created.capture());
        assertThat(created.getValue().getType())
                .as(initializer.getClass().getSimpleName())
                .isEqualTo(expectedType);
    }

    private boolean hasRockDeathRemnant(PrefabInitializer initializer) {
        GameObject gameObject = initialized(initializer, mock(GameContext.class));
        return gameObject.getComponents().stream().anyMatch(RockDeathRemnant.class::isInstance)
                || gameObject.getComponentsToAdd().stream().anyMatch(RockDeathRemnant.class::isInstance);
    }

    private GameObject initialized(PrefabInitializer initializer, GameContext gameContext) {
        GameObject gameObject = new GameObject(
                Master.LeftPlayer,
                initializer.prefabType,
                Vector3.ZERO,
                gameContext
        );
        initializer.initialize(gameObject);
        return gameObject;
    }

    private Parameters parameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters objectParameters = mock(GameObjectParameters.class);
        when(parameters.object(any())).thenReturn(objectParameters);
        return parameters;
    }
}
