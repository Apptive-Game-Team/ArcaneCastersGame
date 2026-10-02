package com.wordonline.server.game.domain.object.prefab.implement.rock;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediumRockRemnantPrefabInitializerTest {

    @Test
    void isLargeSolidRockRubbleWithoutCombatBehaviour() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters remnantParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.MEDIUM_ROCK_REMNANT)).thenReturn(remnantParameters);
        when(remnantParameters.floatValue(ParameterKey.RADIUS)).thenReturn(1f);
        GameObject remnant = new GameObject(
                Master.LeftPlayer,
                PrefabType.MediumRockRemnant,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new MediumRockRemnantPrefabInitializer(parameters).initialize(remnant);

        CircleCollider collider = remnant.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(1f);
        assertThat(collider.isTrigger()).isFalse();
        assertThat(collider.getInvMass()).isZero();
        assertThat(components(remnant)).anyMatch(Collidable.class::isInstance);
        assertThat(components(remnant)).anyMatch(TimedSelfDestroyer.class::isInstance);
        assertThat(components(remnant)).noneMatch(Mob.class::isInstance);
        assertThat(components(remnant)).noneMatch(Damageable.class::isInstance);
        assertThat(remnant.getElement().nativeHas(ElementType.ROCK)).isTrue();
    }

    private Stream<Component> components(GameObject gameObject) {
        return Stream.concat(
                gameObject.getComponents().stream(),
                gameObject.getComponentsToAdd().stream()
        );
    }
}
