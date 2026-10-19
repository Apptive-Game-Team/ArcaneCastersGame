package com.wordonline.server.game.domain.object.component.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.AttackInfo;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.simple.DummyMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.dto.frame.projectile.PositionProjectileTarget;
import com.wordonline.server.game.dto.frame.projectile.ReferenceProjectileTarget;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;

class SpiritBombChannelTest {

    @Test
    void damagesClosestEnemyOnBeamAndPreservesExactFourTickTotal() {
        GameContext context = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        when(context.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);
        when(context.getDeltaTime()).thenReturn(SpiritBombChannel.CHARGE_DURATION);

        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject nearEnemy = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(2f, 0f, 0f), context);
        DummyMob nearMob = addMob(nearEnemy);
        GameObject farEnemy = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(4f, 0f, 0f), context);
        DummyMob farMob = addMob(farEnemy);
        when(context.getActiveGameObjects()).thenReturn(List.of(player, farEnemy, nearEnemy));

        SpiritBombChannel channel = new SpiritBombChannel(
                player,
                new Vector3(6f, 0f, 0f),
                7,
                18f
        );

        channel.update();
        when(context.getDeltaTime()).thenReturn(SpiritBombChannel.TICK_INTERVAL);
        channel.update();
        channel.update();
        channel.update();

        assertThat(nearMob.getHp()).isEqualTo(93);
        assertThat(farMob.getHp()).isEqualTo(100);
        assertThat(player.getComponentsToRemove()).contains(channel);
        // 피해량 7 은 굵기 구간의 아래 끝이라 최소 굵기다.
        verify(dtoBuilder, times(4)).createProjection(
                new ReferenceProjectileTarget(player.getId()),
                new PositionProjectileTarget(2f, 0f, 0f),
                SpiritBombChannel.BEAM_PROJECTILE,
                1.05f,
                SpiritBombChannel.visualWidth(7)
        );
    }

    @Test
    void aSmallHitDrawsTheThinnestBeamAndAHugeOneTheThickest() {
        assertThat(SpiritBombChannel.visualWidth(0)).isEqualTo(0.25f);
        assertThat(SpiritBombChannel.visualWidth(100)).isEqualTo(0.25f);
        assertThat(SpiritBombChannel.visualWidth(1000)).isEqualTo(1f);
        assertThat(SpiritBombChannel.visualWidth(5000)).isEqualTo(1f);
    }

    @Test
    void betweenTheEndsTheBeamThickensWithTheDamage() {
        float half = SpiritBombChannel.visualWidth(550);

        assertThat(half).isEqualTo(0.625f);
        assertThat(SpiritBombChannel.visualWidth(300)).isLessThan(half);
        assertThat(SpiritBombChannel.visualWidth(800)).isGreaterThan(half);
    }

    @Test
    void beamReachesCastRangeEvenWhenClickedCloseToThePlayer() {
        GameContext context = channelContext();
        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject enemy = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(12f, 0f, 0f), context);
        DummyMob mob = addMob(enemy);
        when(context.getActiveGameObjects()).thenReturn(List.of(player, enemy));

        SpiritBombChannel channel = new SpiritBombChannel(player, new Vector3(1f, 0f, 0f), 40, 18f);
        channel.update();

        assertThat(mob.getHp()).isEqualTo(90);
    }

    @Test
    void enemyOutsideTheLaneIsNotHit() {
        GameContext context = channelContext();
        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject offLane = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(5f, 0f, 3f), context);
        DummyMob offLaneMob = addMob(offLane);
        GameObject beyondRange = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(25f, 0f, 0f), context);
        DummyMob beyondRangeMob = addMob(beyondRange);
        when(context.getActiveGameObjects()).thenReturn(List.of(player, offLane, beyondRange));

        SpiritBombChannel channel = new SpiritBombChannel(player, new Vector3(6f, 0f, 0f), 40, 18f);
        channel.update();

        assertThat(offLaneMob.getHp()).isEqualTo(100);
        assertThat(beyondRangeMob.getHp()).isEqualTo(100);
    }

    @Test
    void overkillCarriesOverToTheNextEnemyInTheSameTick() {
        GameContext context = channelContext();
        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject first = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(3f, 0f, 0f), context);
        DummyMob firstMob = addMob(first);
        firstMob.applyDamage(new AttackInfo(70, ElementType.NONE));
        GameObject second = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(6f, 0f, 0f), context);
        DummyMob secondMob = addMob(second);
        when(context.getActiveGameObjects()).thenReturn(List.of(player, second, first));

        // 첫 tick 피해량 40: 첫 적은 30 만 남아 있어 10 이 넘치고, 그 10 이 다음 적에게 간다.
        SpiritBombChannel channel = new SpiritBombChannel(player, new Vector3(6f, 0f, 0f), 160, 18f);
        channel.update();

        assertThat(firstMob.getHp()).isLessThanOrEqualTo(0);
        assertThat(secondMob.getHp()).isEqualTo(90);
        verifyBeam(context, player, 3f, SpiritBombChannel.visualWidth(160));
        verifyBeam(context, player, 6f, SpiritBombChannel.visualWidth(160));
    }

    @Test
    void beamStaysOnTheAimLineAndStopsBesideAnOffCenterEnemy() {
        GameContext context = channelContext();
        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject enemy = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(5f, 0f, 1f), context);
        DummyMob mob = addMob(enemy);
        when(context.getActiveGameObjects()).thenReturn(List.of(player, enemy));

        SpiritBombChannel channel = new SpiritBombChannel(player, new Vector3(6f, 0f, 0f), 40, 18f);
        channel.update();

        // 적의 중심 (5, 0, 1) 로 꺾지 않고 조준선 위의 (5, 0, 0) 에서 끝난다.
        assertThat(mob.getHp()).isEqualTo(90);
        verifyBeam(context, player, 5f, SpiritBombChannel.visualWidth(40));
    }

    @Test
    void beamReachesCastRangeWhenNothingIsOnTheLine() {
        GameContext context = channelContext();
        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        when(context.getActiveGameObjects()).thenReturn(List.of(player));

        SpiritBombChannel channel = new SpiritBombChannel(player, new Vector3(6f, 0f, 0f), 40, 18f);
        channel.update();

        verifyBeam(context, player, 18f, SpiritBombChannel.visualWidth(40));
    }

    private void verifyBeam(GameContext context, GameObject player, float endX, float width) {
        verify(context.getObjectsInfoDtoBuilder()).createProjection(
                new ReferenceProjectileTarget(player.getId()),
                new PositionProjectileTarget(endX, 0f, 0f),
                SpiritBombChannel.BEAM_PROJECTILE,
                1.05f,
                width
        );
    }

    private GameContext channelContext() {
        GameContext context = mock(GameContext.class);
        ObjectsInfoDtoBuilder dtoBuilder = mock(ObjectsInfoDtoBuilder.class);
        when(context.getObjectsInfoDtoBuilder()).thenReturn(dtoBuilder);
        when(context.getDeltaTime()).thenReturn(SpiritBombChannel.CHARGE_DURATION);
        return context;
    }

    private DummyMob addMob(GameObject object) {
        DummyMob mob = new DummyMob(object, 100);
        object.getComponents().add(mob);
        object.addCollider(new CircleCollider(object, 0.5f, false));
        return mob;
    }

    private GameObject object(Master master, PrefabType type, Vector3 position, GameContext context) {
        GameObject object = new GameObject(master, type, position, context);
        object.setStatus(Status.Idle);
        return object;
    }
}
