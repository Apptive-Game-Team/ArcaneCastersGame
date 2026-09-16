package com.wordonline.server.game.domain.object.component.effect.statuseffect;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.IntervalAttacker;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.dto.Effect;

public class InspiredStatusEffect extends BaseStatusEffect {

    private static final float ATTACK_SPEED_BONUS_PERCENT = 0.5f;
    private static final float ATTACK_INTERVAL_MODIFIER =
            -ATTACK_SPEED_BONUS_PERCENT / (1f + ATTACK_SPEED_BONUS_PERCENT);

    public InspiredStatusEffect(GameObject owner, float duration, StatusEffectKey key) {
        super(owner, duration, key, Effect.Inspired);
    }

    @Override
    public void start() {
        applyAttackSpeedModifier(ATTACK_INTERVAL_MODIFIER);
    }

    @Override
    public void onAttacked(ElementType attackType) {
    }

    @Override
    protected void expire() {
        applyAttackSpeedModifier(0f);
        super.expire();
    }

    private void applyAttackSpeedModifier(float modifier) {
        for (IntervalAttacker attacker : gameObject.getComponents(IntervalAttacker.class)) {
            attacker.getAttackInterval().setModifierPercent(modifier);
        }
    }
}
