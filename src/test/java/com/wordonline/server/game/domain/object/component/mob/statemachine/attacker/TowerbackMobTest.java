package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.detector.Detector;
import com.wordonline.server.game.domain.object.component.mob.pathfinder.FlowFieldNavigation;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import com.wordonline.server.game.util.Physics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TowerbackMobTest {

    private static final float GROUND_RANGE = 0.6f;
    private static final float AIR_RANGE = 5f;
    private static final float TOWER_RADIUS = 0.5f;
    private static final float TARGET_RADIUS = 0.3f;
    private static final float DELTA_TIME = 0.05f;
    /** Enough frames for the idle state's first detection and one re-detection interval. */
    private static final int FRAMES_PAST_ONE_DETECTION = Math.round((Detector.DETECTING_INTERVAL + 0.2f) / DELTA_TIME);

    private GameContext gameContext;
    private GameSessionData sessionData;
    private GameObject tower;
    private TowerbackMob mob;

    @BeforeEach
    void setUp() {
        gameContext = mock(GameContext.class);
        sessionData = new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getDeltaTime()).thenReturn(DELTA_TIME);
        when(gameContext.getNavigation()).thenReturn(new FlowFieldNavigation(() -> 0, List::of));
        when(gameContext.getObjectsInfoDtoBuilder()).thenReturn(mock(ObjectsInfoDtoBuilder.class));
        Physics physics = mock(Physics.class);
        when(gameContext.getPhysics()).thenReturn(physics);
        when(physics.overlapSphereAll(any(GameObject.class), anyFloat())).thenReturn(List.of());

        tower = new GameObject(Master.LeftPlayer, PrefabType.Towerback, Vector3.ZERO, gameContext);
        tower.setStatus(Status.Idle);
        tower.addCollider(new CircleCollider(tower, TOWER_RADIUS, false));
        tower.getComponents().add(new RigidBody(tower, 1));
        mob = new TowerbackMob(tower, 100, 1f, 10, GROUND_RANGE, 20, AIR_RANGE, 1f);
        tower.getComponents().add(mob);
        sessionData.gameObjects.add(tower);
    }

    @Test
    void picksAnAirEnemyOverACloserGroundEnemy() {
        enemyOnGround(1f);
        GameObject flyer = enemyInAir(4f);

        mob.start();
        runFrames(1);

        assertThat(mob.target).isSameAs(flyer);
    }

    @Test
    void standsAndFiresAtAnAirEnemyInsideTheAirRange() {
        GameObject flyer = enemyInAir(4f);

        mob.start();
        runFrames(3);

        assertThat(mob.target).isSameAs(flyer);
        assertThat(currentState()).isInstanceOf(BehaviorMob.AttackState.class);
    }

    @Test
    void followsAnAirEnemyJustOutsideTheAirRange() {
        GameObject flyer = enemyInAir(AIR_RANGE + 1.5f);

        mob.start();
        runFrames(3);

        assertThat(mob.target).isSameAs(flyer);
        assertThat(currentState()).isInstanceOf(BehaviorMob.MoveState.class);
    }

    @Test
    void ignoresAnAirEnemyBeyondTheChaseRange() {
        GameObject walker = enemyOnGround(12f);
        enemyInAir(AIR_RANGE * TowerbackMob.AIR_CHASE_RANGE_MULTIPLIER + 2f);

        mob.start();
        runFrames(1);

        assertThat(mob.target).isSameAs(walker);
    }

    @Test
    void givesUpOnAnAirEnemyThatFliesOutOfTheChaseRange() {
        GameObject flyer = enemyInAir(AIR_RANGE + 1.5f);
        mob.start();
        runFrames(3);
        assertThat(mob.target).isSameAs(flyer);

        flyer.setPosition(new Vector3(AIR_RANGE * TowerbackMob.AIR_CHASE_RANGE_MULTIPLIER + 3f, GameConfig.AERIAL_MOB_INIT_HEIGHT, 0f));
        runFrames(1);

        assertThat(currentState()).isInstanceOf(BehaviorMob.IdleState.class);
    }

    @Test
    void leavesAGroundFightWhenAnAirEnemyArrives() {
        enemyOnGround(TOWER_RADIUS + TARGET_RADIUS + 0.2f);
        mob.start();
        runFrames(3);
        assertThat(currentState()).isInstanceOf(BehaviorMob.AttackState.class);

        GameObject flyer = enemyInAir(4f);
        runFrames(FRAMES_PAST_ONE_DETECTION);

        assertThat(mob.target).isSameAs(flyer);
    }

    private void runFrames(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            mob.update();
        }
    }

    private Object currentState() {
        return ReflectionTestUtils.getField(mob, "currentState");
    }

    private GameObject enemyInAir(float x) {
        return enemy(PrefabType.WindSpirit, new Vector3(x, GameConfig.AERIAL_MOB_INIT_HEIGHT, 0f));
    }

    private GameObject enemyOnGround(float x) {
        return enemy(PrefabType.RockSlime, new Vector3(x, 0f, 0f));
    }

    private GameObject enemy(PrefabType prefabType, Vector3 position) {
        GameObject enemy = new GameObject(Master.RightPlayer, prefabType, position, gameContext);
        enemy.setStatus(Status.Idle);
        enemy.addCollider(new CircleCollider(enemy, TARGET_RADIUS, false));
        enemy.getComponents().add(new TargetMob(enemy));
        sessionData.gameObjects.add(enemy);
        return enemy;
    }

    private static class TargetMob extends Mob {
        private TargetMob(GameObject gameObject) {
            super(gameObject, 100, 1f);
        }

        @Override
        public void onDeath() {
        }

        @Override
        public void start() {
        }

        @Override
        public void onDestroy() {
        }
    }
}
