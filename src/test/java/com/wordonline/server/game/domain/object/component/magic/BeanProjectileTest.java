package com.wordonline.server.game.domain.object.component.magic;

import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BeanProjectileTest {

    private static final float AERIAL_HEIGHT = 5f;

    private GameContext context;
    private GameObject bean;
    private BeanProjectile projectile;

    @BeforeEach
    void setUp() {
        context = mock(GameContext.class);
        when(context.getDeltaTime()).thenReturn(0.5f);
        bean = object(Master.LeftPlayer, PrefabType.Bean, Vector3.ZERO);
        bean.setElement(ElementType.NATURE);
        projectile = new BeanProjectile(bean, 4f);
        bean.getComponents().add(projectile);
    }

    @Test
    void damagesGroundEnemyOnceAndDestroysItself() {
        GameObject enemy = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(3f, 0f, 0f));
        DummyMob enemyMob = addMob(enemy);
        projectile.launch(enemy.getPosition(), 30, 9f);

        projectile.onCollisionWithEnemy(enemy);
        projectile.onCollisionWithEnemy(enemy);

        assertThat(enemyMob.getHp()).isEqualTo(70);
        assertThat(bean.isDestroyed()).isTrue();
    }

    @Test
    void passesThroughAirEnemyAndAlly() {
        GameObject airEnemy = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(1f, AERIAL_HEIGHT, 0f));
        DummyMob airMob = addMob(airEnemy);
        GameObject ally = object(Master.LeftPlayer, PrefabType.MiniRock, new Vector3(1f, 0f, 1f));
        DummyMob allyMob = addMob(ally);
        projectile.launch(new Vector3(3f, 0f, 0f), 30, 9f);

        projectile.onCollisionWithEnemy(airEnemy);
        projectile.onCollisionWithEnemy(ally);

        assertThat(airMob.getHp()).isEqualTo(100);
        assertThat(allyMob.getHp()).isEqualTo(100);
        assertThat(bean.isDestroyed()).isFalse();
    }

    @Test
    void fliesTowardTargetAtItsSpeed() {
        projectile.launch(new Vector3(10f, 0f, 0f), 30, 9f);

        projectile.update();

        assertThat(bean.getPosition().getX()).isEqualTo(2f);
    }

    @Test
    void destroysItselfAfterMaxDistance() {
        projectile.launch(new Vector3(10f, 0f, 0f), 30, 3f);

        projectile.update();
        assertThat(bean.isDestroyed()).isFalse();
        projectile.update();

        assertThat(bean.isDestroyed()).isTrue();
    }

    private DummyMob addMob(GameObject object) {
        DummyMob mob = new DummyMob(object, 100);
        object.getComponents().add(mob);
        return mob;
    }

    private GameObject object(Master master, PrefabType type, Vector3 position) {
        GameObject object = new GameObject(master, type, position, context);
        object.setStatus(Status.Idle);
        return object;
    }
}
