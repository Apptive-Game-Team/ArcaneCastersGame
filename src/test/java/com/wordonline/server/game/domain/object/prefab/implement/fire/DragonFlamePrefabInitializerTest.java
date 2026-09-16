package com.wordonline.server.game.domain.object.prefab.implement.fire;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.EffectProvider;
import com.wordonline.server.game.domain.object.component.magic.Shot;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DragonFlamePrefabInitializerTest {

    @Test
    void advertisesDragonFlamePrefabType() {
        DragonFlamePrefabInitializer initializer = new DragonFlamePrefabInitializer(mock(Parameters.class));

        assertThat(initializer.prefabType).isEqualTo(PrefabType.DragonFlame);
    }

    @Test
    void buildsABurningFireShotThatCarriesTheFlamesOwnDamageAndSpeed() {
        GameObject dragonFlame = initializedDragonFlame();

        assertThat(dragonFlame.getElement().has(ElementType.FIRE)).isTrue();

        // a trigger, not a body: the flame passes through rather than pushing what it hits
        CircleCollider collider = dragonFlame.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(0.3f);
        assertThat(collider.isTrigger()).isTrue();

        Shot shot = findComponent(dragonFlame, Shot.class);
        assertThat(shot).isNotNull();
        assertThat(ReflectionTestUtils.getField(shot, "damage")).isEqualTo(30);
        assertThat(ReflectionTestUtils.getField(shot, "speed")).isEqualTo(8f);

        EffectProvider effectProvider = findComponent(dragonFlame, EffectProvider.class);
        assertThat(effectProvider).isNotNull();
        assertThat(ReflectionTestUtils.getField(effectProvider, "effect")).isEqualTo(Effect.Burn);
    }

    // Shot itself has no despawn rule; GameObject.setPosition destroys anything it moves past the
    // field bounds. dragon_flame leans on that, because unlike fire_shot it is always fired with
    // no target and reaches the edge whenever its row is empty.
    @Test
    void isDestroyedWhenItFliesOffTheField() {
        GameObject dragonFlame = initializedDragonFlame(new Vector3(4f, 0f, 5f));
        when(dragonFlame.getGameContext().getDeltaTime()).thenReturn(1f);
        Shot shot = findComponent(dragonFlame, Shot.class);
        shot.setTarget(new Vector3(18f, 0f, 5f));

        shot.update();
        assertThat(dragonFlame.getPosition().getX()).isEqualTo(12f);
        assertThat(dragonFlame.isDestroyed()).isFalse();

        shot.update();
        assertThat(dragonFlame.isDestroyed()).isTrue();
    }

    private GameObject initializedDragonFlame() {
        return initializedDragonFlame(Vector3.ZERO);
    }

    private GameObject initializedDragonFlame(Vector3 position) {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters dragonFlameParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.DRAGON_FLAME)).thenReturn(dragonFlameParameters);
        when(dragonFlameParameters.floatValue(ParameterKey.RADIUS)).thenReturn(0.3f);
        when(dragonFlameParameters.intValue(ParameterKey.DAMAGE)).thenReturn(30);
        when(dragonFlameParameters.floatValue(ParameterKey.SPEED)).thenReturn(8f);

        GameObject dragonFlame = new GameObject(
                Master.LeftPlayer,
                PrefabType.DragonFlame,
                position,
                mock(GameContext.class)
        );

        new DragonFlamePrefabInitializer(parameters).initialize(dragonFlame);
        return dragonFlame;
    }

    private <T> T findComponent(GameObject gameObject, Class<T> clazz) {
        return Stream.concat(gameObject.getComponents().stream(), gameObject.getComponentsToAdd().stream())
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }
}
