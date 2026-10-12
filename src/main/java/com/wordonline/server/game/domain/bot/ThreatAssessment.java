package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * What the bot knows about hostile objects on the field this tick: which ones can still be hit,
 * how much pressure they put on the position the bot defends, and which lane that pressure sits in.
 *
 * <p>Instances are immutable snapshots, built from the frame snapshot the loop thread handed the
 * bot, so nothing here reads live game state.
 */
public final class ThreatAssessment {

    /** Beyond this distance an enemy no longer contributes to defensive pressure. */
    static final double PRESSURE_HORIZON = 9.0;

    /** Weighted threat count at which pressure is considered maximal. */
    static final double PRESSURE_SATURATION = 3.0;

    /** Threats whose z coordinates sit closer than this push down the same lane. */
    static final double LANE_GROUPING_GAP = 2.5;

    /**
     * How much of an allied defender's strength cancels an enemy's. Below one on purpose: a bot
     * that counts every ally as a full answer stops defending one unit too early.
     */
    static final double ALLY_COVER_WEIGHT = 0.8;

    private final List<EnemyThreat> threats;
    private final List<Defender> defenders;
    private final Vector3 defendedPosition;

    private ThreatAssessment(List<EnemyThreat> threats, List<Defender> defenders, Vector3 defendedPosition) {
        this.threats = threats;
        this.defenders = defenders;
        this.defendedPosition = defendedPosition;
    }

    /** Every enemy counts as one unit and allies are not considered. */
    public static ThreatAssessment observe(List<BotVisibleObject> gameObjects,
                                           Master enemySide,
                                           Vector3 defendedPosition,
                                           int enemyPlayerHp) {
        return observe(gameObjects, enemySide, defendedPosition, enemyPlayerHp, object -> 1.0, false);
    }

    /**
     * @param strength    how many average units an object counts as in pressure
     * @param allyCover   whether allied units near the defended lane take pressure off
     */
    static ThreatAssessment observe(List<BotVisibleObject> gameObjects,
                                    Master enemySide,
                                    Vector3 defendedPosition,
                                    int enemyPlayerHp,
                                    ToDoubleFunction<BotVisibleObject> strength,
                                    boolean allyCover) {
        Master allySide = BotSideUtil.getEnemySide(enemySide);
        List<EnemyThreat> observed = new ArrayList<>();
        List<Defender> defenders = new ArrayList<>();
        for (BotVisibleObject gameObject : gameObjects) {
            if (!gameObject.targetable()) {
                continue;
            }
            Vector3 position = gameObject.position();
            boolean playerCore = gameObject.type() == PrefabType.Player;

            if (gameObject.master() == enemySide) {
                observed.add(new EnemyThreat(
                        gameObject.id(),
                        position,
                        playerCore ? enemyPlayerHp : gameObject.hp(),
                        position.distance(defendedPosition),
                        playerCore,
                        playerCore ? 0.0 : strength.applyAsDouble(gameObject)));
            } else if (allyCover && gameObject.master() == allySide && gameObject.mob() && !playerCore) {
                defenders.add(new Defender(
                        position, position.distance(defendedPosition), strength.applyAsDouble(gameObject)));
            }
        }
        observed.sort(Comparator.comparingDouble(EnemyThreat::distanceToDefendedPosition));
        return new ThreatAssessment(List.copyOf(observed), List.copyOf(defenders), defendedPosition);
    }

    public List<EnemyThreat> threats() {
        return threats;
    }

    public boolean hasThreats() {
        return !threats.isEmpty();
    }

    /**
     * How hard the bot is being pushed, from 0 (nothing nearby, or already held) to 1 (more at the
     * door than what stands in their way).
     */
    public double pressure() {
        double weighted = 0;
        for (EnemyThreat threat : threats) {
            weighted += proximityWeight(threat) * threat.strength();
        }
        double cover = 0;
        for (Defender defender : defenders) {
            if (holdsAPressingLane(defender)) {
                cover += proximityWeight(defender.distanceToDefendedPosition()) * defender.strength();
            }
        }
        return Math.clamp((weighted - ALLY_COVER_WEIGHT * cover) / PRESSURE_SATURATION, 0.0, 1.0);
    }

