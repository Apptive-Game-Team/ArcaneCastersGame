package com.wordonline.server.game.domain.object.prefab;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.ElectricDeathEnergy;
import com.wordonline.server.game.domain.object.prefab.implement.build.ElectricTowerPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.lightning.ElectricSlimePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.lightning.ElectricSummonPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.lightning.ZapMousePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.DimensionToadPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.LightningTadpolePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.ManaWellPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.StormRiderPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.ThunderSpiritPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.third.ThunderBirdPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.pve.PveDimensionToadPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.pve.PveLightningTadpolePrefabInitializer;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElectricDeathEnergyPrefabAllowlistTest {

    @Test
    void attachesOnlyToLightningCreatureAllowlist() {
        Parameters parameters = parameters();
        List<PrefabInitializer> eligible = List.of(
                new ElectricSlimePrefabInitializer(parameters),
                new ZapMousePrefabInitializer(parameters),
                new LightningTadpolePrefabInitializer(parameters),
                new PveLightningTadpolePrefabInitializer(parameters),
                new ThunderSpiritPrefabInitializer(parameters),
                new ThunderBirdPrefabInitializer(parameters),
                new StormRiderPrefabInitializer(parameters)
        );
        List<PrefabInitializer> excluded = List.of(
                new ElectricTowerPrefabInitializer(parameters),
                new ElectricSummonPrefabInitializer(parameters),
                new ManaWellPrefabInitializer(parameters),
                new DimensionToadPrefabInitializer(parameters),
                new PveDimensionToadPrefabInitializer(parameters)
        );

        for (PrefabInitializer initializer : eligible) {
            assertThat(hasElectricDeathEnergy(initializer))
                    .as(initializer.getClass().getSimpleName())
                    .isTrue();
        }
        for (PrefabInitializer initializer : excluded) {
            assertThat(hasElectricDeathEnergy(initializer))
                    .as(initializer.getClass().getSimpleName())
                    .isFalse();
        }
    }

    private boolean hasElectricDeathEnergy(PrefabInitializer initializer) {
        GameObject gameObject = new GameObject(
                Master.LeftPlayer,
                initializer.prefabType,
                Vector3.ZERO,
                mock(GameContext.class)
        );
        initializer.initialize(gameObject);
        return gameObject.getComponents().stream().anyMatch(ElectricDeathEnergy.class::isInstance)
                || gameObject.getComponentsToAdd().stream().anyMatch(ElectricDeathEnergy.class::isInstance);
    }

    private Parameters parameters() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters objectParameters = mock(GameObjectParameters.class);
        when(parameters.object(any())).thenReturn(objectParameters);
        return parameters;
    }
}
