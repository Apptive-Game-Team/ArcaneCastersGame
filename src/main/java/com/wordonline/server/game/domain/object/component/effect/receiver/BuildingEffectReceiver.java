package com.wordonline.server.game.domain.object.component.effect.receiver;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.EffectApplyPolicy;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.*;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Effect;

public class BuildingEffectReceiver extends CommonEffectReceiver {

    public BuildingEffectReceiver(GameObject gameObject) {
        super(gameObject);
    }

    @Override
    public void onReceive(Effect effect) {
        switch (effect) {
            case Wet -> {
                float duration = statusParameters().floatValue(ParameterKey.WET_DURATION);
                applyEffect(
                    StatusEffectKey.Wet_Receive,
                    () -> new WetStatusEffect(gameObject, duration, StatusEffectKey.Wet_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    duration);
            }
            case Burn -> {
                float duration = statusParameters().floatValue(ParameterKey.BURN_DURATION);
                applyEffect(
                    StatusEffectKey.Burn_Receive,
                    () -> new BurnStatusEffect(gameObject, duration, StatusEffectKey.Burn_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    duration);
            }
            case Snared -> {
                var values = statusParameters();
                float duration = values.floatValue(ParameterKey.SNARE_DURATION);
                int heal = values.intValue(ParameterKey.BUILDING_SNARE_HEAL);
                applyEffect(
                    StatusEffectKey.Snared_Receive,
                    () -> new DOTStatusEffect(gameObject, duration, -heal, ElementType.NONE, StatusEffectKey.Snared_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    duration);
            }
            case Inspired -> applyEffect(
                    StatusEffectKey.Inspired_Receive,
                    () -> new InspiredStatusEffect(gameObject, 10f, StatusEffectKey.Inspired_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    10f);
        }

    }

    @Override
    public void onReceive(Effect effect, float duration) {
        if (effect != Effect.Inspired) {
            onReceive(effect);
            return;
        }

        applyEffect(
                StatusEffectKey.Inspired_Receive,
                () -> new InspiredStatusEffect(gameObject, duration, StatusEffectKey.Inspired_Receive),
                EffectApplyPolicy.REFRESH_DURATION,
                duration);
    }
}
