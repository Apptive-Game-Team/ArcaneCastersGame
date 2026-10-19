package com.wordonline.server.game.domain.object.component.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
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
import com.wordonline.server.game.util.SimplePhysics;

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
        world(context, player, farEnemy, nearEnemy);

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
        world(context, player, enemy);

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
        world(context, player, offLane, beyondRange);

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
        world(context, player, second, first);

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
        world(context, player, enemy);

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
        world(context, player);

        SpiritBombChannel channel = new SpiritBombChannel(player, new Vector3(6f, 0f, 0f), 40, 18f);
        channel.update();

        verifyBeam(context, player, 18f, SpiritBombChannel.visualWidth(40));
    }

    @Test
    void enemyJustBehindTheCasterIsNotHit() {
        GameContext context = channelContext();
        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        GameObject behind = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(-0.5f, 0f, 0f), context);
        DummyMob behindMob = addMob(behind);
        GameObject ahead = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(8f, 0f, 0f), context);
        DummyMob aheadMob = addMob(ahead);
        world(context, player, behind, ahead);

        SpiritBombChannel channel = new SpiritBombChannel(player, new Vector3(6f, 0f, 0f), 40, 18f);
        channel.update();

        assertThat(behindMob.getHp()).isEqualTo(100);
        assertThat(aheadMob.getHp()).isEqualTo(90);
    }

    @Test
    void theEnemyFurtherAlongTheAimLineIsHitLaterEvenIfItIsCloserInStraightDistance() {
        GameContext context = channelContext();
        GameObject player = object(Master.LeftPlayer, PrefabType.Player, Vector3.ZERO, context);
        // 비켜 선 적은 직선 거리 약 5.14, 조준선 거리 5. 선 위의 적은 직선 거리와 조준선 거리 모두 5.1.
        GameObject offCenter = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(5f, 0f, 1.2f), context);
        DummyMob offCenterMob = addMob(offCenter);
        GameObject onLine = object(Master.RightPlayer, PrefabType.MiniRock, new Vector3(5.1f, 0f, 0f), context);
        DummyMob onLineMob = addMob(onLine);
        world(context, player, onLine, offCenter);

        SpiritBombChannel channel = new SpiritBombChannel(player, new Vector3(6f, 0f, 0f), 40, 18f);
        channel.update();

        assertThat(offCenterMob.getHp()).isEqualTo(90);
        assertThat(onLineMob.getHp()).isEqualTo(100);
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

    private void world(GameContext context, GameObject... objects) {
        List<GameObject> gameObjects = List.of(objects);
        SimplePhysics physics = new SimplePhysics(gameObjects);
        when(context.getActiveGameObjects()).thenReturn(gameObjects);
        when(context.sphereCastAll(any(Vector3.class), anyFloat(), any(Vector3.class), anyFloat()))
                .thenAnswer(invocation -> physics.sphereCastAll(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)));
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
