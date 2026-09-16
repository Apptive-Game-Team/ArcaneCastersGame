package com.wordonline.server.game.domain.object.prefab;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.prefab.implement.build.DragonTowerPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.build.ElectricTowerPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.build.RockTurretPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.drop.RockDropPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.explode.RockExplodePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.GroundCannonPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.GroundTowerPrefabInitializer;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RockDeathRemnantPrefabAllowlistTest {

    @Test
    void attachesOnlyToExactStructureAndRockUnitAllowlist() {
        Parameters parameters = parameters();
        List<PrefabInitializer> eligible = List.of(
                new RockGolemPrefabInitializer(parameters),
                new WallGolemPrefabInitializer(parameters),
                new RockMagePrefabInitializer(parameters),
                new RockSlimePrefabInitializer(parameters),
                new MiniRockPrefabInitializer(parameters),
                new RockDropPrefabInitializer(parameters),
                new RockTurretPrefabInitializer(parameters),
                new GroundCannonPrefabInitializer(parameters),
                new GroundTowerPrefabInitializer(parameters),
                new DragonTowerPrefabInitializer(parameters),
                new ElectricTowerPrefabInitializer(parameters),
                new TowerbackPrefabInitializer(parameters)
        );
        List<PrefabInitializer> excluded = List.of(
                new RockRemnantPrefabInitializer(parameters),
                new RockSummonPrefabInitializer(parameters),
                new RockRollingPrefabInitializer(parameters),
                new RockExplodePrefabInitializer(parameters),
                new RockRunePrefabInitializer(parameters)
        );

        for (PrefabInitializer initializer : eligible) {
            assertThat(hasRockDeathRemnant(initializer))
                    .as(initializer.getClass().getSimpleName())
                    .isTrue();
        }
        for (PrefabInitializer initializer : excluded) {
            assertThat(hasRockDeathRemnant(initializer))
                    .as(initializer.getClass().getSimpleName())
                    .isFalse();
        }
    }

    private boolean hasRockDeathRemnant(PrefabInitializer initializer) {
        GameObject gameObject = new GameObject(
                Master.LeftPlayer,
                initializer.prefabType,
                Vector3.ZERO,
                mock(GameContext.class)
        );
        initializer.initialize(gameObject);
        return gameObject.getComponents().stream().anyMatch(RockDeathRemnant.class::isInstance)
                || gameObject.getComponentsToAdd().stream().anyMatch(RockDeathRemnant.class::isInstance);
    }

    private Parameters parameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters objectParameters = mock(GameObjectParameters.class);
        when(parameters.object(any())).thenReturn(objectParameters);
        return parameters;
    }
}
