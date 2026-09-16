package com.wordonline.server.game.domain.object.component.effect.receiver;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.component.effect.EffectApplication;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.OverchargeStatusEffect;
import com.wordonline.server.game.dto.Effect;

public class LightningSummonEffectReceiver extends CommonEffectReceiver {

    private static final float OVERCHARGE_DURATION = 3f;

    public LightningSummonEffectReceiver(GameObject gameObject) {
        super(gameObject);
    }

    /**
     * Overcharges {@code target} when it is a lightning summon, and does nothing otherwise.
     * Shock is the carrier so the overcharge duration stays defined in one place.
     */
    public static void overcharge(GameObject target) {
        LightningSummonEffectReceiver receiver = target.getComponent(LightningSummonEffectReceiver.class);
        if (receiver == null) {
            return;
        }

        receiver.onReceive(Effect.Shock);
    }

    @Override
    public void onReceive(EffectApplication application) {
        if (application.effect() == Effect.Shock && !application.isFriendlyTo(gameObject)) {
            // an enemy's shock must not charge this summon; lightning is immune to shock anyway
            super.onReceive(Effect.Shock);
            return;
        }

        onReceive(application.effect());
    }

    @Override
    public void onReceive(Effect effect) {
        if (effect != Effect.Shock) {
            super.onReceive(effect);
            return;
        }

        OverchargeStatusEffect.apply(gameObject, OVERCHARGE_DURATION);
    }
}