    /**
     * The lane the incoming pressure is concentrated in, as a z coordinate: the weighted average of
     * every pressing threat. Falls back to the defended position's own lane when nothing is close
     * enough to matter.
     */
    public float contestedLaneZ() {
        return weightedLaneZ(threats);
    }

    /**
     * The single lane pushing hardest, as a z coordinate. Unlike {@link #contestedLaneZ()}, two
     * lanes pushing at once do not average out to the empty space between them.
     */
    public float strongestLaneZ() {
        List<EnemyThreat> pressing = threats.stream()
                .filter(threat -> laneWeight(threat) > 0)
                .sorted(Comparator.comparingDouble(threat -> threat.position().getZ()))
                .toList();
        if (pressing.isEmpty()) {
            return defendedPosition.getZ();
        }

        List<EnemyThreat> heaviest = List.of();
        double heaviestWeight = -1;
        List<EnemyThreat> lane = new ArrayList<>();
        for (EnemyThreat threat : pressing) {
            if (!lane.isEmpty()
                    && threat.position().getZ() - lane.getLast().position().getZ() > LANE_GROUPING_GAP) {
                double weight = totalLaneWeight(lane);
                if (weight > heaviestWeight) {
                    heaviest = lane;
                    heaviestWeight = weight;
                }
                lane = new ArrayList<>();
            }
            lane.add(threat);
        }
        if (totalLaneWeight(lane) > heaviestWeight) {
            heaviest = lane;
        }
        return weightedLaneZ(heaviest);
    }

    /**
     * The same assessment with every threat moved along its velocity for the given time. Threats
     * with no known velocity stay where they are, and so do the defenders.
     */
    ThreatAssessment ledBy(Map<Integer, Vector3> velocities, double seconds) {
        if (velocities.isEmpty() || seconds <= 0) {
            return this;
        }
        List<EnemyThreat> moved = new ArrayList<>(threats.size());
        for (EnemyThreat threat : threats) {
            Vector3 velocity = velocities.get(threat.objectId());
            moved.add(velocity == null
                    ? threat
                    : threat.movedTo(threat.position().plus(velocity.multiply((float) seconds)), defendedPosition));
        }
        moved.sort(Comparator.comparingDouble(EnemyThreat::distanceToDefendedPosition));
        return new ThreatAssessment(List.copyOf(moved), defenders, defendedPosition);
    }

    private float weightedLaneZ(List<EnemyThreat> lane) {
        double weightSum = 0;
        double weightedZ = 0;
        for (EnemyThreat threat : lane) {
            double weight = laneWeight(threat);
            weightSum += weight;
            weightedZ += weight * threat.position().getZ();
        }
        return weightSum == 0 ? defendedPosition.getZ() : (float) (weightedZ / weightSum);
    }

    private static double totalLaneWeight(List<EnemyThreat> lane) {
        double sum = 0;
        for (EnemyThreat threat : lane) {
            sum += laneWeight(threat);
        }
        return sum;
    }

    // A defender only answers pressure in its own lane; one in the far lane is no help here.
    private boolean holdsAPressingLane(Defender defender) {
        for (EnemyThreat threat : threats) {
            if (proximityWeight(threat) > 0
                    && Math.abs(threat.position().getZ() - defender.position().getZ()) <= LANE_GROUPING_GAP) {
                return true;
            }
        }
        return false;
    }

    // Strength decides which lane matters. With strength off every threat weighs one, which is how
    // lanes were weighed before strength existed.
    private static double laneWeight(EnemyThreat threat) {
        return proximityWeight(threat) * threat.strength();
    }

    private static double proximityWeight(EnemyThreat threat) {
        if (threat.playerCore()) {
            return 0;
        }
        return proximityWeight(threat.distanceToDefendedPosition());
    }

    private static double proximityWeight(double distanceToDefendedPosition) {
        return Math.clamp(1.0 - distanceToDefendedPosition / PRESSURE_HORIZON, 0.0, 1.0);
    }

    private record Defender(Vector3 position, double distanceToDefendedPosition, double strength) {
    }
}
