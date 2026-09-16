package com.wordonline.server.game.domain.object.prefab.implement.lightning;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetCategory;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElectricFieldPrefabInitializerTest {

    @Test
    void usesConfiguredElectricFieldRadiusAndDuration() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters electricField = mock(GameObjectParameters.class);
        GameObject field = mock(GameObject.class);
        when(parameters.object(GameObjectKey.ELECTRIC_FIELD)).thenReturn(electricField);
        when(electricField.floatValue(ParameterKey.RADIUS)).thenReturn(4f);
        when(electricField.floatValue(ParameterKey.DURATION)).thenReturn(5f);
        when(field.getPosition()).thenReturn(new Vector3(1f, 2f, 3f));
        when(field.getComponents()).thenReturn(new ArrayList<Component>());

        new ElectricFieldPrefabInitializer(parameters).initialize(field);

        verify(parameters).object(GameObjectKey.ELECTRIC_FIELD);
        verify(electricField).floatValue(ParameterKey.RADIUS);
        verify(electricField).floatValue(ParameterKey.DURATION);
    }

    @Test
    void isNotABuildingSoARepairTotemCannotFreezeIt() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters electricField = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.ELECTRIC_FIELD)).thenReturn(electricField);
        when(electricField.floatValue(ParameterKey.RADIUS)).thenReturn(4f);
        when(electricField.floatValue(ParameterKey.DURATION)).thenReturn(5f);

        GameObject field = new GameObject(
                Master.LeftPlayer,
                PrefabType.ElectricField,
                new Vector3(GameConfig.X_MID, 0f, GameConfig.Y_MID),
                mock(GameContext.class)
        );

        new ElectricFieldPrefabInitializer(parameters).initialize(field);

        // A field carries a TimedSelfDestroyer but neither a Mob nor a Damageable, so it falls
        // through to UNKNOWN. RepairAura freezes BUILDING only; were this BUILDING, a field in
        // range of a repair_totem would never burn out.
        assertThat(field.getComponent(TimedSelfDestroyer.class)).isNotNull();
        assertThat(TargetCategory.of(field)).isEqualTo(TargetCategory.UNKNOWN);
    }
}
