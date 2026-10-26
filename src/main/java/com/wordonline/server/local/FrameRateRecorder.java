package com.wordonline.server.local;

import java.util.Arrays;

/**
 * Keeps every frame-start-to-frame-start interval of one match so the match can report its mean and
 * its 10th percentile frame rate. The shared servers only keep min, max and mean per match, which
 * cannot give a percentile.
 *
 * <p>Written by the loop thread once per frame and read once by the same thread when the loop
 * terminates; the watchdog may read it from another thread after reaping a stalled loop, hence the
 * synchronization. A fifteen minute match at 240 FPS is about 216,000 intervals, 1.7 MB; anything
 * past {@link #MAX_INTERVALS} is dropped rather than grown without bound.
 */
public class FrameRateRecorder {

    static final int MAX_INTERVALS = 1 << 20;
    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private long[] intervals = new long[4096];
    private int size;
    private long previousFrameStart = Long.MIN_VALUE;
    private long totalNanos;

    public synchronized void recordFrameStart(long nowNanos) {
        long previous = previousFrameStart;
        previousFrameStart = nowNanos;
        if (previous == Long.MIN_VALUE || size >= MAX_INTERVALS) {
            return;
        }
        long interval = Math.max(1L, nowNanos - previous);
        if (size == intervals.length) {
            intervals = Arrays.copyOf(intervals, Math.min(intervals.length * 2, MAX_INTERVALS));
        }
        intervals[size++] = interval;
        totalNanos += interval;
    }

    /** Frames per second over the whole match; 0 when fewer than two frames ran. */
    public synchronized double meanFps() {
        if (size == 0) {
            return 0;
        }
        return size * NANOS_PER_SECOND / totalNanos;
    }

    /**
     * The frame rate that 90% of frames met or beat: the reciprocal of the 90th percentile interval
     * (nearest rank). 0 when fewer than two frames ran.
     */
    public synchronized double p10Fps() {
        if (size == 0) {
            return 0;
        }
        long[] sorted = Arrays.copyOf(intervals, size);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(0.9 * size) - 1;
        long interval = sorted[Math.max(0, Math.min(rank, size - 1))];
        return NANOS_PER_SECOND / interval;
    }
}
