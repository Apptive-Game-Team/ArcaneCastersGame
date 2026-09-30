package com.wordonline.server.game.domain.object.component.effect;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.Element;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.effect.statuseffect.OverchargeStatusEffect;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import com.wordonline.server.game.dto.frame.projectile.PositionProjectileTarget;
import com.wordonline.server.game.dto.frame.projectile.ProjectileTarget;
import com.wordonline.server.game.dto.frame.projectile.ReferenceProjectileTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ElectricDeathEnergyTest {

    @Test
    void leavesTheElectricFieldToDeathFieldWithoutAnAbsorber() {
        Fixture fixture = fixture(new Vector3(3f, 2f, 4f), Master.LeftPlayer, List.of());

        fixture.component.onCombatDeath();

        verify(fixture.gameContext, never()).createGameObject(any(GameObject.class));
        verifyNoInteractions(fixture.dtoBuilder);
    }

    @Test
    void selectsNearestEligibleAllyAndEmitsPositionToReferenceAbsorption() {
        Vector3 deathPosition = new Vector3(3f, 0f, 4f);
        GameObject source = candidate(99, deathPosition, Master.LeftPlayer, true, true, true, true);
        GameObject enemy = candidate(1, new Vector3(3.2f, 0f, 4f), Master.RightPlayer, true, true, true, true);
        GameObject nonLightning = candidate(2, new Vector3(3.3f, 0f, 4f), Master.LeftPlayer, false, true, true, true);
        GameObject dead = candidate(3, new Vector3(3.4f, 0f, 4f), Master.LeftPlayer, true, true, false, true);
        GameObject building = candidate(4, new Vector3(3.5f, 0f, 4f), Master.LeftPlayer, true, true, true, false);
        GameObject outOfRange = candidate(5, new Vector3(9f, 0f, 4f), Master.LeftPlayer, true, true, true, true);
        GameObject farther = candidate(6, new Vector3(6f, 0f, 4f), Master.LeftPlayer, true, true, true, true);
        GameObject nearest = candidate(7, new Vector3(4f, 0f, 4f), Master.LeftPlayer, true, true, true, true);
        Fixture fixture = fixture(source, deathPosition, Master.LeftPlayer,
                List.of(source, enemy, nonLightning, dead, building, outOfRange, farther, nearest));

        fixture.component.onCombatDeath();

        verify(nearest).addComponent(any(OverchargeStatusEffect.class));
        verify(farther, never()).addComponent(any(OverchargeStatusEffect.class));
        ArgumentCaptor<ProjectileTarget> start = ArgumentCaptor.forClass(ProjectileTarget.class);
        ArgumentCaptor<ProjectileTarget> end = ArgumentCaptor.forClass(ProjectileTarget.class);
        verify(fixture.dtoBuilder).createProjection(start.capture(), end.capture(), eq("ElectricAbsorb"), eq(0.35f));
        assertThat(start.getValue()).isInstanceOf(PositionProjectileTarget.class);
        PositionProjectileTarget positionTarget = (PositionProjectileTarget) start.getValue();
        assertThat(new Vector3(positionTarget.x, positionTarget.y, positionTarget.z)).isEqualTo(deathPosition);
        assertThat(end.getValue()).isInstanceOf(ReferenceProjectileTarget.class);
        assertThat(((ReferenceProjectileTarget) end.getValue()).id).isEqualTo(7);
    }

    @Test
    void handlesRepeatedCombatDeathOnlyOnce() {
        Vector3 deathPosition = new Vector3(3f, 0f, 4f);
        GameObject absorber = candidate(7, new Vector3(4f, 0f, 4f),
                Master.LeftPlayer, true, true, true, true);
        Fixture fixture = fixture(deathPosition, Master.LeftPlayer, List.of(absorber));

        fixture.component.onCombatDeath();
        fixture.component.onCombatDeath();

        verify(absorber).addComponent(any(OverchargeStatusEffect.class));
    }

    @Test
    void extendsPendingOverchargeByConfiguredElectricFieldDuration() {
        Vector3 deathPosition = new Vector3(3f, 0f, 4f);
        GameObject absorber = candidate(7, new Vector3(4f, 0f, 4f),
                Master.LeftPlayer, true, true, true, true);
        OverchargeStatusEffect pendingOvercharge = mock(OverchargeStatusEffect.class);
        when(absorber.getComponentsToAdd()).thenReturn(List.of(pendingOvercharge));
        Fixture fixture = fixture(deathPosition, Master.LeftPlayer, List.of(absorber));

        fixture.component.onCombatDeath();

        verify(pendingOvercharge).extend(6f);
        verify(absorber, never()).addComponent(any(OverchargeStatusEffect.class));
    }

    private GameObject candidate(
            int id,
            Vector3 position,
            Master master,
            boolean lightning,
            boolean damageable,
            boolean active,
            boolean eligibleCreature) {
        GameObject target = mock(GameObject.class);
        Element element = new Element();
        if (lightning) {
            element.addNative(ElementType.LIGHTNING);
        }
        when(target.getId()).thenReturn(id);
        when(target.getPosition()).thenReturn(position);
        when(target.getMaster()).thenReturn(master);
        when(target.getElement()).thenReturn(element);
        when(target.isActive()).thenReturn(active);
        when(target.getComponent(Mob.class)).thenReturn(damageable ? mock(Mob.class) : null);
        when(target.getComponents(Damageable.class)).thenReturn(
                damageable ? List.of(mock(Damageable.class)) : List.of());
        when(target.getComponents(ElectricDeathEnergy.class)).thenReturn(
                eligibleCreature ? List.of(mock(ElectricDeathEnergy.class)) : List.of());
        when(target.getComponents(OverchargeStatusEffect.class)).thenReturn(List.of());
        when(target.getComponentsToAdd()).thenReturn(List.of());
        return target;
    }

    private Fixture fixture(Vector3 position, Master master, List<GameObject> activeObjects) {
        return fixture(mock(GameObject.class), position, master, activeObjects);
    }

    private Fixture fixture(GameObject source, Vector3 position, Master master, List<GameObject> activeObjects) {
        GameContext gameContext = mock(GameContext.class);
        Parameters parameters = mock(Parameters.class);
        GameObjectParameters electricField = mock(GameObjectParameters.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);

        when(source.getPosition()).thenReturn(position);
        when(source.getMaster()).thenReturn(master);
        when(source.getGameContext()).thenReturn(gameContext);
        when(gameContext.getParameters()).thenReturn(parameters);
        when(parameters.object(GameObjectKey.ELECTRIC_FIELD)).thenReturn(electricField);
        when(electricField.floatValue(ParameterKey.RADIUS)).thenReturn(5f);
        when(electricField.floatValue(ParameterKey.DURATION)).thenReturn(6f);
        when(gameContext.getActiveGameObjects()).thenReturn(activeObjects);
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);

        return new Fixture(gameContext, dtoBuilder, new ElectricDeathEnergy(source));
    }

    private record Fixture(
            GameContext gameContext,
            ObjectsInfoDtoBuilder dtoBuilder,
            ElectricDeathEnergy component) {
    }
}
