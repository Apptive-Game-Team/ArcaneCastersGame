package com.wordonline.server.game.service;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PracticeHoverPauseTest {
    @Test
    void renewsBeyondThreeSecondsAndExpiresAfterTheLastHeartbeat() {
        AtomicLong clock = new AtomicLong();
        PracticeHoverPause pause = new PracticeHoverPause(clock::get);
        for (int i = 0; i < 8; i++) {
            pause.setHovering(7, true);
            clock.addAndGet(500);
            assertThat(pause.isPaused()).isTrue();
        }
        clock.addAndGet(1500);
        assertThat(pause.isPaused()).isFalse();
    }

    @Test
    void releasingOneParticipantDoesNotReleaseAnother() {
        PracticeHoverPause pause = new PracticeHoverPause(() -> 100L);
        pause.setHovering(7, true);
        pause.setHovering(8, true);
        pause.setHovering(7, false);
        assertThat(pause.isPaused()).isTrue();
        pause.setHovering(8, false);
        assertThat(pause.isPaused()).isFalse();
        pause.setHovering(7, true);
        pause.clear();
        assertThat(pause.isPaused()).isFalse();
    }
}
