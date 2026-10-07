package com.wordonline.server.game.service.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.component.physic.RigidBody;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.dto.Status;

/**
 * The river is held by the colliders of its water cells, through the real physics system and the
 * real prefab initializer. The frame length is 0.05 s, the loop's 20 frames per second. Walking
 * speed 3 is a fast ground mob; 6 is a coward's panic flee (twice the walking speed); 8 is the
 * boulder strike push at mass 1 and 4 is the status knockback at full proximity.
 */
class PhysicSystemRiverColliderTest {

    private static final float DELTA_TIME = 0.05f;
    private final RiverWorld world = new RiverWorld(Terrain.RIVER);

    @Test
    void aBodyWalkingStraightAtTheWaterFromTheLeftBankStopsAtTheBank() {
        for (float z : new float[] {0.5f, 4.5f, 5.5f, 9.5f}) {
            GameObject body = world.body(Master.LeftPlayer, new Vector3(5f, 0f, z));

            float furthest = walk(body, 3f, 0f, 120);

            assertThat(furthest).as("z=%s", z).isLessThan(8f - 0.5f + 0.15f);
            assertThat(Terrain.RIVER.isWaterAt(body.getPosition())).isFalse();
        }
    }

    @Test
    void aBodyWalkingStraightAtTheWaterFromTheRightBankStopsAtTheBank() {
        for (float z : new float[] {0.5f, 4.5f, 5.5f, 9.5f}) {
            GameObject body = world.body(Master.LeftPlayer, new Vector3(13f, 0f, z));

            float furthest = walk(body, -3f, 0f, 120);

            assertThat(furthest).as("z=%s", z).isGreaterThan(10f + 0.5f - 0.15f);
            assertThat(Terrain.RIVER.isWaterAt(body.getPosition())).isFalse();
        }
    }

    @Test
    void aBodyWalkingAlongTheBridgeEdgeIntoTheSideOfTheWaterStopsOnTheBridge() {
        // 다리 위 (8.5, 3.4)에서 +z 로 가면 물 칸 (8, 4)의 아래 변 z = 4 에 막힌다
        GameObject body = world.body(Master.LeftPlayer, new Vector3(8.5f, 0f, 2.5f));

        for (int frame = 0; frame < 80; frame++) {
            body.getComponent(RigidBody.class).addVelocity(new Vector3(0f, 0f, 3f));
            world.tick();
        }

        assertThat(body.getPosition().getZ()).isLessThan(4f - 0.5f + 0.15f);
        assertThat(body.getPosition().getX()).isCloseTo(8.5f, within(1e-4f));
    }

    @Test
    void aKnockbackOfBoulderStrengthDoesNotCarryABodyAcrossTheRiver() {
        // 돌 강타: 속도 8, 0.65 초, 질량 1 이면 막히지 않을 때 5.2 를 간다. 물 폭은 2 이다
        for (int mass : new int[] {1, 2}) {
            for (float z : new float[] {5f, 4.2f, 5.8f}) {
                GameObject left = world.body(Master.LeftPlayer, new Vector3(6.9f, 0f, z), mass);
                GameObject right = world.body(Master.RightPlayer, new Vector3(11.1f, 0f, z), mass);
                float speed = 8f / (float) Math.sqrt(mass);

                for (int frame = 0; frame < 13; frame++) {
                    left.getComponent(RigidBody.class).addVelocity(new Vector3(speed, 0f, 0f));
                    right.getComponent(RigidBody.class).addVelocity(new Vector3(-speed, 0f, 0f));
                    world.tick();
                    assertThat(left.getPosition().getX()).as("mass=%s z=%s", mass, z).isLessThan(8f);
                    assertThat(right.getPosition().getX()).as("mass=%s z=%s", mass, z).isGreaterThan(10f);
                }
                left.destroy();
                right.destroy();
                world.sessionData.gameObjects.remove(left);
                world.sessionData.gameObjects.remove(right);
            }
        }
    }

