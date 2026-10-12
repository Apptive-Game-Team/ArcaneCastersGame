package com.wordonline.server.game.util;

/**
 * Counts how often a fixed interval of game time has passed, carrying what is left over into the
 * next interval. A timer that resets to zero instead drops up to one frame per period, and how
 * much that is depends on the tick rate, so the same match would run slower at 20 FPS than at 60.
 */
public final class IntervalTimer {

    // k frames of 1/rate seconds summed in floating point can land a hair below k/rate. Without
    // this margin that frame would wait one more frame, which again depends on the tick rate.
    private static final double EPSILON_SECONDS = 1e-6;

    private double elapsedSeconds;

    /**
     * Adds one frame and returns how many whole intervals completed, usually 0 or 1. A long frame
     * returns more than 1 so the caller catches up rather than losing the time. The interval may
     * change between calls; the time already accumulated is kept.
     */
    public int advance(float deltaTime, float intervalSeconds) {
        if (intervalSeconds <= 0f) {
            throw new IllegalArgumentException("intervalSeconds must be positive: " + intervalSeconds);
        }
        elapsedSeconds += deltaTime;
        int completed = 0;
        while (elapsedSeconds + EPSILON_SECONDS >= intervalSeconds) {
            elapsedSeconds -= intervalSeconds;
            completed++;
        }
        return completed;
    }

    /**
     * What a component's own float timer keeps after it fired at {@code timer >= interval}. The
     * part of this frame that ran past the interval belongs to the next period. A timer that kept
     * counting while it waited for something else (no target in range, an attack that did not go
     * out) is past its interval by more than a frame; it fired late on purpose and starts over.
     */
    public static float carryOver(float timer, float intervalSeconds, float deltaTime) {
        float overshoot = timer - intervalSeconds;
        if (overshoot <= 0f || overshoot >= deltaTime) {
            return 0f;
        }
        return overshoot;
    }
}
