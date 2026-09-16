package com.wordonline.server.game.domain.object.prefab.implement.build;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.receiver.BuildingEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.simple.TitanRemnantMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.GaugeCategory;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TitanRemnantPrefabInitializerTest {

    @Test
    void configuresDurableTimedRockBuilding() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters stats = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.TITAN_REMNANT)).thenReturn(stats);
        when(stats.intValue(ParameterKey.MASS)).thenReturn(8);
        when(stats.floatValue(ParameterKey.RADIUS)).thenReturn(1.2f);
        when(stats.intValue(ParameterKey.HP)).thenReturn(40);
        when(stats.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(2f);
        when(stats.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(5f);
        when(stats.floatValue(ParameterKey.DURATION)).thenReturn(15f);

        GameObject remnant = new GameObject(
                Master.LeftPlayer,
                PrefabType.TitanRemnant,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new TitanRemnantPrefabInitializer(parameters).initialize(remnant);

        assertThat(remnant.getElement().nativeHas(ElementType.ROCK)).isTrue();
        assertThat(remnant.getFirstCircleCollider()).map(CircleCollider::getRadius).hasValue(1.2f);
        assertThat(components(remnant)).anyMatch(RigidBody.class::isInstance);
        assertThat(components(remnant)).anyMatch(TitanRemnantMob.class::isInstance);
        assertThat(components(remnant)).anyMatch(BuildingEffectReceiver.class::isInstance);
        assertThat(components(remnant)
                .filter(TimedSelfDestroyer.class::isInstance)
                .map(TimedSelfDestroyer.class::cast)
                .map(TimedSelfDestroyer::getGauge))
                .anyMatch(gauge -> gauge.category() == GaugeCategory.TTL
                        && gauge.maxValue() == 15f);
    }

    private Stream<Component> components(GameObject gameObject) {
        return Stream.concat(
                gameObject.getComponents().stream(),
                gameObject.getComponentsToAdd().stream()
        );
    }
}
