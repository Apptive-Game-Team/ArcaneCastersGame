package com.wordonline.server.game.domain.object.component.magic;

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

class TidalWarheadProjectileTest {

    @Test
    void groundLockHalvesAreaDamageAndDoesNotHitAllies() {
        GameContext context = mock(GameContext.class);
        GameObject missile = object(Master.LeftPlayer, PrefabType.GroundTidalWarhead, Vector3.ZERO, context);
        GameObject enemy = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(1f, 0f, 0f), context);
        DummyMob enemyMob = addMob(enemy);
        GameObject ally = object(Master.LeftPlayer, PrefabType.MiniRock, new Vector3(1f, 0f, 1f), context);
        DummyMob allyMob = addMob(ally);
        when(context.overlapSphereAll(missile, 3f)).thenReturn(List.of(missile, enemy, ally));

        TidalWarheadProjectile projectile = new TidalWarheadProjectile(missile, 100, 8f, 3f);
        projectile.setTarget(enemy, false);
        clearInvocations(context);

        projectile.onCollisionWithEnemy(enemy);

        assertThat(enemyMob.getHp()).isEqualTo(50);
        assertThat(allyMob.getHp()).isEqualTo(100);
        assertThat(missile.isDestroyed()).isTrue();

        ArgumentCaptor<GameObject> explosion = ArgumentCaptor.forClass(GameObject.class);
        verify(context).createGameObject(explosion.capture());
        assertThat(explosion.getValue().getType()).isEqualTo(PrefabType.TidalWarheadExplosion);
    }

    @Test
    void aerialLockDealsFullAreaDamage() {
        GameContext context = mock(GameContext.class);
        GameObject missile = object(Master.LeftPlayer, PrefabType.TidalWarhead, Vector3.ZERO, context);
        GameObject enemy = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(1f, 3f, 0f), context);
        DummyMob enemyMob = addMob(enemy);
        when(context.overlapSphereAll(missile, 3f)).thenReturn(List.of(missile, enemy));

        TidalWarheadProjectile projectile = new TidalWarheadProjectile(missile, 100, 8f, 3f);
        projectile.setTarget(enemy, true);

        projectile.onCollisionWithEnemy(enemy);

        assertThat(enemyMob.getHp()).isZero();
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
