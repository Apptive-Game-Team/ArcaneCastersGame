package com.wordonline.server.game.domain.object.prefab.implement;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.magic.RallyingTotem;
import com.wordonline.server.game.domain.object.component.mob.simple.Totem;
import com.wordonline.server.game.domain.object.prefab.PrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.object.prefab.implement.build.LifeTreePrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.build.RallyingTotemPrefabInitializer;
import com.wordonline.server.game.domain.object.prefab.implement.misc.HealingTotemPrefabInitializer;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V084 redefined {@code range} on these objects as the cast range (6) and moved their area to
 * {@code effect_radius} (1.5, 1.5 and 2). RANGE and EFFECT_RADIUS are mocked to different values
 * so an initializer that reads the cast range fails here.
 */
class TotemEffectRadiusPrefabInitializerTest {

    private static final float CAST_RANGE = 6f;
    private static final float EFFECT_RADIUS = 1.5f;

    @Test
    void healingTotemHealsWithinEffectRadius() {
        GameObject totem = initialize(GameObjectKey.HEALING_TOTEM, PrefabType.HealingTotem,
                HealingTotemPrefabInitializer::new);

        assertThat(ReflectionTestUtils.getField(component(totem, Totem.class), "healRange"))
                .isEqualTo(EFFECT_RADIUS);
    }

    @Test
    void lifeTreeHealsWithinEffectRadius() {
        GameObject tree = initialize(GameObjectKey.LIFE_TREE, PrefabType.LifeTree,
                LifeTreePrefabInitializer::new);

        assertThat(ReflectionTestUtils.getField(component(tree, Totem.class), "healRange"))
                .isEqualTo(EFFECT_RADIUS);
    }

    @Test
    void rallyingTotemRalliesWithinEffectRadius() {
        GameObject totem = initialize(GameObjectKey.RALLYING_TOTEM, PrefabType.RallyingTotem,
                RallyingTotemPrefabInitializer::new);

        assertThat(ReflectionTestUtils.getField(component(totem, RallyingTotem.class), "rallyCombatRange"))
                .isEqualTo(EFFECT_RADIUS);
    }

    private GameObject initialize(GameObjectKey key, PrefabType type,
                                  Function<Parameters, PrefabInitializer> initializer) {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters objectParameters = mock(GameObjectParameters.class);
        when(parameters.object(key)).thenReturn(objectParameters);
        when(objectParameters.intValue(ParameterKey.HP)).thenReturn(100);
        when(objectParameters.floatValue(ParameterKey.RADIUS)).thenReturn(0.5f);
        when(objectParameters.floatValue(ParameterKey.RANGE)).thenReturn(CAST_RANGE);
        when(objectParameters.floatValue(ParameterKey.EFFECT_RADIUS)).thenReturn(EFFECT_RADIUS);

        GameObject gameObject = new GameObject(Master.LeftPlayer, type, Vector3.ZERO, mock(GameContext.class));
        initializer.apply(parameters).initialize(gameObject);
        return gameObject;
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
