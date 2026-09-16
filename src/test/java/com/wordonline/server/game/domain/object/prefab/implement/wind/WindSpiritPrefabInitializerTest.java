package com.wordonline.server.game.domain.object.prefab.implement.wind;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.detector.ClosestEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.BehaviorMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.SelfDestructMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WindSpiritPrefabInitializerTest {

    @Test
    void targetsOnlyAerialUnits() throws Exception {
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters windSpiritParameters = mock(GameObjectParameters.class);
        when(parameters.object(GameObjectKey.WIND_SPIRIT)).thenReturn(windSpiritParameters);
        when(windSpiritParameters.intValue(ParameterKey.HP)).thenReturn(12);
        when(windSpiritParameters.intValue(ParameterKey.MASS)).thenReturn(1);
        when(windSpiritParameters.intValue(ParameterKey.DAMAGE)).thenReturn(3);
        when(windSpiritParameters.floatValue(ParameterKey.RADIUS)).thenReturn(0.5f);
        when(windSpiritParameters.floatValue(ParameterKey.SPEED)).thenReturn(2f);
        when(windSpiritParameters.floatValue(ParameterKey.ATTACK_INTERVAL)).thenReturn(1f);
        when(windSpiritParameters.floatValue(ParameterKey.ATTACK_RANGE)).thenReturn(1.5f);
        GameObject windSpirit = new GameObject(
                Master.LeftPlayer,
                PrefabType.WindSpirit,
                Vector3.ZERO,
                mock(GameContext.class)
        );

        new WindSpiritPrefabInitializer(parameters).initialize(windSpirit);

        SelfDestructMob mob = windSpirit.getComponentsToAdd().stream()
                .filter(SelfDestructMob.class::isInstance)
                .map(SelfDestructMob.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(targetMaskOf(mob)).isEqualTo(TargetMask.AIR.bit);
        assertThat(targetMaskOf(mob) & TargetMask.AIR.bit).isNotZero();
        assertThat(targetMaskOf(mob) & TargetMask.GROUND.bit).isZero();
    }

    // The detector and its mask are internal to the mob state machine, so the wiring is read back
    // reflectively instead of widening production visibility for a test.
    private int targetMaskOf(SelfDestructMob mob) throws Exception {
        Field detectorField = BehaviorMob.class.getDeclaredField("detector");
        detectorField.setAccessible(true);
        ClosestEnemyDetector detector = (ClosestEnemyDetector) detectorField.get(mob);
        Field maskField = ClosestEnemyDetector.class.getDeclaredField("targetMask");
        maskField.setAccessible(true);
        return (int) maskField.get(detector);
    }
}
