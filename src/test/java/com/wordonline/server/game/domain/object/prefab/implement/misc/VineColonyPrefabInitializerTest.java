package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetCategory;
import com.wordonline.server.game.domain.object.component.mob.simple.SummonMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
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

class VineColonyPrefabInitializerTest {

    @Test
    void usesVineColonyDurationForLifetime() {
        GameObject vineColony = initializedVineColony();

        TimedSelfDestroyer selfDestroyer = components(vineColony)
                .filter(TimedSelfDestroyer.class::isInstance)
                .map(TimedSelfDestroyer.class::cast)
                .findFirst()
                .orElseThrow();
        GaugeDto gauge = selfDestroyer.getGauge();

        assertThat(gauge.maxValue()).isEqualTo(15f);
        assertThat(gauge.category()).isEqualTo(GaugeCategory.TTL);
        assertThat(vineColony.getElement().nativeHas(ElementType.NATURE)).isTrue();
        assertThat(components(vineColony)).anyMatch(RigidBody.class::isInstance);
        assertThat(components(vineColony)).anyMatch(SummonMob.class::isInstance);
        assertThat(vineColony.getFirstCircleCollider()).map(CircleCollider::getRadius).hasValue(1f);
    }

    @Test
    void classifiesVineColonyAsBuildingByLeavingSummonMobSpeedAtZero() {
        GameObject vineColony = initializedVineColony();

        SummonMob summonMob = components(vineColony)
                .filter(SummonMob.class::isInstance)
                .map(SummonMob.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(summonMob.getSpeed().total()).isEqualTo(0f);
        assertThat(TargetCategory.of(vineColony)).isEqualTo(TargetCategory.BUILDING);
    }

    private GameObject initializedVineColony() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters vineColonyParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.VINE_COLONY)).thenReturn(vineColonyParameters);
        when(vineColonyParameters.intValue(ParameterKey.MASS)).thenReturn(3);
        when(vineColonyParameters.floatValue(ParameterKey.RADIUS)).thenReturn(1f);
        when(vineColonyParameters.intValue(ParameterKey.HP)).thenReturn(40);
        // DAMAGE stays stubbed with a non-zero value even though the initializer no longer reads it:
        // without it the mock would answer 0, and putting DAMAGE back into the speed slot would pass
        // classifiesVineColonyAsBuildingByLeavingSummonMobSpeedAtZero unnoticed.
        when(vineColonyParameters.intValue(ParameterKey.DAMAGE)).thenReturn(9);
        when(vineColonyParameters.intValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(1);
        when(vineColonyParameters.intValue(ParameterKey.ATTACK_RANGE)).thenReturn(3);
        when(vineColonyParameters.floatValue(ParameterKey.DURATION)).thenReturn(15f);

        GameObject vineColony = new GameObject(
                Master.LeftPlayer,
                PrefabType.VineColony,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new VineColonyPrefabInitializer(parameters).initialize(vineColony);
        return vineColony;
    }

    private Stream<Component> components(GameObject gameObject) {
        List<Component> all = Stream.concat(
                gameObject.getComponents().stream(),
                gameObject.getComponentsToAdd().stream()
        ).toList();
        return all.stream();
    }
}
