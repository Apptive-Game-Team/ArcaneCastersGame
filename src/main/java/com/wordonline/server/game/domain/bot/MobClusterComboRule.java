package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.implement.explode.AbstractExplosionMagic;
import com.wordonline.server.game.domain.object.Vector3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** An explosion on the biggest knot of enemy mobs within reach. */
final class MobClusterComboRule implements PriorityRule {

    static final String RULE_ID = "combo.mob-cluster";
    static final double CLUSTER_RADIUS = 2.5;
    static final int CLUSTER_MIN_MOBS = 3;

    @Override
    public BotCapability capability() {
        return BotCapability.MOB_CLUSTER_COMBO;
    }

    @Override
    public Optional<ScoredPlay> find(ThinkContext context) {
        List<BotVisibleObject> enemyMobs = context.objects().stream()
                .filter(object -> object.master() == context.enemySide() && object.mob() && object.targetable())
                .toList();
        if (enemyMobs.size() < CLUSTER_MIN_MOBS) {
            return Optional.empty();
        }

        List<ScoredPlay> candidates = new ArrayList<>();
        for (Magic magic : context.hand()) {
            if (!(magic instanceof AbstractExplosionMagic)) {
                continue;
            }
            int cost = context.spellStats().manaCost(magic);
            if (cost > context.mana()) {
                continue;
            }
            findBestCluster(enemyMobs, context.playerPos(), context.spellStats().castRange(magic))
                    .ifPresent(cluster -> candidates.add(context.scored(
                            magic, cluster.center(), cost, (double) cluster.count() / cost, RULE_ID,
                            magic.getClass().getSimpleName() + " targets a cluster of "
                                    + cluster.count() + " enemy mobs.")));
        }
        return candidates.stream().max(Comparator.comparingDouble(ScoredPlay::noisyScore));
    }

    private static Optional<Cluster> findBestCluster(List<BotVisibleObject> mobs,
                                                     Vector3 playerPos,
                                                     double castRange) {
        Cluster best = null;
        for (BotVisibleObject anchor : mobs) {
            List<BotVisibleObject> cluster = mobs.stream()
                    .filter(mob -> mob.position().distance(anchor.position()) <= CLUSTER_RADIUS)
                    .toList();
            if (cluster.size() < CLUSTER_MIN_MOBS) {
                continue;
            }
            Vector3 initialCenter = averagePosition(cluster);
            cluster = mobs.stream()
                    .filter(mob -> mob.position().distance(initialCenter) <= CLUSTER_RADIUS)
                    .toList();
            if (cluster.size() < CLUSTER_MIN_MOBS) {
                continue;
            }
            Vector3 center = averagePosition(cluster);
            if (center.distance(playerPos) > castRange) {
                continue;
            }
            Cluster candidate = new Cluster(center, cluster.size());
            if (best == null || candidate.count() > best.count()
                    || (candidate.count() == best.count()
                    && candidate.center().distance(playerPos) < best.center().distance(playerPos))) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Vector3 averagePosition(List<BotVisibleObject> objects) {
        float x = 0;
        float y = 0;
        float z = 0;
        for (BotVisibleObject object : objects) {
            x += object.position().getX();
            y += object.position().getY();
            z += object.position().getZ();
        }
        float count = objects.size();
        return new Vector3(x / count, y / count, z / count);
    }

    private record Cluster(Vector3 center, int count) {}
}
