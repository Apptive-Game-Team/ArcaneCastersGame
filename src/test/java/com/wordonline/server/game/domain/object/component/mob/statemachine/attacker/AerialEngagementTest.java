package com.wordonline.server.game.domain.object.component.mob.statemachine.attacker;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.GameSessionData;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.mob.Mob;
import com.wordonline.server.game.domain.object.component.mob.detector.TargetMask;
import com.wordonline.server.game.domain.object.component.physic.CircleCollider;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;
import com.wordonline.server.game.service.GameContext;
import com.wordonline.server.game.util.Physics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Attack range is a cylinder by default: horizontal edge distance and vertical center distance must
 * each be within the configured range. A mob constructed with verticalRangeIgnored holds a hover
 * height it only leaves once it commits, so it engages on horizontal distance alone.
 */
class AerialEngagementTest {

    private static final float HOVER_HEIGHT = GameConfig.AERIAL_MOB_INIT_HEIGHT;
    private static final float ATTACK_RANGE = 1.5f;

    private GameContext gameContext;
    private GameSessionData sessionData;

    @BeforeEach
    void setUp() {
        gameContext = mock(GameContext.class);
        sessionData = new GameSessionData(mock(PlayerData.class), mock(PlayerData.class));
        when(gameContext.getGameSessionData()).thenReturn(sessionData);
        when(gameContext.getDeltaTime()).thenReturn(0.05f);
        Physics physics = mock(Physics.class);
        when(gameContext.getPhysics()).thenReturn(physics);
        when(physics.overlapSphereAll(any(GameObject.class), anyFloat())).thenReturn(List.of());
    }

    @Test
    void hoveringMobAttacksAGroundTargetItIsStandingOver() {
        GameObject groundTarget = groundTarget();
        GameObject flyer = hoveringObject();
        TestAerialMob mob = new TestAerialMob(flyer, groundTarget, true);
        flyer.getComponents().add(mob);
        mob.start();

        runFrames(mob, 40);

        assertThat(mob.attacked)
                .as("aerial mob hovering directly over its target must engage")
                .isTrue();
    }

    @Test
    void hoveringMobStillWaitsWhileTheHorizontalGapExceedsRange() {
        GameObject groundTarget = groundTarget();
        GameObject flyer = hoveringObject();
        flyer.setPosition(new Vector3(ATTACK_RANGE + 5f, HOVER_HEIGHT, 0f));
        TestAerialMob mob = new TestAerialMob(flyer, groundTarget, true);
        flyer.getComponents().add(mob);
        mob.start();

        runFrames(mob, 3);

        assertThat(mob.attacked)
                .as("dropping the vertical term must not widen the horizontal reach")
                .isFalse();
    }

    @Test
    void groundMobKeepsTheVerticalTermOfTheRangeCheck() {
        GameObject groundTarget = groundTarget();
        GameObject walker = new GameObject(Master.LeftPlayer, PrefabType.RockSlime, new Vector3(0f, HOVER_HEIGHT, 0f), gameContext);
        walker.setStatus(Status.Idle);
        walker.addCollider(new CircleCollider(walker, 0.5f, false));
        walker.getComponents().add(new RigidBody(walker, 1));
        TestAerialMob mob = new TestAerialMob(walker, groundTarget, false);
        walker.getComponents().add(mob);
        mob.start();

        runFrames(mob, 40);

        assertThat(mob.attacked)
                .as("a mob with no hover keeps the cylindrical check, so the vertical gap blocks it")
                .isFalse();
    }

    @Test
    void groundMobKeepsItsExistingEngagementDistance() {
        GameObject groundTarget = groundTarget();
        GameObject walker = new GameObject(Master.LeftPlayer, PrefabType.RockSlime, new Vector3(ATTACK_RANGE + 2f, 0f, 0f), gameContext);
        walker.setStatus(Status.Idle);
        walker.addCollider(new CircleCollider(walker, 0.5f, false));
        walker.getComponents().add(new RigidBody(walker, 1));
        TestAerialMob mob = new TestAerialMob(walker, groundTarget, false);
        walker.getComponents().add(mob);
        mob.start();

        runFrames(mob, 3);

        assertThat(mob.attacked)
                .as("a ground mob still out of range must not attack yet")
                .isFalse();
    }

    @Test
    void diveProgressCompletesInsteadOfProducingNaNAtEqualAltitude() {
        GameObject flyer = hoveringObject();
        TestAerialMob mob = new TestAerialMob(flyer, groundTarget(), true);
        flyer.getComponents().add(mob);

        float sameAltitude = mob.exposeDiveProgress(new Vector3(0f, HOVER_HEIGHT, 0f), HOVER_HEIGHT);
        float halfWayDown = mob.exposeDiveProgress(new Vector3(0f, HOVER_HEIGHT * 2f, 0f), 0f);

        assertThat(Float.isNaN(sameAltitude)).isFalse();
        assertThat(sameAltitude).isEqualTo(1f);
        assertThat(halfWayDown).isEqualTo(0.5f);
    }

    private void runFrames(TestAerialMob mob, int frames) {
        for (int frame = 0; frame < frames && !mob.attacked; frame++) {
            mob.update();
        }
    }

    private GameObject hoveringObject() {
        GameObject flyer = new GameObject(Master.LeftPlayer, PrefabType.WindSpirit, new Vector3(0f, HOVER_HEIGHT, 0f), gameContext);
        flyer.setStatus(Status.Idle);
        flyer.addCollider(new CircleCollider(flyer, 0.3f, false));
        flyer.getComponents().add(new RigidBody(flyer, 1));
        sessionData.gameObjects.add(flyer);
        return flyer;
    }

    private GameObject groundTarget() {
        GameObject target = new GameObject(Master.RightPlayer, PrefabType.Player, Vector3.ZERO, gameContext);
        target.setStatus(Status.Idle);
        target.addCollider(new CircleCollider(target, 1f, false));
        target.getComponents().add(new TargetMob(target));
        sessionData.gameObjects.add(target);
        return target;
    }

    private static class TestAerialMob extends BehaviorMob {
        private boolean attacked;

        private TestAerialMob(GameObject gameObject, GameObject forcedTarget, boolean verticalRangeIgnored) {
            super(gameObject, 10, 1f, TargetMask.ANY.bit, 0f, ATTACK_RANGE, null, verticalRangeIgnored);
            setBehavior(target -> {
                attacked = true;
                return true;
            });
            this.target = forcedTarget;
            this.targetRadius = forcedTarget.getFirstCircleCollider().orElseThrow().getRadius();
        }

        private float exposeDiveProgress(Vector3 startPos, float targetY) {
            return diveProgress(startPos, targetY);
        }
    }

    private static class TargetMob extends Mob {
        private TargetMob(GameObject gameObject) {
            super(gameObject, 100, 0f);
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
