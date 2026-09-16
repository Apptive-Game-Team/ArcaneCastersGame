package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BombSpriteBombTest {

    @Test
    void explodesAtGroundTargetAndDamagesEnemiesOnly() {
        GameContext context = mock(GameContext.class);
        when(context.getDeltaTime()).thenReturn(0.25f);

        GameObject bomb = object(
                Master.LeftPlayer,
                PrefabType.BombSpriteBomb,
                new Vector3(0f, 2f, 0f),
                context);
        bomb.setElement(ElementType.WIND);
        GameObject enemy = object(
                Master.RightPlayer,
                PrefabType.MiniRock,
                new Vector3(1f, 0f, 0f),
                context);
        DummyMob enemyMob = addMob(enemy);
        GameObject ally = object(
                Master.LeftPlayer,
                PrefabType.MiniRock,
                new Vector3(1f, 0f, 1f),
                context);
        DummyMob allyMob = addMob(ally);
        when(context.overlapSphereAll(bomb, 3f)).thenReturn(List.of(bomb, enemy, ally));

        BombSpriteBomb component = new BombSpriteBomb(bomb, 40, 10f, 3f);
        component.setTarget(Vector3.ZERO);
        clearInvocations(context);

        component.update();

        assertThat(enemyMob.getHp()).isEqualTo(60);
        assertThat(allyMob.getHp()).isEqualTo(100);
        assertThat(bomb.isDestroyed()).isTrue();

        ArgumentCaptor<GameObject> explosion = ArgumentCaptor.forClass(GameObject.class);
        verify(context).createGameObject(explosion.capture());
        assertThat(explosion.getValue().getType()).isEqualTo(PrefabType.BombSpriteExplosion);
    }

    private DummyMob addMob(GameObject object) {
        DummyMob mob = new DummyMob(object, 100);
        object.getComponents().add(mob);
        return mob;
    }

    private GameObject object(Master master, PrefabType type, Vector3 position, GameContext context) {
        GameObject object = new GameObject(master, type, position, context);
        object.setStatus(Status.Idle);
        return object;
    }
}
