package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.Stat;
import com.wordonline.server.game.domain.magic.ElementType;
import com.wordonline.server.game.domain.object.Element;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Component;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.MovementSpeedTracker;
import com.wordonline.server.game.domain.object.component.mob.detector.PriorityEnemyDetector;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetCategory;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.pathfinder.FlowFieldNavigation;
import com.wordonline.server.game.domain.object.component.mob.simple.PlayerHealthComponent;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.StaticObstacle;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StormStagMobTest {
    private GameContext gameContext;
    private GameSessionData sessionData;
    private GameObject stormStag;
    private MovementSpeedTracker speedTracker;
    private StormStagMob mob;
    private RigidBody rigidBody;
    private Vector3 position;
    private int frame;

    @BeforeEach
    void setUp() {
        gameContext = mock(GameContext.class);
        sessionData = new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
        stormStag = mock(GameObject.class);
        speedTracker = mock(MovementSpeedTracker.class);
        position = new Vector3();
        frame = 0;
        rigidBody = new RigidBody(stormStag, 1);
        Element element = new Element();
        element.addNative(ElementType.LIGHTNING);

        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getDeltaTime()).thenReturn(0.05f);
        when(stormStag.getGameContext()).thenReturn(gameContext);
        when(stormStag.getMaster()).thenReturn(Master.LeftPlayer);
        when(stormStag.getPosition()).thenAnswer(invocation -> position);
        doAnswer(invocation -> {
            position = invocation.getArgument(0);
            return null;
        }).when(stormStag).setPosition(any(Vector3.class));
        when(stormStag.getStatus()).thenReturn(Status.Idle);
        when(stormStag.getElement()).thenReturn(element);
        when(stormStag.getComponent(RigidBody.class)).thenReturn(rigidBody);
        when(gameContext.getNavigation()).thenReturn(
                new FlowFieldNavigation(() -> frame, () -> sessionData.gameObjects));
        when(stormStag.getComponent(MovementSpeedTracker.class)).thenReturn(speedTracker);
        when(stormStag.getComponentsToAdd()).thenReturn(new ArrayList<Component>());
        when(stormStag.getFirstCircleCollider(false)).thenReturn(Optional.empty());

        mob = new StormStagMob(
                stormStag, 100, 4f, TargetMask.GROUND.bit, 10, 2f, 2f, 1f);
        mob.start();
    }

    @Test
    void prioritizesUnitsBeforeBuildingsAndPlayersForCharge() {
        GameObject player = target(TargetCategory.PLAYER, new Vector3(0.5f, 0f, 0f));
        GameObject building = target(TargetCategory.BUILDING, new Vector3(1f, 0f, 0f));
        GameObject unit = target(TargetCategory.UNIT, new Vector3(5f, 0f, 0f));
        sessionData.gameObjects.addAll(List.of(player, building, unit));

        PriorityEnemyDetector detector = (PriorityEnemyDetector) ReflectionTestUtils.getField(
                mob, "objectiveDetector");

        assertThat(detector.detect(stormStag)).isSameAs(unit);
    }

    @Test
    void treatsEnemyPlayerAsPanicThreatButNotBuilding() {
        GameObject building = target(TargetCategory.BUILDING, new Vector3(0.25f, 0f, 0f));
        GameObject player = target(TargetCategory.PLAYER, new Vector3(1f, 0f, 0f));
        sessionData.gameObjects.addAll(List.of(building, player));

        PriorityEnemyDetector detector = (PriorityEnemyDetector) ReflectionTestUtils.getField(
                mob, "threatDetector");

        assertThat(detector.detect(stormStag)).isSameAs(player);
    }

    @Test
    void entersPanicImmediatelyAfterChargeImpact() {
        GameObject target = target(TargetCategory.UNIT, Vector3.ZERO);
        Damageable damageable = mock(Damageable.class);
        when(target.getComponent(Damageable.class)).thenReturn(damageable);
        ReflectionTestUtils.setField(mob, "target", target);
        ReflectionTestUtils.setField(mob, "panicCooldownRemaining", 10f);
        when(speedTracker.getTier()).thenReturn(1);
        mob.setState(mob.new ChargeState());

        mob.update();

        assertThat(ReflectionTestUtils.getField(mob, "currentState"))
                .isInstanceOf(StormStagMob.PanicState.class);
        verify(damageable).onDamaged(any());
    }

    @Test
    void damagesAnEnemyWhenCollidingDuringChargeAndDoesNotHitAgain() {
        GameObject target = target(TargetCategory.UNIT, new Vector3(5f, 0f, 0f));
        GameObject collidedEnemy = target(TargetCategory.UNIT, new Vector3(0.25f, 0f, 0f));
        Damageable damageable = mock(Damageable.class);
        when(collidedEnemy.getComponent(Damageable.class)).thenReturn(damageable);
        when(speedTracker.getTier()).thenReturn(1);
        ReflectionTestUtils.setField(mob, "target", target);
        mob.setState(mob.new ChargeState());

        mob.onCollisionWithEnemy(collidedEnemy);
        mob.update();
        mob.onCollisionWithEnemy(collidedEnemy);

        verify(damageable).onDamaged(any());
        assertThat(ReflectionTestUtils.getField(mob, "currentState"))
                .isInstanceOf(StormStagMob.PanicState.class);
    }

    @Test
    void ignoresEnemyCollisionOutsideCharge() {
        GameObject collidedEnemy = target(TargetCategory.UNIT, new Vector3(0.25f, 0f, 0f));
        Damageable damageable = mock(Damageable.class);
        when(collidedEnemy.getComponent(Damageable.class)).thenReturn(damageable);

        mob.onCollisionWithEnemy(collidedEnemy);

        verify(damageable, org.mockito.Mockito.never()).onDamaged(any());
    }

    @Test
    void resumesChargeWhenPanicFleeHitsWall() {
        GameObject target = target(TargetCategory.UNIT, new Vector3(5f, 0f, 0f));
        GameObject wall = mock(GameObject.class);
        when(wall.getType()).thenReturn(PrefabType.Wall);
        ReflectionTestUtils.setField(mob, "target", target);
        mob.setState(mob.new PanicState(target));

        ((Collidable) mob).onCollision(wall);

        assertThat(ReflectionTestUtils.getField(mob, "currentState"))
                .isInstanceOf(StormStagMob.ChargeState.class);
        assertThat(ReflectionTestUtils.getField(mob, "panicCooldownRemaining"))
                .isEqualTo(10f);
        verify(stormStag).setStatus(Status.Move);
    }

    @Test
    void chargesAroundAStaticObstacleAndReachesTheTarget() {
        position = new Vector3(3f, 0f, 5.5f);
        GameObject obstacle = obstacleAt(new Vector3(9.5f, 0f, 5.5f), 1f);
        sessionData.gameObjects.add(obstacle);
        GameObject target = beginCharge(new Vector3(15f, 0f, 5.5f));
        Damageable damageable = mock(Damageable.class);
        when(target.getComponent(Damageable.class)).thenReturn(damageable);
        when(speedTracker.getTier()).thenReturn(1);
        boolean detoured = false;

        for (int i = 0; i < 200; i++) {
            advanceCharge();
            assertThat(position.distance(obstacle.getPosition())).isGreaterThan(1.5);
            detoured |= Math.abs(position.getZ() - 5.5f) > 1.5f;
            if (ReflectionTestUtils.getField(mob, "currentState") instanceof StormStagMob.PanicState) {
                break;
            }
        }

        assertThat(detoured).isTrue();
        verify(damageable).onDamaged(any());
        verify(speedTracker).begin();
    }

    @Test
    void refreshesTheChargePathWhenAnObstacleAppearsAndDisappears() {
        position = new Vector3(3f, 0f, 5f);
        beginCharge(new Vector3(15f, 0f, 5f));
        mob.update();
        assertThat(rigidBody.getVelocity().getZ()).isZero();
        rigidBody.getVelocity().clear();

        sessionData.gameObjects.add(obstacleAt(new Vector3(9f, 0f, 5f), 1f));
        for (int i = 0; i < 21; i++) {
            frame++;
            mob.update();
            if (i < 20) rigidBody.getVelocity().clear();
        }
        assertThat(Math.abs(rigidBody.getVelocity().getZ())).isGreaterThan(0f);
        rigidBody.getVelocity().clear();

        sessionData.gameObjects.clear();
        for (int i = 0; i < 21; i++) {
            frame++;
            mob.update();
            if (i < 20) rigidBody.getVelocity().clear();
        }
        assertThat(rigidBody.getVelocity().getZ()).isZero();
        assertThat(rigidBody.getVelocity().getX()).isEqualTo(4f);
        verify(speedTracker).begin();
    }

    @Test
    void refreshesTheChargePathToFollowAMovingTarget() {
        Vector3 targetPosition = new Vector3(10f, 0f, 0f);
        beginCharge(targetPosition);
        mob.update();
        rigidBody.getVelocity().clear();
        targetPosition.setZ(5f);

        for (int i = 0; i < 21; i++) {
            frame++;
            mob.update();
            if (i < 20) rigidBody.getVelocity().clear();
        }

        assertThat(rigidBody.getVelocity().getZ()).isPositive();
        verify(speedTracker).begin();
    }

    @Test
    void closesTheFinalWaypointEvenInsideThePathReachThreshold() {
        beginCharge(new Vector3(0.3f, 0f, 0f));

        advanceCharge();

        assertThat(position.getX()).isPositive();
        assertThat(ReflectionTestUtils.getField(mob, "currentState"))
                .isInstanceOf(StormStagMob.ChargeState.class);
    }

    @Test
    void doesNotOvershootTheFinalWaypointAtHighSpeed() {
        beginCharge(new Vector3(0.1f, 0f, 0f));
        Object state = ReflectionTestUtils.getField(mob, "currentState");
        ReflectionTestUtils.setField(state, "currentSpeed", 4f);

        advanceCharge();

        assertThat(position.getX()).isEqualTo(0.1f);
    }

    @Test
    void stopsChargingWhenTheTargetIsDestroyed() {
        GameObject target = beginCharge(new Vector3(10f, 0f, 0f));
        when(target.getStatus()).thenReturn(Status.Destroyed);

        advanceCharge();

        assertThat(position).isEqualTo(Vector3.ZERO);
        assertThat(ReflectionTestUtils.getField(mob, "currentState"))
                .isInstanceOf(StormStagMob.TargetSearchState.class);
        verify(speedTracker, times(3)).reset();
    }

    @Test
    void doesNotCancelTheChargeWhenCollidingWithANonDamageableObstacle() {
        beginCharge(new Vector3(15f, 0f, 5f));
        GameObject obstacle = obstacleAt(new Vector3(9f, 0f, 5f), 1f);
        when(obstacle.getMaster()).thenReturn(Master.None);

        mob.onCollisionWithEnemy(obstacle);

        assertThat(ReflectionTestUtils.getField(mob, "currentState"))
                .isInstanceOf(StormStagMob.ChargeState.class);
    }

    private GameObject beginCharge(Vector3 targetPosition) {
        GameObject target = target(TargetCategory.UNIT, targetPosition);
        ReflectionTestUtils.setField(mob, "target", target);
        ReflectionTestUtils.setField(mob, "panicCooldownRemaining", 100f);
        mob.setState(mob.new ChargeState());
        return target;
    }

    private void advanceCharge() {
        frame++;
        mob.update();
        rigidBody.applyVelocity();
    }

    private GameObject obstacleAt(Vector3 obstaclePosition, float radius) {
        GameObject obstacle = mock(GameObject.class);
        when(obstacle.isActive()).thenReturn(true);
        when(obstacle.hasComponent(StaticObstacle.class)).thenReturn(true);
        when(obstacle.getPosition()).thenReturn(obstaclePosition);
        when(obstacle.getFirstCircleCollider(false))
                .thenReturn(Optional.of(new CircleCollider(obstacle, radius, false)));
        return obstacle;
    }

    private GameObject target(TargetCategory category, Vector3 position) {
        GameObject target = mock(GameObject.class);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getPosition()).thenReturn(position);
        when(target.getStatus()).thenReturn(Status.Idle);
        when(target.hasComponent(Damageable.class)).thenReturn(true);
        when(target.hasComponent(PlayerHealthComponent.class))
                .thenReturn(category == TargetCategory.PLAYER);
        when(target.getFirstCircleCollider(false)).thenReturn(Optional.empty());

        if (category != TargetCategory.PLAYER) {
            Mob targetMob = mock(Mob.class);
            float speed = category == TargetCategory.UNIT ? 1f : 0f;
            when(targetMob.getSpeed()).thenReturn(new Stat(speed));
            when(target.getComponent(Mob.class)).thenReturn(targetMob);
        }
        return target;
    }
}
