package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.Element;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;

class SeaSerpentMobTest {

    @Test
    void hydroPumpDamagesEveryEnemyInsideTheLineIncludingAirTargets() {
        GameObject seaSerpent = mock(GameObject.class);
        GameObject groundTarget = enemyAt(new Vector3(4f, 0f, 0f));
        GameObject airTarget = enemyAt(new Vector3(4f, 2f, 0f));
        GameObject outsideBeam = enemyAt(new Vector3(4f, 0f, 2f));
        GameObject friendly = enemyAt(new Vector3(3f, 0f, 0f));
        GameContext gameContext = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        Element water = new Element();
        water.addNative(ElementType.WATER);

        when(seaSerpent.getPosition()).thenReturn(Vector3.ZERO);
        when(seaSerpent.getMaster()).thenReturn(Master.LeftPlayer);
        when(seaSerpent.getElement()).thenReturn(water);
        when(seaSerpent.getGameContext()).thenReturn(gameContext);
        CircleCollider seaSerpentCollider = mock(CircleCollider.class);
        when(seaSerpent.getFirstCircleCollider(false)).thenReturn(Optional.of(seaSerpentCollider));
        when(seaSerpentCollider.getRadius()).thenReturn(1.2f);
        when(friendly.getMaster()).thenReturn(Master.LeftPlayer);
        when(gameContext.getActiveGameObjects())
                .thenReturn(List.of(seaSerpent, groundTarget, airTarget, outsideBeam, friendly));
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);

        SeaSerpentMob.fireHydroPump(seaSerpent, groundTarget, 16, 7f, 1f);

        verify(groundTarget.getComponent(Damageable.class))
                .onDamaged(any(AttackInfo.class), org.mockito.ArgumentMatchers.eq(0.35f));
        verify(airTarget.getComponent(Damageable.class))
                .onDamaged(any(AttackInfo.class), org.mockito.ArgumentMatchers.eq(0.35f));
        verify(outsideBeam.getComponent(Damageable.class), never())
                .onDamaged(any(AttackInfo.class), anyFloat());
        verify(friendly.getComponent(Damageable.class), never())
                .onDamaged(any(AttackInfo.class), anyFloat());
        verify(dtoBuilder).createProjection(
                new Vector3(0f, 1.6f, 0f),
                new Vector3(8.2f, 0f, 0f),
                SeaSerpentMob.HYDRO_PUMP_PROJECTILE,
                SeaSerpentMob.HYDRO_PUMP_DURATION);
        verify(seaSerpent).setStatus(Status.Attack);
    }

    private GameObject enemyAt(Vector3 position) {
        GameObject target = mock(GameObject.class);
        Damageable damageable = mock(Damageable.class);
        CircleCollider collider = mock(CircleCollider.class);
        when(target.isActive()).thenReturn(true);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getPosition()).thenReturn(position);
        when(target.getComponent(Damageable.class)).thenReturn(damageable);
        when(target.getFirstCircleCollider()).thenReturn(Optional.of(collider));
        when(collider.getRadius()).thenReturn(0.25f);
        return target;
    }
}
