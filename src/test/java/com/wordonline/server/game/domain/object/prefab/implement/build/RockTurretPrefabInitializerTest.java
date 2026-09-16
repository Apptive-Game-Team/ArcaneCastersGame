package com.wordonline.server.game.domain.object.prefab.implement.build;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.GaugeCategory;
import com.wordonline.server.game.dto.frame.GaugeDto;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RockTurretPrefabInitializerTest {

    @Test
    void usesConfiguredRockTurretDurationForLifetime() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters rockTurretParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.ROCK_TURRET)).thenReturn(rockTurretParameters);
        when(rockTurretParameters.intValue(ParameterKey.MASS)).thenReturn(99999);
        when(rockTurretParameters.floatValue(ParameterKey.RADIUS)).thenReturn(0.5f);
        when(rockTurretParameters.intValue(ParameterKey.HP)).thenReturn(40);
        when(rockTurretParameters.intValue(ParameterKey.DAMAGE)).thenReturn(2);
        when(rockTurretParameters.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(0.5f);
        when(rockTurretParameters.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(3f);
        when(rockTurretParameters.floatValue(ParameterKey.DURATION)).thenReturn(7f);

        GameObject rockTurret = new GameObject(
                Master.LeftPlayer,
                PrefabType.RockTurret,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new RockTurretPrefabInitializer(parameters).initialize(rockTurret);

        TimedSelfDestroyer selfDestroyer = components(rockTurret)
                .filter(TimedSelfDestroyer.class::isInstance)
                .map(TimedSelfDestroyer.class::cast)
                .findFirst()
                .orElseThrow();
        GaugeDto gauge = selfDestroyer.getGauge();

        assertThat(gauge.maxValue()).isEqualTo(7f);
        assertThat(gauge.category()).isEqualTo(GaugeCategory.TTL);
    }

    private Stream<Component> components(GameObject gameObject) {
        List<Component> all = Stream.concat(
                gameObject.getComponents().stream(),
                gameObject.getComponentsToAdd().stream()
        ).toList();
        return all.stream();
    }
}
