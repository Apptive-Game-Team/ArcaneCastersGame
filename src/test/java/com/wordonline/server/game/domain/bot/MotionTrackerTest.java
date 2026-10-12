package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MotionTrackerTest {

    @Test
    void measuresVelocityBetweenTwoSnapshots() {
        MotionTracker tracker = new MotionTracker();

        assertThat(tracker.observe(eye(1.0, new Vector3(10, 0, 5)))).isEmpty();
        Map<Integer, Vector3> velocities = tracker.observe(eye(1.5, new Vector3(9, 0, 5)));

        assertThat(velocities).containsEntry(1, new Vector3(-2, 0, 0));
    }

    @Test
    void snapshotsWithoutGameTimeAreNeverMeasured() {
        MotionTracker tracker = new MotionTracker();

        tracker.observe(eye(Double.NaN, new Vector3(10, 0, 5)));

        assertThat(tracker.observe(eye(Double.NaN, new Vector3(9, 0, 5)))).isEmpty();
    }

    @Test
    void snapshotsTooFarApartSayNothing() {
        MotionTracker tracker = new MotionTracker();

        tracker.observe(eye(1.0, new Vector3(10, 0, 5)));

        assertThat(tracker.observe(eye(1.0 + MotionTracker.MAX_SAMPLE_GAP_SECONDS + 0.1, new Vector3(9, 0, 5))))
                .isEmpty();
    }

    // 넉백이나 순간이동은 이동 속도가 아니다. 그걸 따라 조준하면 허공을 친다.
    @Test
    void ignoresJumpsFasterThanAnyUnitWalks() {
        MotionTracker tracker = new MotionTracker();

        tracker.observe(eye(1.0, new Vector3(2, 0, 5)));

        assertThat(tracker.observe(eye(1.1, new Vector3(12, 0, 5)))).isEmpty();
    }

    private static BotEye eye(double time, Vector3 position) {
        BotVisibleObject object = new BotVisibleObject(1, Master.RightPlayer, PrefabType.FireSpirit, position, 10, true, true);
        return new BotEye(List.of(object), List.of(), 0, 100, time);
    }
}
