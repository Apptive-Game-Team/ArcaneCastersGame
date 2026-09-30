package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.Element;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaterSlimeRangeAttackMobTest {

    @Test
    void attackStateFiresOneWaterShotAndAppliesSlimeDamageAtItsOwnCadence() {
        GameObject waterSlime = mock(GameObject.class);
        GameObject target = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        Damageable damageable = mock(Damageable.class);
        Element water = new Element();
        water.addNative(ElementType.WATER);

        when(waterSlime.getGameContext()).thenReturn(gameContext);
        when(waterSlime.getMaster()).thenReturn(Master.LeftPlayer);
        when(waterSlime.getPosition()).thenReturn(Vector3.ZERO);
        when(waterSlime.getElement()).thenReturn(water);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getPosition()).thenReturn(new Vector3(3f, 0f, 0f));
        when(target.getComponentOptional(Damageable.class)).thenReturn(Optional.of(damageable));
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);
        when(gameContext.getDeltaTime()).thenReturn(0.9f, 0.2f);

        WaterSlimeRangeAttackMob mob = new WaterSlimeRangeAttackMob(
                waterSlime, 10, 1f, 1, 4, 1f, 6f);
        mob.target = target;
        mob.setState(mob.new AttackState());

        mob.update();

        verify(dtoBuilder, never()).createProjection(waterSlime, target, "WaterShot", 0.5f);
        verify(damageable, never()).onDamaged(any(AttackInfo.class));

        mob.update();

        verify(dtoBuilder).createProjection(waterSlime, target, "WaterShot", 0.5f);
        verify(damageable).onDamaged(new AttackInfo(4, ElementType.WATER));
        verify(waterSlime).setStatus(Status.Attack);
    }

    @Test
    void separateWaterSlimesFireIndependentlyWithoutGroupMultiplier() {
        GameObject first = mock(GameObject.class);
        GameObject second = mock(GameObject.class);
        GameObject target = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        Damageable damageable = mock(Damageable.class);
        Element water = new Element();
        water.addNative(ElementType.WATER);

        configureAttacker(first, gameContext, water);
        configureAttacker(second, gameContext, water);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getPosition()).thenReturn(new Vector3(3f, 0f, 0f));
        when(target.getComponentOptional(Damageable.class)).thenReturn(Optional.of(damageable));
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);
        when(gameContext.getDeltaTime()).thenReturn(1.1f);

        WaterSlimeRangeAttackMob firstMob = new WaterSlimeRangeAttackMob(first, 10, 1f, 1, 4, 1f, 6f);
        WaterSlimeRangeAttackMob secondMob = new WaterSlimeRangeAttackMob(second, 10, 1f, 1, 4, 1f, 6f);
        firstMob.target = target;
        secondMob.target = target;
        firstMob.setState(firstMob.new AttackState());
        secondMob.setState(secondMob.new AttackState());

        firstMob.update();

        verify(dtoBuilder).createProjection(first, target, "WaterShot", 0.5f);
        verify(dtoBuilder, never()).createProjection(second, target, "WaterShot", 0.5f);

        secondMob.update();

        verify(dtoBuilder).createProjection(second, target, "WaterShot", 0.5f);
        verify(damageable, times(2)).onDamaged(new AttackInfo(4, ElementType.WATER));
    }

    @Test
    void lethalDamageCreatesExactlyOneOwnedWaterFieldAtDeathPosition() {
        GameContext gameContext = mock(GameContext.class);
        Vector3 deathPosition = new Vector3(2f, 0f, 3f);
        GameObject waterSlime = new GameObject(
                Master.LeftPlayer, PrefabType.WaterSlime, deathPosition, gameContext);
        waterSlime.setElement(ElementType.WATER);
        clearInvocations(gameContext);
        WaterSlimeRangeAttackMob mob = new WaterSlimeRangeAttackMob(
                waterSlime, 5, 1f, 1, 4, 1f, 6f);

        mob.onDamaged(new AttackInfo(5, ElementType.WATER));
        mob.onDamaged(new AttackInfo(5, ElementType.WATER));

        ArgumentCaptor<GameObject> created = ArgumentCaptor.forClass(GameObject.class);
        verify(gameContext, times(1)).createGameObject(created.capture());
        assertThat(created.getValue().getType()).isEqualTo(PrefabType.WaterField);
        assertThat(created.getValue().getMaster()).isEqualTo(Master.LeftPlayer);
        assertThat(created.getValue().getPosition()).isEqualTo(deathPosition);
        assertThat(waterSlime.isDestroyed()).isTrue();
    }

    @Test
    void directDestroyDoesNotCreateDeathField() {
        GameContext gameContext = mock(GameContext.class);
        GameObject waterSlime = new GameObject(
                Master.LeftPlayer, PrefabType.WaterSlime, Vector3.ZERO, gameContext);
        WaterSlimeRangeAttackMob mob = new WaterSlimeRangeAttackMob(
                waterSlime, 5, 1f, 1, 4, 1f, 6f);
        waterSlime.getComponents().add(mob);
        clearInvocations(gameContext);

        waterSlime.destroy();

        verify(gameContext, never()).createGameObject(any(GameObject.class));
        assertThat(waterSlime.isDestroyed()).isTrue();
    }

    private void configureAttacker(GameObject attacker, GameContext gameContext, Element element) {
        when(attacker.getGameContext()).thenReturn(gameContext);
        when(attacker.getMaster()).thenReturn(Master.LeftPlayer);
        when(attacker.getPosition()).thenReturn(Vector3.ZERO);
        when(attacker.getElement()).thenReturn(element);
    }
}
