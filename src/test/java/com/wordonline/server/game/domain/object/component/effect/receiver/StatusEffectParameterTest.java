package com.wordonline.server.game.domain.object.component.effect.receiver;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusEffectParameterTest {

    private final Parameters parameters = mock(Parameters.class);
    private final GameContext gameContext = mock(GameContext.class);

    @BeforeEach
    void setUp() {
        Map<ParameterKey, Double> tuned = Map.ofEntries(
                Map.entry(ParameterKey.BURN_DURATION, 7.0),
                Map.entry(ParameterKey.BURN_TOTAL_DAMAGE, 9.0),
                Map.entry(ParameterKey.WET_DURATION, 6.0),
                Map.entry(ParameterKey.WET_NATURE_HEAL, 4.0),
                Map.entry(ParameterKey.SHOCK_STUN_DURATION, 2.0),
                Map.entry(ParameterKey.SHOCK_REFRESH_DURATION, 5.0),
                Map.entry(ParameterKey.SNARE_DURATION, 8.0),
                Map.entry(ParameterKey.SNARE_FIRE_DAMAGE, 11.0),
                Map.entry(ParameterKey.SNARE_SLOW_PERCENT, 0.25),
                Map.entry(ParameterKey.LEAF_FIELD_HEAL_DURATION, 12.0),
                Map.entry(ParameterKey.LEAF_FIELD_HEAL_AMOUNT, 6.0),
                Map.entry(ParameterKey.SANDSTORM_EFFECT_DURATION, 1.5),
                Map.entry(ParameterKey.SANDSTORM_EFFECT_DAMAGE, 4.0),
                Map.entry(ParameterKey.BUILDING_SNARE_HEAL, 3.0));
        when(gameContext.getParameters()).thenReturn(parameters);
        when(parameters.object(GameObjectKey.GAME))
                .thenReturn(new GameObjectParameters(GameObjectKey.GAME, parameters));
        when(parameters.getValue(eq(GameObjectKey.GAME), any(ParameterKey.class)))
                .thenAnswer(invocation -> tuned.get(invocation.getArgument(1)));
    }

    @Test
    void ordinaryEffectsReadTunedValues() {
        CommonEffectReceiver receiver = receiver(ElementType.NATURE);

        receiver.onReceive(Effect.Burn);
        assertThat(receiver.getEffectByKey(StatusEffectKey.Burn_Receive).getRemaining()).isEqualTo(7f);
        assertThat(receiver.getEffectByKey(StatusEffectKey.DOTDeal_Burn).getRemaining()).isEqualTo(7f);
        verify(parameters).getValue(GameObjectKey.GAME, ParameterKey.BURN_TOTAL_DAMAGE);

        receiver.onReceive(Effect.Wet);
        assertThat(receiver.getEffectByKey(StatusEffectKey.Wet_Receive).getRemaining()).isEqualTo(6f);
        assertThat(receiver.getEffectByKey(StatusEffectKey.DOTHeal_NatureWithWaterField).getRemaining()).isEqualTo(6f);
        verify(parameters).getValue(GameObjectKey.GAME, ParameterKey.WET_NATURE_HEAL);

        receiver.onReceive(Effect.LeafFieldHeal);
        assertThat(receiver.getEffectByKey(StatusEffectKey.DOTHeal_NatureField).getRemaining()).isEqualTo(12f);
        verify(parameters).getValue(GameObjectKey.GAME, ParameterKey.LEAF_FIELD_HEAL_AMOUNT);

        receiver.onReceive(Effect.Sandstorm);
        assertThat(receiver.getEffectByKey(StatusEffectKey.DOT_SandStorm).getRemaining()).isEqualTo(1.5f);
        verify(parameters).getValue(GameObjectKey.GAME, ParameterKey.SANDSTORM_EFFECT_DAMAGE);
    }

    @Test
    void controlEffectsReadTunedValues() {
        CommonEffectReceiver receiver = receiver(ElementType.NONE);

        receiver.onReceive(Effect.Shock);
        assertThat(receiver.getEffectByKey(StatusEffectKey.Shock_Receive).getRemaining()).isEqualTo(2f);
        verify(parameters).getValue(GameObjectKey.GAME, ParameterKey.SHOCK_REFRESH_DURATION);

        receiver.onReceive(Effect.Snared);
        assertThat(receiver.getEffectByKey(StatusEffectKey.Snared_Receive).getRemaining()).isEqualTo(8f);
        verify(parameters).getValue(GameObjectKey.GAME, ParameterKey.SNARE_FIRE_DAMAGE);
        verify(parameters).getValue(GameObjectKey.GAME, ParameterKey.SNARE_SLOW_PERCENT);
    }

    @Test
    void buildingSnareHealReadsTunedValue() {
        GameObject owner = owner(ElementType.NONE);
        BuildingEffectReceiver receiver = new BuildingEffectReceiver(owner);

        receiver.onReceive(Effect.Snared);

        assertThat(receiver.getEffectByKey(StatusEffectKey.Snared_Receive).getRemaining()).isEqualTo(8f);
        verify(parameters).getValue(GameObjectKey.GAME, ParameterKey.BUILDING_SNARE_HEAL);
    }

    private CommonEffectReceiver receiver(ElementType element) {
        return new CommonEffectReceiver(owner(element));
    }

    private GameObject owner(ElementType element) {
        GameObject owner = new GameObject(Master.LeftPlayer, PrefabType.SeedSpirit, Vector3.ZERO, gameContext);
        owner.setElement(element);
        return owner;
    }
}
