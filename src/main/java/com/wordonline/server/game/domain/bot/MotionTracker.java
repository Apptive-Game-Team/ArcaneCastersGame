package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.object.Vector3;

import java.util.HashMap;
import java.util.Map;

/**
 * How fast each object was moving, from where the bot saw it last time and where it sees it now.
 *
 * <p>Objects do not carry a velocity the bot could copy: rigid bodies clear theirs every frame. Two
 * snapshots are enough, though, because the bot sees the field every reaction interval anyway.
 *
 * <p>Only the brain that owns it reads or writes it. Its agent runs one think at a time, and each
 * hand-off to the executor goes through an atomic flag and a task submit, so a later think always
 * sees what the earlier one stored.
 */
final class MotionTracker {

    /** Snapshots further apart than this say nothing about how anything is moving now. */
    static final double MAX_SAMPLE_GAP_SECONDS = 2.0;

    /**
     * Faster than any unit walks. A jump beyond it is a knockback, a teleport or a reused id, and
     * leading by it would aim at nothing.
     */
    static final double MAX_TRACKED_SPEED = 8.0;

    private Map<Integer, Vector3> lastPositions = Map.of();
    private double lastSeenAtSeconds = Double.NaN;

    /**
     * Records this snapshot and returns the velocity, in units per second, of every object seen in
     * both this one and the last. Empty when there is no usable previous snapshot.
     */
    Map<Integer, Vector3> observe(BotEye eye) {
        double now = eye.observedAtSeconds();
        Map<Integer, Vector3> positions = new HashMap<>();
        for (BotVisibleObject object : eye.gameObjectList()) {
            positions.put(object.id(), object.position());
        }

        Map<Integer, Vector3> velocities = new HashMap<>();
        double gap = now - lastSeenAtSeconds;
        if (gap > 0 && gap <= MAX_SAMPLE_GAP_SECONDS) {
            for (Map.Entry<Integer, Vector3> entry : positions.entrySet()) {
                Vector3 previous = lastPositions.get(entry.getKey());
                if (previous == null) {
                    continue;
                }
                Vector3 velocity = entry.getValue().subtract(previous).multiply((float) (1.0 / gap));
                if (velocity.distance(Vector3.ZERO) <= MAX_TRACKED_SPEED) {
                    velocities.put(entry.getKey(), velocity);
                }
            }
        }

        // An eye with no time stamp cannot be measured against, so it does not become the baseline.
        if (!Double.isNaN(now)) {
            lastPositions = positions;
            lastSeenAtSeconds = now;
        }
        return velocities;
    }
}
