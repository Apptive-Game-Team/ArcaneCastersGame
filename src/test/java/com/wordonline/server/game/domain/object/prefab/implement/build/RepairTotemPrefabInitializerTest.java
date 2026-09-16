package com.wordonline.server.game.domain.object.prefab.implement.build;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.build.RepairAura;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.GaugeDto;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepairTotemPrefabInitializerTest {

    @Test
    void wiresHpRadiusAndDurationFromParametersOntoTheirComponents() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters repairTotemParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.REPAIR_TOTEM)).thenReturn(repairTotemParameters);
        when(repairTotemParameters.intValue(ParameterKey.HP)).thenReturn(150);
        // RADIUS (body collider) and EFFECT_RADIUS (repair aura range) are mocked to different
        // values so a test that reads the wrong one fails instead of passing by coincidence.
        when(repairTotemParameters.floatValue(ParameterKey.RADIUS)).thenReturn(4f);
        when(repairTotemParameters.floatValue(ParameterKey.EFFECT_RADIUS)).thenReturn(6f);
        when(repairTotemParameters.floatValue(ParameterKey.DURATION)).thenReturn(20f);
        when(repairTotemParameters.intValue(ParameterKey.MASS)).thenReturn(1000000);

        GameObject repairTotem = new GameObject(
                Master.LeftPlayer,
                PrefabType.RepairTotem,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new RepairTotemPrefabInitializer(parameters).initialize(repairTotem);

        DummyMob mob = component(repairTotem, DummyMob.class);
        assertThat(mob.getGauge().maxValue()).isEqualTo(150f);

        CircleCollider collider = repairTotem.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(4f);

        RepairAura aura = component(repairTotem, RepairAura.class);
        assertThat(ReflectionTestUtils.getField(aura, "radius")).isEqualTo(6f);

        TimedSelfDestroyer selfDestroyer = component(repairTotem, TimedSelfDestroyer.class);
        GaugeDto gauge = selfDestroyer.getGauge();
        assertThat(gauge.maxValue()).isEqualTo(20f);

        assertThat(component(repairTotem, BuildingEffectReceiver.class)).isNotNull();
        assertThat(repairTotem.getElement().total()).contains(ElementType.NATURE);
    }

    private <T> T component(GameObject gameObject, Class<T> type) {
        List<Component> all = Stream.concat(
                gameObject.getComponents().stream(),
                gameObject.getComponentsToAdd().stream()
        ).toList();
        return all.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }
}
