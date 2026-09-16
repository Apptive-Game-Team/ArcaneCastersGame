package com.wordonline.server.game.domain.object.component.effect.receiver;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.EffectApplication;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.BaseStatusEffect;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.OverchargeStatusEffect;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LightningSummonEffectReceiverTest {

    private GameObject lightningSummon() {
        GameObject gameObject = new GameObject(Master.LeftPlayer, PrefabType.ElectricSlime, Vector3.ZERO,
                mock(GameContext.class));
        gameObject.setElement(ElementType.LIGHTNING);
        return gameObject;
    }

    @Test
    void shockAppliesOverchargeInsteadOfStun() {
        LightningSummonEffectReceiver receiver = new LightningSummonEffectReceiver(lightningSummon());

        receiver.onReceive(Effect.Shock);

        BaseStatusEffect shock = receiver.getEffectByKey(StatusEffectKey.Shock_Receive);
        BaseStatusEffect overcharge = receiver.getEffectByKey(StatusEffectKey.Overcharge_Receive);

        assertThat(shock).isNull();
        assertThat(overcharge).isInstanceOf(OverchargeStatusEffect.class);
    }

    @Test
    void restackingShockExtendsOverchargeDuration() {
        LightningSummonEffectReceiver receiver = new LightningSummonEffectReceiver(lightningSummon());

        receiver.onReceive(Effect.Shock);
        receiver.onReceive(Effect.Shock);

        BaseStatusEffect overcharge = receiver.getEffectByKey(StatusEffectKey.Overcharge_Receive);
        assertThat(overcharge.getRemaining()).isEqualTo(6f);
    }

    private GameObject shockSource(Master master) {
        return new GameObject(master, PrefabType.ElectricShot, Vector3.ZERO, mock(GameContext.class));
    }

    @Test
    void enemyShockDoesNotOverchargeTheSummon() {
        GameObject summon = lightningSummon();
        LightningSummonEffectReceiver receiver = new LightningSummonEffectReceiver(summon);

        receiver.onReceive(new EffectApplication(Effect.Shock, shockSource(Master.RightPlayer)));

        BaseStatusEffect overcharge = receiver.getEffectByKey(StatusEffectKey.Overcharge_Receive);
        BaseStatusEffect shock = receiver.getEffectByKey(StatusEffectKey.Shock_Receive);
        assertThat(overcharge).isNull();
        assertThat(shock).isNull();
    }

    @Test
    void friendlyShockOverchargesTheSummon() {
        GameObject summon = lightningSummon();
        LightningSummonEffectReceiver receiver = new LightningSummonEffectReceiver(summon);

        receiver.onReceive(new EffectApplication(Effect.Shock, shockSource(Master.LeftPlayer)));

        BaseStatusEffect overcharge = receiver.getEffectByKey(StatusEffectKey.Overcharge_Receive);
        assertThat(overcharge).isInstanceOf(OverchargeStatusEffect.class);
    }

    @Test
    void neutralFieldShockOverchargesEitherSide() {
        GameObject summon = lightningSummon();
        LightningSummonEffectReceiver receiver = new LightningSummonEffectReceiver(summon);

        receiver.onReceive(new EffectApplication(Effect.Shock, shockSource(Master.None)));

        BaseStatusEffect overcharge = receiver.getEffectByKey(StatusEffectKey.Overcharge_Receive);
        assertThat(overcharge).isInstanceOf(OverchargeStatusEffect.class);
    }

    @Test
    void commonReceiverKeepsShockImmunityForLightningUnits() {
        CommonEffectReceiver receiver = new CommonEffectReceiver(lightningSummon());

        receiver.onReceive(Effect.Shock);

        BaseStatusEffect shock = receiver.getEffectByKey(StatusEffectKey.Shock_Receive);
        BaseStatusEffect overcharge = receiver.getEffectByKey(StatusEffectKey.Overcharge_Receive);

        assertThat(shock).isNull();
        assertThat(overcharge).isNull();
    }
}
