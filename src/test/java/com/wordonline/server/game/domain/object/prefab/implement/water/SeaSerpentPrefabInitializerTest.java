package com.wordonline.server.game.domain.object.prefab.implement.water;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.PathSpawner;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.SeaSerpentMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

class SeaSerpentPrefabInitializerTest {

    @Test
    void initializesAnyTargetBeamAttackerAndWaterFieldTrail() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters values = mock(GameObjectParameters.class);

        when(parameters.object(GameObjectKey.SEA_SERPENT)).thenReturn(values);
        when(values.intValue(ParameterKey.MASS)).thenReturn(10);
        when(values.floatValue(ParameterKey.RADIUS)).thenReturn(1.2f);
        when(values.intValue(ParameterKey.HP)).thenReturn(160);
        when(values.floatValue(ParameterKey.SPEED)).thenReturn(0.55f);
        when(values.intValue(ParameterKey.DAMAGE)).thenReturn(16);
        when(values.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(3.5f);
        when(values.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(7f);
        when(values.floatValue(ParameterKey.BEAM_WIDTH)).thenReturn(1f);

        GameObject seaSerpent = new GameObject(
                Master.LeftPlayer,
                PrefabType.SeaSerpent,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new SeaSerpentPrefabInitializer(parameters).initialize(seaSerpent);

        SeaSerpentMob mob = seaSerpent.getComponentsToAdd().stream()
                .filter(SeaSerpentMob.class::isInstance)
                .map(SeaSerpentMob.class::cast)
                .findFirst()
                .orElseThrow();
        Object detector = ReflectionTestUtils.getField(mob, "detector");
        assertThat(ReflectionTestUtils.getField(detector, "targetMask"))
                .isEqualTo(TargetMask.ANY.bit);
        assertThat(ReflectionTestUtils.getField(mob, "attackRange")).isEqualTo(7f);

        PathSpawner pathSpawner = seaSerpent.getComponentsToAdd().stream()
                .filter(PathSpawner.class::isInstance)
                .map(PathSpawner.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(ReflectionTestUtils.getField(pathSpawner, "prefabType"))
                .isEqualTo(PrefabType.WaterField);
        assertThat(ReflectionTestUtils.getField(pathSpawner, "interval")).isEqualTo(1f);

        CircleCollider collider = seaSerpent.getFirstCircleCollider().orElseThrow();
        assertThat(collider.getRadius()).isEqualTo(1.2f);
    }
}
