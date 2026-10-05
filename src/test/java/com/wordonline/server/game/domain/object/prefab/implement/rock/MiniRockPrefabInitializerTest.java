package com.wordonline.server.game.domain.object.prefab.implement.rock;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.TimedSelfDestroyer;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.Slime;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniRockPrefabInitializerTest {

    @Test
    void advertisesMiniRockPrefabType() {
        MiniRockPrefabInitializer initializer = new MiniRockPrefabInitializer(mock(Parameters.class));

        assertThat(initializer.prefabType).isEqualTo(PrefabType.MiniRock);
    }

    @Test
    void doesNotLeaveARemnantBecauseItIsSmall() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters miniRockParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.MINI_ROCK)).thenReturn(miniRockParameters);
        GameObject miniRock = new GameObject(
                Master.LeftPlayer,
                PrefabType.MiniRock,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new MiniRockPrefabInitializer(parameters).initialize(miniRock);

        assertThat(miniRock.getComponentsToAdd()).noneMatch(RockDeathRemnant.class::isInstance);
    }

    @Test
    void remainsHpBasedSlimeWithSolidCircleCollider() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters miniRockParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.MINI_ROCK)).thenReturn(miniRockParameters);
        when(miniRockParameters.intValue(ParameterKey.HP)).thenReturn(17);
        when(miniRockParameters.floatValue(ParameterKey.RADIUS)).thenReturn(0.75f);
        GameObject miniRock = new GameObject(
                Master.LeftPlayer,
                PrefabType.MiniRock,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new MiniRockPrefabInitializer(parameters).initialize(miniRock);

        Slime slime = miniRock.getComponent(Slime.class);
        assertThat(slime).isNotNull();
        assertThat(slime.getHp()).isEqualTo(17);
        assertThat(slime.getMaxHp()).isEqualTo(17);
        CircleCollider collider = miniRock.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(0.75f);
        assertThat(collider.isTrigger()).isFalse();
        assertThat(miniRock.getComponents(TimedSelfDestroyer.class)).isEmpty();
    }
}