    @Test
    void aBodyIsStoppedAtEverySpeedTheGameHasUpToTwelveUnitsPerSecond() {
        // 12 는 한 frame 에 0.6 이라 반경 0.5 보다 크지만, 걸리는 거리가 0.5 에서 시작하므로 가장자리 안에서 잡힌다
        for (float speed : new float[] {1f, 3f, 4f, 6f, 8f, 10f, 12f}) {
            GameObject body = world.body(Master.LeftPlayer, new Vector3(5.2f, 0f, 5f));

            float furthest = walk(body, speed, 0f, 60);

            assertThat(furthest).as("speed=%s", speed).isLessThan(8f);
            world.sessionData.gameObjects.remove(body);
        }
    }

    @Test
    void aBodyHeadedForAConvexCornerOfAPocketDoesNotEnterTheWater() {
        // 물 칸 (8, 4)의 모서리 (8, 4): 아래쪽 변과 왼쪽 변이 만나는 곳이다. 몸은 다리 쪽으로 미끄러져 건널 수는 있다
        for (float[] velocity : new float[][] {{3f, 3f}, {4f, 2f}, {2f, 4f}, {3f, 3.2f}}) {
            GameObject body = world.body(Master.LeftPlayer, new Vector3(6.8f, 0f, 2.8f));

            for (int frame = 0; frame < 100; frame++) {
                body.getComponent(RigidBody.class).addVelocity(new Vector3(velocity[0], 0f, velocity[1]));
                world.tick();
                assertThat(Terrain.RIVER.isWaterAt(body.getPosition())).as("%s frame %s", body.getPosition(), frame).isFalse();
            }
            world.sessionData.gameObjects.remove(body);
        }
    }

    @Test
    void aBodyPressedAlongTheBankSlidesAlongIt() {
        GameObject body = world.body(Master.LeftPlayer, new Vector3(7.4f, 0f, 4.2f));

        for (int frame = 0; frame < 15; frame++) {
            body.getComponent(RigidBody.class).addVelocity(new Vector3(3f, 0f, 2f));
            world.tick();
        }

        // the bank only cancels the x part, so z keeps its 2 * 0.05 * 15 = 1.5 (a little more: the
        // pocket corner at (8, 4) deflects the first touch), and the body stays on the bank, z < 6
        assertThat(body.getPosition().getX()).isLessThan(8f - 0.5f + 0.15f);
        assertThat(body.getPosition().getZ()).isBetween(4.2f + 1.5f - 0.02f, 4.2f + 1.5f + 0.15f);
        assertThat(Terrain.RIVER.isWaterAt(body.getPosition())).isFalse();
    }

    @Test
    void aCrowdLeaningOnTheBankDoesNotPushABodyOver() {
        List<GameObject> crowd = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            crowd.add(world.body(Master.LeftPlayer, new Vector3(6.6f + (i % 3) * 0.35f, 0f, 4.1f + (i / 3) * 0.55f)));
        }

