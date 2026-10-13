package com.wordonline.server.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class FrameRateRecorderTest {

    private static final long MILLISECOND = 1_000_000L;

    @Test
    void reportsZeroBeforeTwoFrames() {
        FrameRateRecorder recorder = new FrameRateRecorder();
        recorder.recordFrameStart(0);

        assertThat(recorder.meanFps()).isZero();
        assertThat(recorder.p10Fps()).isZero();
    }

    @Test
    void p10IsTheRateNinetyPercentOfFramesMet() {
        FrameRateRecorder recorder = new FrameRateRecorder();
        long now = 0;
        recorder.recordFrameStart(now);
        // 90 frames of 10 ms and 10 slow frames of 50 ms.
        for (int i = 0; i < 90; i++) {
            recorder.recordFrameStart(now += 10 * MILLISECOND);
        }
        for (int i = 0; i < 10; i++) {
            recorder.recordFrameStart(now += 50 * MILLISECOND);
        }

        assertThat(recorder.meanFps()).isCloseTo(100 / 1.4, within(0.01));
        assertThat(recorder.p10Fps()).isCloseTo(100.0, within(0.01));
    }

    @Test
    void oneSlowFrameInTenDragsP10Down() {
        FrameRateRecorder recorder = new FrameRateRecorder();
        long now = 0;
        recorder.recordFrameStart(now);
        for (int i = 0; i < 88; i++) {
            recorder.recordFrameStart(now += 10 * MILLISECOND);
        }
        for (int i = 0; i < 12; i++) {
            recorder.recordFrameStart(now += 20 * MILLISECOND);
        }

        assertThat(recorder.p10Fps()).isCloseTo(50.0, within(0.01));
    }
}
