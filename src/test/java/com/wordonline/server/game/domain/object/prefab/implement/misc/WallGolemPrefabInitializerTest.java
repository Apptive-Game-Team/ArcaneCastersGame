package com.wordonline.server.game.domain.object.prefab.implement.misc;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.RockDeathRemnant;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.RockGolemMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.ZPhysics;
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

class WallGolemPrefabInitializerTest {

    @Test
    void advertisesWallGolemPrefabType() {
        WallGolemPrefabInitializer initializer = new WallGolemPrefabInitializer(mock(Parameters.class));

        assertThat(initializer.prefabType).isEqualTo(PrefabType.WallGolem);
    }

    @Test
    void leavesARemnantWhenKilledInCombat() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters wallGolemParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.WALL_GOLEM)).thenReturn(wallGolemParameters);
        GameObject wallGolem = new GameObject(
                Master.LeftPlayer,
                PrefabType.WallGolem,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new WallGolemPrefabInitializer(parameters).initialize(wallGolem);

        assertThat(wallGolem.getComponentsToAdd()).anyMatch(RockDeathRemnant.class::isInstance);
    }

    @Test
    void isASlowHeavilyArmoredMeleeGolemWithSolidCircleCollider() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters wallGolemParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.WALL_GOLEM)).thenReturn(wallGolemParameters);
        when(wallGolemParameters.intValue(ParameterKey.HP)).thenReturn(5000);
        when(wallGolemParameters.floatValue(ParameterKey.SPEED)).thenReturn(0.1f);
        when(wallGolemParameters.intValue(ParameterKey.DAMAGE)).thenReturn(5);
        when(wallGolemParameters.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(1.5f);
        when(wallGolemParameters.intValue(ParameterKey.MASS)).thenReturn(200);
        when(wallGolemParameters.floatValue(ParameterKey.RADIUS)).thenReturn(1.5f);
        GameObject wallGolem = new GameObject(
                Master.LeftPlayer,
                PrefabType.WallGolem,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new WallGolemPrefabInitializer(parameters).initialize(wallGolem);

        RockGolemMob mob = wallGolem.getComponent(RockGolemMob.class);
        assertThat(mob).isNotNull();
        assertThat(mob.getHp()).isEqualTo(5000);
        assertThat(mob.getMaxHp()).isEqualTo(5000);

        CircleCollider collider = wallGolem.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(1.5f);
        assertThat(collider.isTrigger()).isFalse();

        assertThat(wallGolem.getComponents(RigidBody.class)).isNotEmpty();
        assertThat(wallGolem.getComponents(ZPhysics.class)).isNotEmpty();
        assertThat(wallGolem.getComponents(CommonEffectReceiver.class)).isNotEmpty();
    }
}
