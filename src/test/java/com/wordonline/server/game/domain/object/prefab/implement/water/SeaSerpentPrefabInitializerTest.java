package com.wordonline.server.game.domain.object.prefab.implement.water;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.PathSpawner;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.SeaSerpentMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.service.GameContext;

class SeaSerpentPrefabInitializerTest {

    @Test
    void initializesAnyTargetBeamAttackerAndWaterFieldTrail() {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters values = mock(GameObjectParameters.class);
        GameObject seaSerpent = mock(GameObject.class);
        List<Component> components = new ArrayList<>();

        when(parameters.object(GameObjectKey.SEA_SERPENT)).thenReturn(values);
        when(values.intValue(ParameterKey.MASS)).thenReturn(10);
        when(values.floatValue(ParameterKey.RADIUS)).thenReturn(1.2f);
        when(values.intValue(ParameterKey.HP)).thenReturn(160);
        when(values.floatValue(ParameterKey.SPEED)).thenReturn(0.55f);
        when(values.intValue(ParameterKey.DAMAGE)).thenReturn(16);
        when(values.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(3.5f);
        when(values.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(7f);
        when(values.floatValue(ParameterKey.BEAM_WIDTH)).thenReturn(1f);
        when(seaSerpent.getPosition()).thenReturn(Vector3.ZERO);
        when(seaSerpent.getGameContext()).thenReturn(mock(GameContext.class));
        when(seaSerpent.getComponents()).thenReturn(components);

        new SeaSerpentPrefabInitializer(parameters).initialize(seaSerpent);

        SeaSerpentMob mob = components.stream()
                .filter(SeaSerpentMob.class::isInstance)
                .map(SeaSerpentMob.class::cast)
                .findFirst()
                .orElseThrow();
        Object detector = ReflectionTestUtils.getField(mob, "detector");
        assertThat(ReflectionTestUtils.getField(detector, "targetMask"))
                .isEqualTo(TargetMask.ANY.bit);
        assertThat(ReflectionTestUtils.getField(mob, "attackRange")).isEqualTo(7f);

        PathSpawner pathSpawner = components.stream()
                .filter(PathSpawner.class::isInstance)
                .map(PathSpawner.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(ReflectionTestUtils.getField(pathSpawner, "prefabType"))
                .isEqualTo(PrefabType.WaterField);
        assertThat(ReflectionTestUtils.getField(pathSpawner, "interval")).isEqualTo(1f);

        ArgumentCaptor<CircleCollider> collider = ArgumentCaptor.forClass(CircleCollider.class);
        verify(seaSerpent).addCollider(collider.capture());
        assertThat(collider.getValue().getRadius()).isEqualTo(1.2f);
    }
}
