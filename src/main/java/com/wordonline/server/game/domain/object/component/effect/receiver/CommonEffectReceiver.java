package com.wordonline.server.game.domain.object.component.effect.receiver;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.effect.EffectApplyPolicy;
import com.wordonline.server.game.domain.object.component.effect.EffectImmuneChart;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.*;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Effect;
import java.util.function.Supplier;

public class CommonEffectReceiver extends Component implements EffectReceiver {

    public <T extends BaseStatusEffect> T getEffectByKey(StatusEffectKey key) {
        for (Component c : gameObject.getComponents())
            if (c instanceof BaseStatusEffect se && key.equals(se.getKey())) return (T) se;
        for (Component c : gameObject.getComponentsToAdd())
            if (c instanceof BaseStatusEffect se && key.equals(se.getKey())) return (T) se;
        return null;
    }

    public <T extends BaseStatusEffect> void applyEffect(
            StatusEffectKey key,
            Supplier<T> factory, // Supplier<T> : () -> 형식의 팩토리
            EffectApplyPolicy policy,
            float duration // 재적용 시 갱신
    ) {
        T existing = getEffectByKey(key);
        if (existing != null) {
            switch (policy) {
                case IGNORE -> {}
                case REFRESH_DURATION -> existing.refresh(duration);
                case EXTEND_DURATION -> existing.extend(duration); // 2단 상태이상 구현 x
            }
            return;
        }
        T created = factory.get();
        gameObject.addComponent(created);
    }

    @Override
    public void onReceive(Effect effect) {
        if(EffectImmuneChart.isImmuneTo(gameObject, effect)) return;

        switch (effect) {
            case Wet -> {
                var values = statusParameters();
                float duration = values.floatValue(ParameterKey.WET_DURATION);
                applyEffect(
                    StatusEffectKey.Wet_Receive,
                    () -> new WetStatusEffect(gameObject, duration, StatusEffectKey.Wet_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    duration);
                if(gameObject.getElement().has(ElementType.NATURE)){
                    int heal = values.intValue(ParameterKey.WET_NATURE_HEAL);
                    applyEffect(
                            StatusEffectKey.DOTHeal_NatureWithWaterField,
                            () -> new DOTStatusEffect(gameObject, duration, -heal, ElementType.NONE, StatusEffectKey.DOTHeal_NatureWithWaterField),
                            EffectApplyPolicy.REFRESH_DURATION,
                            duration);
                }
            }
            case Burn -> {
                var values = statusParameters();
                float duration = values.floatValue(ParameterKey.BURN_DURATION);
                int damage = values.intValue(ParameterKey.BURN_TOTAL_DAMAGE);
                applyEffect(
                    StatusEffectKey.Burn_Receive,
                    () -> new BurnStatusEffect(gameObject, duration, StatusEffectKey.Burn_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    duration);
                applyEffect(
                    StatusEffectKey.DOTDeal_Burn,
                    () -> new DOTStatusEffect(gameObject, duration, damage, ElementType.FIRE, StatusEffectKey.DOTDeal_Burn),
                    EffectApplyPolicy.REFRESH_DURATION,
                    duration);
            }
            case Shock -> {
                var values = statusParameters();
                float duration = values.floatValue(ParameterKey.SHOCK_STUN_DURATION);
                applyEffect(
                    StatusEffectKey.Shock_Receive,
                    () -> new ShockStatusEffect(gameObject, duration, StatusEffectKey.Shock_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    values.floatValue(ParameterKey.SHOCK_REFRESH_DURATION));
            }
            case Snared -> {
                var values = statusParameters();
                float duration = values.floatValue(ParameterKey.SNARE_DURATION);
                applyEffect(
                        StatusEffectKey.Snared_Receive,
                        () -> new SnaredStatusEffect(gameObject, duration,
                                values.intValue(ParameterKey.SNARE_FIRE_DAMAGE),
                                values.floatValue(ParameterKey.SNARE_SLOW_PERCENT),
                                StatusEffectKey.Snared_Receive),
                        EffectApplyPolicy.REFRESH_DURATION,
                        duration);
            }

            case LeafFieldHeal -> {
                var values = statusParameters();
                float duration = values.floatValue(ParameterKey.LEAF_FIELD_HEAL_DURATION);
                int heal = values.intValue(ParameterKey.LEAF_FIELD_HEAL_AMOUNT);
                applyEffect(
                        StatusEffectKey.DOTHeal_NatureField,
                        () -> new DOTStatusEffect(gameObject, duration, -heal, ElementType.NONE, StatusEffectKey.DOTHeal_NatureField),
                        EffectApplyPolicy.REFRESH_DURATION,
                        duration);
            }
            case Sandstorm -> {
                var values = statusParameters();
                float duration = values.floatValue(ParameterKey.SANDSTORM_EFFECT_DURATION);
                int damage = values.intValue(ParameterKey.SANDSTORM_EFFECT_DAMAGE);
                applyEffect(
                        StatusEffectKey.DOT_SandStorm,
                        () -> new DOTStatusEffect(gameObject, duration, damage, ElementType.NONE, StatusEffectKey.DOT_SandStorm),
                        EffectApplyPolicy.REFRESH_DURATION,
                        duration);
            }
            case Frenzy -> applyEffect(
                        StatusEffectKey.Frenzy_Receive,
                        () -> new FrenzyStatusEffect(gameObject, 10f, StatusEffectKey.Frenzy_Receive),
                        EffectApplyPolicy.REFRESH_DURATION,
                        10f);
            case Bubble -> applyEffect(
                    StatusEffectKey.Bubble_Receive,
                    () -> new BubbleStatusEffect(gameObject, 8f, StatusEffectKey.Bubble_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    8f);
            case Inspired -> applyEffect(
                    StatusEffectKey.Inspired_Receive,
                    () -> new InspiredStatusEffect(gameObject, 10f, StatusEffectKey.Inspired_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    10f);

        }
    }

    @Override
    public void onReceive(Effect effect, float duration) {
        if (effect == Effect.Frenzy) {
            applyEffect(
                    StatusEffectKey.Frenzy_Receive,
                    () -> new FrenzyStatusEffect(gameObject, duration, StatusEffectKey.Frenzy_Receive),
                    EffectApplyPolicy.REFRESH_DURATION,
                    duration);
            return;
        }

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

    @Override
    public void onReceive(Effect effect, Vector3 direction, float prox) {
        applyEffect(
                StatusEffectKey.Knockback_Receive,
                () -> new KnockbackStatusEffect(gameObject, direction, prox, StatusEffectKey.Knockback_Receive),
                EffectApplyPolicy.IGNORE,
                3f);
    }

    @Override
    public void start() { }

    @Override
    public void update() { }

    @Override
    public void onDestroy() { }

    public CommonEffectReceiver(GameObject gameObject) {
        super(gameObject);
    }

    protected GameObjectParameters statusParameters() {
        return getGameContext().getParameters().object(GameObjectKey.GAME);
    }
}