        for (int frame = 0; frame < 300; frame++) {
            for (int i = 0; i < crowd.size(); i += 2) {
                // every other body leans on the bank, the rest stand idle and take the push
                crowd.get(i).getComponent(RigidBody.class).addVelocity(new Vector3(3f, 0f, 0f));
            }
            world.tick();
            for (GameObject body : crowd) {
                // a body may slide off the bank end onto a bridge, but never into the water
                assertThat(Terrain.RIVER.isWaterAt(body.getPosition())).as("%s frame %s", body.getPosition(), frame).isFalse();
            }
        }
    }

    @Test
    void aFlyerCrossesTheRiverUntouched() {
        GameObject flyer = world.flyer(new Vector3(5f, 3f, 5f));

        for (int frame = 0; frame < 80; frame++) {
            flyer.getComponent(RigidBody.class).addVelocity(new Vector3(3f, 0f, 0f));
            world.tick();
        }

        // 80 frame 이면 x = 5 에서 17 까지 가며 물 위를 지난다
        assertThat(flyer.getPosition().getX()).isCloseTo(5f + 3f * DELTA_TIME * 80, within(1e-3f));
        assertThat(flyer.getPosition().getZ()).isEqualTo(5f);
    }

    @Test
    void aProjectileCrossesTheRiverUntouchedAtGroundHeightToo() {
        // 14 는 evil_ent 의 projectile_speed 이다. Damageable 이 없으니 지상 몸이 아니다
        for (float height : new float[] {0f, 0.5f}) {
            GameObject projectile = world.projectile(new Vector3(5f, height, 5f));

            for (int frame = 0; frame < 15; frame++) {
                projectile.getComponent(RigidBody.class).addVelocity(new Vector3(14f, 0f, 0f));
                world.tick();
            }

            assertThat(projectile.getPosition().getX()).as("height=%s", height)
                    .isCloseTo(5f + 14f * DELTA_TIME * 15, within(1e-3f));
            world.sessionData.gameObjects.remove(projectile);
        }
    }

    @Test
    void aDyingBodyFallingOverTheWaterIsNotHeldByIt() {
        GameObject body = world.body(Master.LeftPlayer, new Vector3(7.7f, 0f, 5f));
        body.setStatus(Status.Dying);

        for (int frame = 0; frame < 20; frame++) {
            body.getComponent(RigidBody.class).addVelocity(new Vector3(3f, 0f, 0f));
            world.tick();
        }

        assertThat(body.getPosition().getX()).isCloseTo(7.7f + 3f * DELTA_TIME * 20, within(1e-3f));
    }

    @Test
    void aGroundBodyCrossesABridgeFromEndToEndWithoutTouchingAnyEdge() {
        // 다리 가운데 줄 (z = 2.5, 7.5)은 물 변에서 1.5 떨어져 있다
        for (float z : new float[] {2.5f, 7.5f}) {
            GameObject body = world.body(Master.LeftPlayer, new Vector3(5f, 0f, z));

            for (int frame = 0; frame < 80; frame++) {
                body.getComponent(RigidBody.class).addVelocity(new Vector3(3f, 0f, 0f));
                world.tick();
                assertThat(world.touchesWater(body)).as("z=%s frame %s", z, frame).isFalse();
            }

            // x = 5 to 17: from the left bank to the right bank, over the whole 2-wide bridge
            assertThat(body.getPosition().getX()).isCloseTo(5f + 3f * DELTA_TIME * 80, within(1e-3f));
            assertThat(body.getPosition().getZ()).isEqualTo(z);
            world.sessionData.gameObjects.remove(body);
        }
    }

    @Test
    void waterOnlyPushesAndNeverReportsACollisionToEitherSide() {
        GameObject body = world.body(Master.LeftPlayer, new Vector3(7.3f, 0f, 5f));

        walk(body, 3f, 0f, 40);

        assertThat(body.getComponent(RiverWorld.Touchable.class).touches).isZero();
    }

    @Test
    void aBodyThatEndsUpInsideAPocketCannotLeaveItButIsNotThrownAcross() {
        // setPosition 으로 물 안에 들어간 몸: 가장자리가 안쪽으로 되밀어 물 칸 (8..10, 4..6)에 갇힌다
        double[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (double[] direction : directions) {
            GameObject body = world.body(Master.LeftPlayer, new Vector3(5f, 0f, 5f));
            body.setPosition(new Vector3(8.3f, 0f, 5.1f));

            for (int frame = 0; frame < 100; frame++) {
                body.getComponent(RigidBody.class)
                        .addVelocity(new Vector3((float) direction[0] * 3f, 0f, (float) direction[1] * 3f));
                world.tick();
            }

            Vector3 end = body.getPosition();
            assertThat(end.getX()).as("direction %s,%s end %s", direction[0], direction[1], end).isBetween(8f, 10f);
            assertThat(end.getZ()).as("direction %s,%s end %s", direction[0], direction[1], end).isBetween(4f, 6f);
            world.sessionData.gameObjects.remove(body);
        }
    }

    /** Pushes the body with a constant velocity every frame and returns the furthest x it reached on the way. */
    private float walk(GameObject body, float velocityX, float velocityZ, int frames) {
        float furthest = body.getPosition().getX();
        for (int frame = 0; frame < frames; frame++) {
            body.getComponent(RigidBody.class).addVelocity(new Vector3(velocityX, 0f, velocityZ));
            world.tick();
            furthest = velocityX >= 0
                    ? Math.max(furthest, body.getPosition().getX())
                    : Math.min(furthest, body.getPosition().getX());
        }
        return furthest;
    }
}
