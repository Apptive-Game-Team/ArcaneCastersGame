package com.wordonline.server.game.domain.object.prefab.implement.subprefab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.OnStartAttacker;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.frame.GaugeCategory;
import com.wordonline.server.game.service.GameContext;

class TitanFistPrefabInitializerTest {

    @Test
    void configuresRockAreaAttackAndPresentationLifetime() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters stats = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.TITAN_FIST)).thenReturn(stats);
        when(stats.floatValue(ParameterKey.RADIUS)).thenReturn(2f);
        when(stats.intValue(ParameterKey.DAMAGE)).thenReturn(30);
        when(stats.floatValue(ParameterKey.DURATION)).thenReturn(1f);

        GameObject fist = new GameObject(
                Master.LeftPlayer,
                PrefabType.TitanFist,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new TitanFistPrefabInitializer(parameters).initialize(fist);

        assertThat(fist.getElement().nativeHas(ElementType.ROCK)).isTrue();
        assertThat(components(fist)).anyMatch(OnStartAttacker.class::isInstance);
        assertThat(fist.getFirstCircleCollider()).hasValueSatisfying(collider -> {
            assertThat(collider.getRadius()).isEqualTo(2f);
            assertThat(collider.isTrigger()).isTrue();
        });

        TimedSelfDestroyer lifetime = components(fist)
                .filter(TimedSelfDestroyer.class::isInstance)
                .map(TimedSelfDestroyer.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(lifetime.getGauge().maxValue()).isEqualTo(1f);
        assertThat(lifetime.getGauge().category()).isEqualTo(GaugeCategory.TTL);
    }

    private Stream<Component> components(GameObject gameObject) {
        return Stream.concat(
                gameObject.getComponents().stream(),
                gameObject.getComponentsToAdd().stream()
        );
    }
}
