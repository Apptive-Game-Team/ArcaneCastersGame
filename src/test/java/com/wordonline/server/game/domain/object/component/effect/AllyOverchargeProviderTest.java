package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.effect.receiver.CommonEffectReceiver;
import com.wordonline.server.game.domain.object.component.effect.receiver.LightningSummonEffectReceiver;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.BaseStatusEffect;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.OverchargeStatusEffect;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AllyOverchargeProviderTest {

    private final GameContext gameContext = mock(GameContext.class);

    private GameObject electricShot(Master master) {
        GameObject gameObject = new GameObject(master, PrefabType.ElectricShot, Vector3.ZERO, gameContext);
        gameObject.setElement(ElementType.LIGHTNING);
        return gameObject;
    }

    private GameObject lightningSummon(Master master) {
        GameObject gameObject = new GameObject(master, PrefabType.ElectricSlime, Vector3.ZERO, gameContext);
        gameObject.setElement(ElementType.LIGHTNING);
        gameObject.addComponent(new LightningSummonEffectReceiver(gameObject));
        gameObject.flushComponents();
        return gameObject;
    }

    private GameObject rockSummon(Master master) {
        GameObject gameObject = new GameObject(master, PrefabType.RockSlime, Vector3.ZERO, gameContext);
        gameObject.setElement(ElementType.ROCK);
        gameObject.addComponent(new CommonEffectReceiver(gameObject));
        gameObject.flushComponents();
        return gameObject;
    }

    private BaseStatusEffect overchargeOf(GameObject gameObject) {
        return gameObject.getComponent(LightningSummonEffectReceiver.class)
                .getEffectByKey(StatusEffectKey.Overcharge_Receive);
    }

    @Test
    void ownLightningSummonIsOverchargedOnContact() {
        GameObject shot = electricShot(Master.LeftPlayer);
        GameObject summon = lightningSummon(Master.LeftPlayer);

        new AllyOverchargeProvider(shot).onCollision(summon);

        assertThat(overchargeOf(summon)).isInstanceOf(OverchargeStatusEffect.class);
    }

    @Test
    void enemyLightningSummonIsNotOvercharged() {
        GameObject shot = electricShot(Master.LeftPlayer);
        GameObject summon = lightningSummon(Master.RightPlayer);

        new AllyOverchargeProvider(shot).onCollision(summon);

        assertThat(overchargeOf(summon)).isNull();
    }

    @Test
    void ownNonLightningSummonTakesNothing() {
        GameObject shot = electricShot(Master.LeftPlayer);
        GameObject summon = rockSummon(Master.LeftPlayer);

        new AllyOverchargeProvider(shot).onCollision(summon);

        assertThat(summon.getComponents(BaseStatusEffect.class)).isEmpty();
        assertThat(summon.getComponentsToAdd()).isEmpty();
    }

    @Test
    void enemyCollisionCallbackDoesNothing() {
        GameObject shot = electricShot(Master.LeftPlayer);
        GameObject summon = lightningSummon(Master.LeftPlayer);

        new AllyOverchargeProvider(shot).onCollisionWithEnemy(summon);

        assertThat(overchargeOf(summon)).isNull();
    }
}
