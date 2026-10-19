package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.config.GameConfig;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

import java.util.Random;

/**
 * Chooses where a summon or structure should be dropped.
 *
 * <p>Placement used to be a uniformly random point in the half-disc facing the enemy, which
 * scattered blockers across lanes the fight was not in. The planner instead puts the object in the
 * contested lane: ahead of the bot when it is being pushed, so it intercepts, and further forward
 * when the field is clear, so it starts advancing.
 *
 * <p>How well it reads the field depends on the bot: {@link Options} says whether it picks the single
 * strongest lane and whether the unit being placed is a ranged one that belongs behind the line.
 */
public final class PlacementPlanner {

    /** Fraction of the gap to the nearest threat at which a blocker is dropped. */
    static final double INTERCEPT_GAP_FRACTION = 0.6;

    /** Fraction of the cast range used when there is nothing to intercept. */
    static final double ADVANCE_RANGE_FRACTION = 0.9;

    /** Lane spread applied when advancing, so repeated summons do not stack on one point. */
    static final double ADVANCE_LANE_JITTER = 1.5;

    /** Where a ranged unit goes when intercepting: behind the point a blocker would hold. */
    static final double RANGED_INTERCEPT_GAP_FRACTION = 0.35;

    /** Where a ranged unit goes when advancing: short of where a blocker would be sent. */
    static final double RANGED_ADVANCE_RANGE_FRACTION = 0.6;

    /**
     * @param strongestLane defend the single most pressured lane rather than the average of all
     * @param ranged        the unit attacks from range and should stand behind the line
     */
    public record Options(boolean strongestLane, boolean ranged) {
        /** How every bot placed before placement options existed. */
        public static final Options BASIC = new Options(false, false);
    }

    private PlacementPlanner() {
    }

    public static Vector3 plan(Vector3 casterPosition,
                               double castRange,
                               Master botSide,
                               ThreatAssessment threats,
                               Random random) {
        return plan(casterPosition, castRange, botSide, threats, Options.BASIC, random);
    }

    public static Vector3 plan(Vector3 casterPosition,
                               double castRange,
                               Master botSide,
                               ThreatAssessment threats,
                               Options options,
                               Random random) {
        float forward = forwardDirection(botSide);
        float laneZ = options.strongestLane() ? threats.strongestLaneZ() : threats.contestedLaneZ();

        double offset;
        if (threats.hasThreats()) {
            double gap = interceptGap(threats, laneZ, options.strongestLane());
            double fraction = options.ranged() ? RANGED_INTERCEPT_GAP_FRACTION : INTERCEPT_GAP_FRACTION;
            offset = Math.min(castRange, gap * fraction);
        } else {
            offset = castRange * (options.ranged() ? RANGED_ADVANCE_RANGE_FRACTION : ADVANCE_RANGE_FRACTION);
            laneZ += (float) ((random.nextDouble() * 2 - 1) * ADVANCE_LANE_JITTER);
        }

        Vector3 desired = new Vector3(
                casterPosition.getX() + forward * (float) offset,
                casterPosition.getY(),
                laneZ);
        return clampToRange(casterPosition, clampToMapBounds(desired), castRange);
    }

    // The nearest threat sets how far out to meet it. Defending one lane, that is the nearest threat
    // in that lane: meeting a closer one in the other lane would put the blocker short of its own.
    private static double interceptGap(ThreatAssessment threats, float laneZ, boolean laneOnly) {
        EnemyThreat nearest = threats.threats().getFirst();
        if (laneOnly) {
            nearest = threats.threats().stream()
                    .filter(threat -> !threat.playerCore())
                    .filter(threat -> Math.abs(threat.position().getZ() - laneZ) <= ThreatAssessment.LANE_GROUPING_GAP)
                    .findFirst()
                    .orElse(nearest);
        }
        return nearest.distanceToDefendedPosition();
    }

    private static float forwardDirection(Master botSide) {
        return switch (botSide) {
            case LeftPlayer -> 1f;
            case RightPlayer -> -1f;
            default -> throw new IllegalArgumentException("Unknown botSide: " + botSide);
        };
    }

    private static Vector3 clampToMapBounds(Vector3 position) {
        return new Vector3(
                Math.clamp(position.getX(), 0f, (float) GameConfig.WIDTH),
                position.getY(),
                Math.clamp(position.getZ(), 0f, (float) GameConfig.HEIGHT));
    }

    private static Vector3 clampToRange(Vector3 origin, Vector3 position, double range) {
        double distance = origin.distance(position);
        if (distance <= range || distance == 0) {
            return position;
        }
        return origin.plus(position.subtract(origin).normalize().multiply((float) range));
    }
}
