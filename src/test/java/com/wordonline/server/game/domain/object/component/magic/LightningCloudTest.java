package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.Element;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.StatusEffectKey;
import com.wordonline.server.game.domain.object.component.effect.receiver.LightningSummonEffectReceiver;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.OverchargeStatusEffect;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.util.Physics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LightningCloudTest {

    private static final Vector3 CLOUD_POSITION = new Vector3(4f, 2f, 6f);

    private final GameObject cloudObject = mock(GameObject.class);
    private final GameContext gameContext = mock(GameContext.class);
    private final Physics physics = mock(Physics.class);

    private LightningCloud cloud(float strikeInterval, int strikeCount) {
        Element lightning = new Element();
        lightning.addNative(ElementType.LIGHTNING);

        when(cloudObject.getGameContext()).thenReturn(gameContext);
        when(cloudObject.getElement()).thenReturn(lightning);
        when(cloudObject.getMaster()).thenReturn(Master.LeftPlayer);
        when(cloudObject.getPosition()).thenReturn(CLOUD_POSITION);
        when(gameContext.getPhysics()).thenReturn(physics);
        when(physics.overlapBoxAll(any(), any())).thenReturn(List.of());

        return new LightningCloud(cloudObject, strikeInterval, strikeCount, 8, 0.5f);
    }

    @Test
    void strikesImmediatelyThenTwiceAtTwoSecondIntervals() {
        when(gameContext.getDeltaTime()).thenReturn(1.9f, 0.1f, 2f, 0.3f);
        LightningCloud cloud = cloud(2f, 3);

        cloud.start();
        verify(cloudObject, times(1)).setStatus(Status.Attack);

        cloud.update();
        verify(cloudObject, times(1)).setStatus(Status.Attack);

        cloud.update();
        verify(cloudObject, times(2)).setStatus(Status.Attack);

        cloud.update();
        verify(cloudObject, times(3)).setStatus(Status.Attack);

        // the cloud outlives its last strike so the client can finish growing the bolt
        verify(cloudObject, never()).destroy();

        cloud.update();
        verify(cloudObject).destroy();
    }

    @Test
    void createsNeutralElectricFieldUnderTheCloudOnEveryStrike() {
        LightningCloud cloud = cloud(2f, 1);

        cloud.start();

        ArgumentCaptor<GameObject> createdObject = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext).createGameObject(createdObject.capture());
        assertThat(createdObject.getValue().getType()).isEqualTo(PrefabType.ElectricField);
        assertThat(createdObject.getValue().getMaster()).isEqualTo(Master.None);
        assertThat(createdObject.getValue().getPosition()).isEqualTo(new Vector3(4f, 0f, 6f));
    }

    @Test
    void damagesEnemiesInTheColumnBelowTheCloud() {
        GameObject enemy = mock(GameObject.class);
        Damageable damageable = mock(Damageable.class);
        when(enemy.getMaster()).thenReturn(Master.RightPlayer);
        when(enemy.getComponents(Damageable.class)).thenReturn(List.of(damageable));

        LightningCloud cloud = cloud(2f, 1);
        when(physics.overlapBoxAll(any(), any())).thenReturn(List.of(enemy));

        cloud.start();

        verify(enemy).setStatus(Status.Damaged);
        verify(damageable).onDamaged(any());
    }

    @Test
    void leavesTheCastersOwnObjectsAlone() {
        GameObject ally = mock(GameObject.class);
        Damageable damageable = mock(Damageable.class);
        when(ally.getMaster()).thenReturn(Master.LeftPlayer);

        LightningCloud cloud = cloud(2f, 1);
        when(physics.overlapBoxAll(any(), any())).thenReturn(List.of(ally));

        cloud.start();

        verify(damageable, never()).onDamaged(any());
    }

    @Test
    void overchargesTheCastersOwnLightningSummonsInTheColumnBelow() {
        LightningCloud cloud = cloud(2f, 1);
        GameObject ownSummon = lightningSummon(Master.LeftPlayer);
        GameObject enemySummon = lightningSummon(Master.RightPlayer);
        when(physics.overlapBoxAll(any(), any())).thenReturn(List.of(ownSummon, enemySummon));

        cloud.start();

        assertThat(overchargeOf(ownSummon)).isInstanceOf(OverchargeStatusEffect.class);
        assertThat(overchargeOf(enemySummon)).isNull();
    }

    private GameObject lightningSummon(Master master) {
        GameObject gameObject = new GameObject(master, PrefabType.ElectricSlime, Vector3.ZERO, gameContext);
        gameObject.setElement(ElementType.LIGHTNING);
        gameObject.addComponent(new LightningSummonEffectReceiver(gameObject));
        gameObject.flushComponents();
        return gameObject;
    }

    private Object overchargeOf(GameObject gameObject) {
        return gameObject.getComponent(LightningSummonEffectReceiver.class)
                .getEffectByKey(StatusEffectKey.Overcharge_Receive);
    }
}
