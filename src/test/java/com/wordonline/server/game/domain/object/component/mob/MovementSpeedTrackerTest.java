package com.wordonline.server.game.domain.object.component.mob;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MovementSpeedTrackerTest {
    private static final float MAX_SPEED = 10f;

    @Test
    void mapsSpeedPercentageToChargeTier() {
        assertThat(MovementSpeedTracker.tierFor(0f, MAX_SPEED)).isEqualTo(1);
        assertThat(MovementSpeedTracker.tierFor(2.99f, MAX_SPEED)).isEqualTo(1);
        assertThat(MovementSpeedTracker.tierFor(3f, MAX_SPEED)).isEqualTo(2);
        assertThat(MovementSpeedTracker.tierFor(5.99f, MAX_SPEED)).isEqualTo(2);
        assertThat(MovementSpeedTracker.tierFor(6f, MAX_SPEED)).isEqualTo(3);
        assertThat(MovementSpeedTracker.tierFor(9.99f, MAX_SPEED)).isEqualTo(3);
        assertThat(MovementSpeedTracker.tierFor(10f, MAX_SPEED)).isEqualTo(4);
    }

    @Test
    void staysAtFirstTierWhenMaxSpeedIsInvalid() {
        assertThat(MovementSpeedTracker.tierFor(10f, 0f)).isEqualTo(1);
    }
}
