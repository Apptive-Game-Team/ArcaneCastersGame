package com.wordonline.server.game.domain.magic.implement.shoot;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TidalWarheadMagicTest {

    @Test
    void prefersAerialTargetEvenWhenGroundTargetIsCloserToAim() {
        GameContext context = mock(GameContext.class);
        GameObject source = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject groundEnemy = damageable(
                Master.RightPlayer,
                new Vector3(1f, 0f, 0f),
                context);
        GameObject aerialEnemy = damageable(
                Master.RightPlayer,
                new Vector3(8f, 3f, 0f),
                context);
        when(context.getActiveGameObjects()).thenReturn(List.of(source, groundEnemy, aerialEnemy));

        GameObject target = new TidalWarheadMagic().selectTarget(
                context,
                source,
                groundEnemy.getPosition());

        assertThat(target).isSameAs(aerialEnemy);
    }

    @Test
    void fallsBackToGroundTargetWhenNoAerialEnemyExists() {
        GameContext context = mock(GameContext.class);
        GameObject source = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject nearGroundEnemy = damageable(
                Master.RightPlayer,
                new Vector3(2f, 0f, 0f),
                context);
        GameObject farGroundEnemy = damageable(
                Master.RightPlayer,
                new Vector3(6f, 0f, 0f),
                context);
        when(context.getActiveGameObjects()).thenReturn(List.of(source, farGroundEnemy, nearGroundEnemy));

        GameObject target = new TidalWarheadMagic().selectTarget(
                context,
                source,
                Vector3.ZERO);

        assertThat(target).isSameAs(nearGroundEnemy);
    }

    private GameObject damageable(Master master, Vector3 position, GameContext context) {
        GameObject object = object(master, PrefabType.MiniRock, position, context);
        object.getComponents().add(new DummyMob(object, 100));
        return object;
    }

    private GameObject object(Master master, PrefabType type, Vector3 position, GameContext context) {
        GameObject object = new GameObject(master, type, position, context);
        object.setStatus(Status.Idle);
        return object;
    }
}
