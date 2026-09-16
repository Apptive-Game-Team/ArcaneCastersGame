package com.wordonline.server.game.domain.object.component.effect.statuseffect;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OverchargeStatusEffectTest {

    @Test
    void increasesMovementSpeedByFiftyPercentUntilExpiration() {
        GameObject owner = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        Mob mob = mock(Mob.class);
        Stat speed = new Stat(10f);
        when(owner.getGameContext()).thenReturn(gameContext);
        when(owner.getComponent(Mob.class)).thenReturn(mob);
        when(mob.getSpeed()).thenReturn(speed);
        when(gameContext.getDeltaTime()).thenReturn(1.1f);

        OverchargeStatusEffect effect = new OverchargeStatusEffect(owner, 1f);
        effect.start();

        assertThat(speed.total()).isEqualTo(15f);

        effect.update();

        assertThat(speed.total()).isEqualTo(10f);
        verify(owner).addEffect(Effect.Overcharge);
        verify(owner).removeEffect(Effect.Overcharge);
        verify(owner).removeComponent(effect);
    }

    @Test
    void multipliesAlreadyModifiedMovementSpeedByExactlyOnePointFive() {
        GameObject owner = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        Mob mob = mock(Mob.class);
        Stat speed = new Stat(10f);
        speed.addPercent(-0.2f);
        when(owner.getGameContext()).thenReturn(gameContext);
        when(owner.getComponent(Mob.class)).thenReturn(mob);
        when(mob.getSpeed()).thenReturn(speed);
        when(gameContext.getDeltaTime()).thenReturn(1.1f);

        OverchargeStatusEffect effect = new OverchargeStatusEffect(owner, 1f);
        effect.start();

        assertThat(speed.total()).isEqualTo(12f);

        effect.update();

        assertThat(speed.total()).isEqualTo(8f);
    }

    @Test
    void emitsElectricShotProjectileEverySecondAndAppliesConfiguredDamage() {
        GameObject owner = mock(GameObject.class);
        GameObject target = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters electricShotParameters = mock(GameObjectParameters.class);
        Mob mob = mock(Mob.class);
        Damageable damageable = mock(Damageable.class);
        when(owner.getGameContext()).thenReturn(gameContext);
        when(owner.getComponent(Mob.class)).thenReturn(mob);
        when(mob.getSpeed()).thenReturn(new Stat(10f));
        when(owner.getMaster()).thenReturn(Master.LeftPlayer);
        when(owner.getPosition()).thenReturn(Vector3.ZERO);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getPosition()).thenReturn(new Vector3(2f, 0f, 0f));
        when(target.getComponents(Damageable.class)).thenReturn(List.of(damageable));
        when(gameContext.getActiveGameObjects()).thenReturn(List.of(owner, target));
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);
        when(gameContext.getParameters()).thenReturn(parameters);
        when(parameters.object(GameObjectKey.ELECTRIC_SHOT)).thenReturn(electricShotParameters);
        when(electricShotParameters.intValue(ParameterKey.DAMAGE)).thenReturn(4);
        when(gameContext.getDeltaTime()).thenReturn(0.5f);

        OverchargeStatusEffect effect = new OverchargeStatusEffect(owner, 2f);
        effect.start();
        effect.update();

        verifyNoInteractions(dtoBuilder);

        effect.update();

        verify(dtoBuilder).createProjection(owner, target, "ElectricShot", 0.2f);
        verify(damageable).onDamaged(new AttackInfo(4, ElementType.LIGHTNING), 0.2f);
    }

    @Test
    void doesNotEmitElectricShotBeyondRangeTwo() {
        GameObject owner = mock(GameObject.class);
        GameObject target = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        Mob mob = mock(Mob.class);
        Damageable damageable = mock(Damageable.class);
        when(owner.getGameContext()).thenReturn(gameContext);
        when(owner.getComponent(Mob.class)).thenReturn(mob);
        when(mob.getSpeed()).thenReturn(new Stat(10f));
        when(owner.getMaster()).thenReturn(Master.LeftPlayer);
        when(owner.getPosition()).thenReturn(Vector3.ZERO);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getPosition()).thenReturn(new Vector3(2.01f, 0f, 0f));
        when(target.getComponents(Damageable.class)).thenReturn(List.of(damageable));
        when(gameContext.getActiveGameObjects()).thenReturn(List.of(owner, target));
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);
        when(gameContext.getDeltaTime()).thenReturn(1f);

        OverchargeStatusEffect effect = new OverchargeStatusEffect(owner, 2f);
        effect.start();
        effect.update();

        verifyNoInteractions(dtoBuilder);
        verifyNoInteractions(damageable);
    }

    @Test
    void reapplicationExtendsPendingEffectWithoutAddingAnotherComponent() {
        GameObject owner = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        Mob mob = mock(Mob.class);
        Stat speed = new Stat(10f);
        when(owner.getGameContext()).thenReturn(gameContext);
        when(owner.getComponent(Mob.class)).thenReturn(mob);
        when(mob.getSpeed()).thenReturn(speed);

        OverchargeStatusEffect existing = new OverchargeStatusEffect(owner, 1f);
        when(owner.getComponents(OverchargeStatusEffect.class)).thenReturn(List.of());
        when(owner.getComponentsToAdd()).thenReturn(List.of(existing));
        existing.start();

        OverchargeStatusEffect.apply(owner, 1f);
        when(gameContext.getDeltaTime()).thenReturn(1.5f);
        existing.update();

        assertThat(speed.total()).isEqualTo(15f);
        verify(owner, never()).addComponent(any());
        verify(owner, never()).removeComponent(existing);

        when(gameContext.getDeltaTime()).thenReturn(0.6f);
        existing.update();

        assertThat(speed.total()).isEqualTo(10f);
        verify(owner).removeComponent(existing);
    }

    @Test
    void reapplicationCancelsPendingRemovalAndReactivatesSameComponent() {
        GameObject owner = mock(GameObject.class);
        GameContext gameContext = mock(GameContext.class);
        Mob mob = mock(Mob.class);
        Stat speed = new Stat(10f);
        List<Component> pendingRemoval = new ArrayList<>();
        when(owner.getGameContext()).thenReturn(gameContext);
        when(owner.getComponent(Mob.class)).thenReturn(mob);
        when(mob.getSpeed()).thenReturn(speed);

        OverchargeStatusEffect existing = new OverchargeStatusEffect(owner, 1f);
        when(owner.getComponents(OverchargeStatusEffect.class)).thenReturn(List.of(existing));
        when(owner.getComponentsToRemove()).thenReturn(pendingRemoval);
        pendingRemoval.add(existing);
        existing.start();
        existing.onDestroy();

        OverchargeStatusEffect.apply(owner, 1f);

        assertThat(pendingRemoval).isEmpty();
        assertThat(speed.total()).isEqualTo(15f);
        verify(owner, never()).addComponent(any());
    }
}
