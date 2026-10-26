package com.wordonline.server.game.service.system;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.Damageable;
import com.wordonline.server.game.domain.object.component.mob.pathfinder.FlowFieldNavigation;
import com.wordonline.server.game.domain.object.component.effect.receiver.LightningSummonEffectReceiver;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.mob.MovementSpeedTracker;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.CowardMob;
import com.wordonline.server.game.domain.object.component.mob.statemachine.attacker.StormStagMob;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.EdgeCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.component.physic.StaticObstacle;
import com.wordonline.server.game.domain.object.component.physic.WallCollision;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Effect;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.service.ObjectsInfoDtoBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class PhysicSystemWallCollisionTest {

    @Test
    void fullMapWallKeepsCowardInsideDuringActualPanicState() {
        GameContext gameContext = mock(GameContext.class);
        GameObject unit = new GameObject(
                Master.LeftPlayer,
                PrefabType.ZapMouse,
                new Vector3(0.4f, 0f, 5f),
                gameContext);
        RigidBody rigidBody = new RigidBody(unit, 2);
        CowardMob cowardMob = new CowardMob(
                unit,
                10,
                2f,
                TargetMask.GROUND.bit,
                1,
                1f,
                3f,
                1f);
        unit.addComponent(rigidBody);
        unit.addComponent(cowardMob);
        unit.addComponent(new LightningSummonEffectReceiver(unit));
        unit.addCollider(new CircleCollider(unit, 0.5f, false));
        unit.flushComponents();
        unit.setStatus(Status.Idle);

        GameObject threat = new GameObject(
                Master.RightPlayer,
                PrefabType.FireSpirit,
                new Vector3(2f, 0f, 5f),
                gameContext);
        cowardMob.setState(cowardMob.new PanicState(threat));

        GameObject wall = new GameObject(Master.None, PrefabType.Wall, Vector3.ZERO, gameContext);
        wall.addComponent(new WallCollision(wall));
        wall.addCollider(new EdgeCollider(
                wall,
                new Vector3(0f, 0f, 0f),
                new Vector3(0f, 0f, GameConfig.HEIGHT),
                false));
        wall.addCollider(new EdgeCollider(
                wall,
                new Vector3(GameConfig.WIDTH, 0f, GameConfig.HEIGHT),
                new Vector3(0f, 0f, GameConfig.HEIGHT),
                false));
        wall.addCollider(new EdgeCollider(
                wall,
                new Vector3(GameConfig.WIDTH, 0f, GameConfig.HEIGHT),
                new Vector3(GameConfig.WIDTH, 0f, 0f),
                false));
        wall.addCollider(new EdgeCollider(
                wall,
                new Vector3(0f, 0f, 0f),
                new Vector3(GameConfig.WIDTH, 0f, 0f),
                false));
        wall.flushComponents();
        wall.setStatus(Status.Idle);

        when(gameContext.getActiveGameObjects()).thenReturn(List.of(unit, wall));
        when(gameContext.getDeltaTime()).thenReturn(0.1f);

        PhysicSystem physicSystem = new PhysicSystem();
        for (int frame = 0; frame < 5; frame++) {
            unit.update();
            assertThat(unit.getEffects()).contains(Effect.Panic);

            physicSystem.update(gameContext);

            assertThat(unit.isDestroyed()).isFalse();
            assertThat(unit.getPosition().getX()).isGreaterThanOrEqualTo(0f);
        }
    }

    @Test
    void stormStagResumesChargeAfterPanicHitsLeftWall() {
        GameContext gameContext = mock(GameContext.class);
        when(gameContext.getDeltaTime()).thenReturn(0.05f);
        when(gameContext.getNavigation()).thenReturn(new FlowFieldNavigation(() -> 0, List::of));

        GameObject stag = new GameObject(
                Master.LeftPlayer, PrefabType.StormStag, new Vector3(0.4f, 0f, 5f), gameContext);
        stag.addComponent(new RigidBody(stag, 3));
        stag.addComponent(new MovementSpeedTracker(stag, 3f));
        StormStagMob mob = new StormStagMob(stag, 150, 3f, TargetMask.GROUND.bit,
                20, 5f, 2f, 2f);
        stag.addComponent(mob);
        stag.addCollider(new CircleCollider(stag, 0.8f, false));
        stag.flushComponents();
        stag.setStatus(Status.Idle);

        GameObject target = mock(GameObject.class);
        when(target.getPosition()).thenReturn(new Vector3(9f, 0f, 5f));
        when(target.getStatus()).thenReturn(Status.Idle);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        ReflectionTestUtils.setField(mob, "target", target);

        GameObject threat = mock(GameObject.class);
        when(threat.getPosition()).thenReturn(new Vector3(2f, 0f, 5f));
        mob.setState(mob.new PanicState(threat));

        GameObject wall = new GameObject(Master.None, PrefabType.Wall, Vector3.ZERO, gameContext);
        wall.addComponent(new WallCollision(wall));
        wall.addCollider(new EdgeCollider(wall,
                new Vector3(0f, 0f, 0f), new Vector3(0f, 0f, GameConfig.HEIGHT), false));
        wall.flushComponents();
        wall.setStatus(Status.Idle);

        when(gameContext.getActiveGameObjects()).thenReturn(List.of(stag, wall));
        PhysicSystem physics = new PhysicSystem();
        stag.update();
        physics.update(gameContext);
        assertThat(ReflectionTestUtils.getField(mob, "currentState"))
                .isInstanceOf(StormStagMob.ChargeState.class);

        for (int frame = 1; frame < 20; frame++) {
            stag.update();
            physics.update(gameContext);
        }

        assertThat(ReflectionTestUtils.getField(mob, "currentState"))
                .isInstanceOf(StormStagMob.ChargeState.class);
        assertThat(stag.getPosition().getX()).isGreaterThan(0.8f);
    }

    @Test
    void stormStagChargesPastAnObstacleWithPhysicalCollisionResolution() {
        GameContext context = mock(GameContext.class);
        when(context.getDeltaTime()).thenReturn(0.05f);
        when(context.getObjectsInfoDtoBuilder()).thenReturn(mock(ObjectsInfoDtoBuilder.class));
        GameObject stag = new GameObject(
                Master.LeftPlayer, PrefabType.StormStag, new Vector3(3f, 0f, 5f), context);
        stag.addComponent(new RigidBody(stag, 3));
        stag.addComponent(new MovementSpeedTracker(stag, 4f));
        StormStagMob mob = new StormStagMob(stag, 150, 4f, TargetMask.GROUND.bit,
                20, 2f, 2f, 2f);
        stag.addComponent(mob);
        stag.addCollider(new CircleCollider(stag, 0.8f, false));
        stag.flushComponents();

        GameObject rock = new GameObject(
                Master.None, PrefabType.RockRemnant, new Vector3(9f, 0f, 5f), context);
        rock.addComponent(new StaticObstacle(rock));
        rock.addCollider(new CircleCollider(rock, 1f, false));
        rock.flushComponents();
        rock.setStatus(Status.Idle);
        List<GameObject> objects = List.of(stag, rock);
        when(context.getActiveGameObjects()).thenReturn(objects);
        when(context.getNavigation()).thenReturn(new FlowFieldNavigation(() -> 0, () -> objects));

        GameObject target = mock(GameObject.class);
        when(target.getMaster()).thenReturn(Master.RightPlayer);
        when(target.getStatus()).thenReturn(Status.Idle);
        when(target.getPosition()).thenReturn(new Vector3(15f, 0f, 5f));
        Damageable damageable = mock(Damageable.class);
        when(target.getComponent(Damageable.class)).thenReturn(damageable);
        ReflectionTestUtils.setField(mob, "target", target);
        ReflectionTestUtils.setField(mob, "panicCooldownRemaining", 100f);
        mob.setState(mob.new ChargeState());

        PhysicSystem physics = new PhysicSystem();
        for (int frame = 0; frame < 300; frame++) {
            stag.update();
            physics.update(context);
            if (ReflectionTestUtils.getField(mob, "currentState") instanceof StormStagMob.PanicState) {
                break;
            }
        }

        verify(damageable).onDamaged(any());
        assertThat(stag.getPosition().getX()).isGreaterThan(14f);
    }
}
