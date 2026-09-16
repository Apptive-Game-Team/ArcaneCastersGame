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
import com.wordonline.server.game.domain.object.component.mob.simple.PlayerHealthComponent;
import com.wordonline.server.game.domain.object.component.physic.Collidable;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StormStagMobTest {
    private GameContext gameContext;
    private GameSessionData sessionData;
    private GameObject stormStag;
    private MovementSpeedTracker speedTracker;
    private StormStagMob mob;

    @BeforeEach
    void setUp() {
        gameContext = mock(GameContext.class);
        sessionData = new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
        stormStag = mock(GameObject.class);
        speedTracker = mock(MovementSpeedTracker.class);
        Element element = new Element();
        element.addNative(ElementType.LIGHTNING);

        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getDeltaTime()).thenReturn(0.05f);
        when(stormStag.getGameContext()).thenReturn(gameContext);
        when(stormStag.getMaster()).thenReturn(Master.LeftPlayer);
        when(stormStag.getPosition()).thenReturn(Vector3.ZERO);
        when(stormStag.getStatus()).thenReturn(Status.Idle);
        when(stormStag.getElement()).thenReturn(element);
        when(stormStag.getComponent(RigidBody.class)).thenReturn(mock(RigidBody.class));
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
