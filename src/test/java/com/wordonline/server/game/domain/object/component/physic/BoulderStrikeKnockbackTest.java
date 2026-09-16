package com.wordonline.server.game.domain.object.component.physic;

import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoulderStrikeKnockbackTest {

    @Test
    void damagesBothKnockedTargetAndCollidedEnemy() {
        GameContext context = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        when(context.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);

        GameObject source = object(Master.LeftPlayer, PrefabType.BoulderStrike, Vector3.ZERO, context);
        GameObject knockedTarget = movableMob(
                Master.RightPlayer,
                new Vector3(1f, 0f, 0f),
                context);
        DummyMob knockedMob = knockedTarget.getComponent(DummyMob.class);
        GameObject collidedEnemy = movableMob(
                Master.RightPlayer,
                new Vector3(2f, 0f, 0f),
                context);
        DummyMob collidedMob = collidedEnemy.getComponent(DummyMob.class);

        assertThat(BoulderStrikeKnockback.apply(
                knockedTarget,
                source,
                Vector3.RIGHT,
                8f,
                0.65f,
                30)).isTrue();

        BoulderStrikeKnockback knockback = knockedTarget.getComponentsToAdd().stream()
                .filter(BoulderStrikeKnockback.class::isInstance)
                .map(BoulderStrikeKnockback.class::cast)
                .findFirst()
                .orElseThrow();

        knockback.onCollision(collidedEnemy);

        assertThat(knockedMob.getHp()).isEqualTo(70);
        assertThat(collidedMob.getHp()).isEqualTo(70);
        assertThat(knockedTarget.getComponentsToRemove()).contains(knockback);
        verify(dtoBuilder).createProjection(
                any(Vector3.class),
                any(Vector3.class),
                org.mockito.ArgumentMatchers.eq(BoulderStrikeKnockback.IMPACT_PROJECTILE),
                org.mockito.ArgumentMatchers.eq(0.35f));
    }

    @Test
    void ignoresUnitsThatAreNotEnemiesOfTheCaster() {
        GameContext context = mock(GameContext.class);
        GameObject source = object(Master.LeftPlayer, PrefabType.BoulderStrike, Vector3.ZERO, context);
        GameObject knockedTarget = movableMob(
                Master.RightPlayer,
                new Vector3(1f, 0f, 0f),
                context);
        GameObject casterAlly = movableMob(
                Master.LeftPlayer,
                new Vector3(2f, 0f, 0f),
                context);

        BoulderStrikeKnockback.apply(
                knockedTarget,
                source,
                Vector3.RIGHT,
                8f,
                0.65f,
                30);
        BoulderStrikeKnockback knockback = knockedTarget.getComponentsToAdd().stream()
                .filter(BoulderStrikeKnockback.class::isInstance)
                .map(BoulderStrikeKnockback.class::cast)
                .findFirst()
                .orElseThrow();

        knockback.onCollision(casterAlly);

        assertThat(knockedTarget.getComponent(DummyMob.class).getHp()).isEqualTo(100);
        assertThat(casterAlly.getComponent(DummyMob.class).getHp()).isEqualTo(100);
        assertThat(knockedTarget.getComponentsToRemove()).doesNotContain(knockback);
    }

    private GameObject movableMob(Master master, Vector3 position, GameContext context) {
        GameObject object = object(master, PrefabType.MiniRock, position, context);
        object.getComponents().add(new DummyMob(object, 100));
        object.getComponents().add(new RigidBody(object, 1));
        return object;
    }

    private GameObject object(Master master, PrefabType type, Vector3 position, GameContext context) {
        GameObject object = new GameObject(master, type, position, context);
        object.setStatus(Status.Idle);
        return object;
    }
}
