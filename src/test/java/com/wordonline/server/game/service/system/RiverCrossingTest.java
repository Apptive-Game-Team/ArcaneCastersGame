package com.wordonline.server.game.service.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wordonline.server.game.domain.map.Terrain;
import com.wordonline.server.game.domain.object.GameObject;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

/**
 * Ground units over the river with everything real: the terrain set on the context after the
 * navigation object was built (as {@code GameLoop} does), {@code BehaviorMob} walking the flow
 * field, the physics system and the water colliders. This is what the live match runs, minus the
 * network. Ground units are meant to cross, and only on a bridge.
 */
class RiverCrossingTest {

    private final RiverWorld world = new RiverWorld(Terrain.RIVER);

    @Test
    void aWalkerCrossesOnTheMiddleOfABridgeAndNeverTouchesTheWater() {
        world.body(Master.RightPlayer, new Vector3(16f, 0f, 5f));
        GameObject walker = world.walker(new Vector3(2f, 0f, 5f), 2f);

        Vector3 last = new Vector3(walker.getPosition());
        List<Float> crossings = new ArrayList<>();
        for (int frame = 0; frame < 400; frame++) {
            world.tick();
            Vector3 position = walker.getPosition();
            assertThat(world.touchesWater(walker)).as("frame %s at %s", frame, position).isFalse();
            if ((last.getX() - 9f) * (position.getX() - 9f) < 0f) {
                crossings.add(position.getZ());
            }
            last = new Vector3(position);
        }

        assertThat(walker.getPosition().getX()).isGreaterThan(13f);
        assertThat(crossings).hasSize(1);
        assertThat(crossings.getFirst()).satisfiesAnyOf(
                z -> assertThat(z).isBetween(2f, 3f),
                z -> assertThat(z).isBetween(7f, 8f));
    }

    @Test
    void aCrowdOfWalkersAllReachTheOtherBankOverTheBridgesAndNeverStandInTheWater() {
        world.body(Master.RightPlayer, new Vector3(16f, 0f, 5f));
        List<GameObject> crowd = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            crowd.add(world.walker(new Vector3(6f + (i % 3) * 0.4f, 0f, 3.5f + (i / 3) * 0.6f), 2f));
        }

        for (int frame = 0; frame < 500; frame++) {
            world.tick();
            for (GameObject walker : crowd) {
                assertThat(Terrain.RIVER.isWaterAt(walker.getPosition()))
                        .as("frame %s at %s", frame, walker.getPosition()).isFalse();
            }
        }

        assertThat(crowd).allMatch(walker -> walker.getPosition().getX() > 10f);
    }
}
