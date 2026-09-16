package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.magic.implement.explode.AbstractExplosionMagic;
import com.wordonline.server.game.domain.magic.implement.explode.OvergrowthMagic;
import com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.parser.MagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides what the bot casts and where.
 *
 * <p>Every affordable card in hand becomes a candidate. Offensive cards are scored by the damage
 * the blast would actually convert at its best landing spot; placement cards are scored by how much
 * the bot needs a body on the field right now. Both are divided by the card's real mana cost, so
 * the choice is value per mana rather than "the biggest thing in hand".
 */
@Slf4j
public class BotBrain {

    public record InputDecision(long magicId, Vector3 target, String ruleId, String reason) {}

    static final String SEED_SPIRIT_RULE = "combo.seed-spirit";
    static final String MOB_CLUSTER_RULE = "combo.mob-cluster";
    static final String VALUE_RULE = "score.best-value";
    static final String CYCLE_RULE = "cycle.low-utility";
    static final double COMBO_CLUSTER_RADIUS = 2.5;
    static final int COMBO_CLUSTER_MIN_MOBS = 3;

    /** Tempo value of removing one enemy from the field, on top of the damage itself. */
    static final double KILL_TEMPO_BONUS = 8.0;

    /** Board value a placement card is worth. One card is one cast now, so it is a flat term. */
    static final double PLACEMENT_VALUE_PER_CAST = 6.0;

    /** How much full defensive pressure inflates the value of putting a body down. */
    static final double DEFENSE_URGENCY_WEIGHT = 1.5;

    /** Scales the tag-based counter score into the same range as the damage-based scores. */
    static final double COUNTER_WEIGHT = 3.0;

    private final MagicParser magicParser;
    private final BotCounterEvaluator counterEvaluator;
    private final BotPersona persona;

    /** Non-null only for the tutorial opponent; every other tier scores plays the ordinary way. */
    private final HospitalityDirector hospitalityDirector;

    public BotBrain(MagicParser magicParser, BotCounterEvaluator counterEvaluator, BotPersona persona) {
        this(magicParser, counterEvaluator, persona, HospitalityDirector.DEFAULT_BOARD_SHARE);
    }

    /**
     * @param opponentNoviceProgress how far the human opponent is through the tutorial; the share of
     *                               their board a hospitality bot may match. Ignored by other tiers.
     */
    public BotBrain(MagicParser magicParser,
                    BotCounterEvaluator counterEvaluator,
                    BotPersona persona,
                    double opponentNoviceProgress) {
        this.magicParser = magicParser;
        this.counterEvaluator = counterEvaluator;
        this.persona = persona;
        this.hospitalityDirector = persona.tier() == BotTier.HOSPITALITY
                ? new HospitalityDirector(opponentNoviceProgress)
                : null;
    }

    // Runs on the bot executor thread. Everything it reads about the world comes from the snapshot,
    // never from a live GameObject; parameters are loaded once per session and read-only after that.
    public InputDecision think(BotEye botEye, Parameters parameters, Master botSide) {
        return think(botEye, parameters, botSide, false, ThreadLocalRandom.current());
    }

    /**
     * @param overdue the bot has been silent long enough that standing still would read as going
     *                easy on the player, so it must not hold out for a better moment
     */
    public InputDecision think(BotEye botEye, Parameters parameters, Master botSide, boolean overdue)
    {
        return think(botEye, parameters, botSide, overdue, ThreadLocalRandom.current());
    }

    InputDecision think(BotEye botEye, Parameters parameters, Master botSide, Random random) {
        return think(botEye, parameters, botSide, false, random);
    }

    private InputDecision think(BotEye botEye,
                                Parameters parameters,
                                Master botSide,
                                boolean overdue,
                                Random random) {
        List<Long> cardList = botEye.cardList();
        int mana = botEye.mana();
        try {
            log.trace("[Bot {}] Start thinking with cards={}, mana={}", botSide, cardList, mana);
            if (cardList.isEmpty()) {
                return null;
            }
            if (!(magicParser instanceof DatabaseMagicParser dbParser)) {
                log.warn("[Bot {}] magicParser is not DatabaseMagicParser, fallback disabled", botSide);
                return null;
            }

            Vector3 playerPos = BotSideUtil.getPlayerPosition(botSide);
            Master enemySide = BotSideUtil.getEnemySide(botSide);
            List<BotVisibleObject> enemies = botEye.gameObjectList().stream()
                    .filter(go -> go.master() == enemySide)
                    .toList();

            BotSpellStats spellStats = new BotSpellStats(parameters);
            ThreatAssessment threats = ThreatAssessment.observe(
                    botEye.gameObjectList(), enemySide, playerPos, botEye.enemyPlayerHp());
            double aggression = aggression(botEye, threats, botSide);

            // 접대 봇의 보드는 플레이어 보드의 일정 비율 아래로만 간다. 한 번의 소환만 비교하면
            // 싼 유닛을 계속 쌓아 결국 따라붙으므로, 이미 깔아 둔 것까지 더해서 본다. 비율과
            // 마나로 재는 이유는 각각 HospitalityDirector와 BoardValue에 적어 두었다.
            int manaBudget = Integer.MAX_VALUE;
            if (hospitalityDirector != null) {
                BoardValue boardValue = new BoardValue(dbParser, spellStats, parameters);
                int enemyBoardMana = boardValue.manaOnField(botEye.gameObjectList(), enemySide);
                int ownBoardMana = boardValue.manaOnField(botEye.gameObjectList(), botSide);
                manaBudget = hospitalityDirector.summonAllowance(enemyBoardMana, ownBoardMana);
                log.debug("[Bot {}] Hospitality board: enemy={} own={} budget={}",
                        botSide, enemyBoardMana, ownBoardMana, manaBudget);
            }

            List<Magic> hand = resolveHand(dbParser, cardList);
            if (hand.isEmpty()) {
                log.trace("[Bot {}] No card in hand resolves to a known magic", botSide);
                return null;
            }

            // Tutorial hospitality rules only permit summons. Priority combos are combat tactics,
            // so they run for ordinary tiers and still precede the legacy value scorer.
            if (hospitalityDirector == null) {
                Optional<ScoredPlay> combo = findSeedSpiritCombo(
                        hand, mana, spellStats, botEye.gameObjectList(), playerPos, botSide, random);
                if (combo.isEmpty()) {
                    combo = findMobClusterCombo(
                            hand, mana, spellStats, botEye.gameObjectList(), playerPos, enemySide, random);
                }
                if (combo.isPresent()) {
                    ScoredPlay chosen = combo.get();
                    log.debug("[Bot {}] Chose priority rule {}: {}", botSide, chosen.ruleId(), chosen.reason());
                    return chosen.toDecision();
                }
            }

            List<ScoredPlay> plays = new ArrayList<>();
            boolean hasAffordableCard = false;
            Magic cheapestAffordable = null;
            int cheapestCost = Integer.MAX_VALUE;

            for (Magic magic : hand) {
                if (hospitalityDirector != null && !isSummon(magic)) {
                    continue;
                }

                int cost = spellStats.manaCost(magic);
                if (cost > mana || cost >= BotSpellStats.UNKNOWN_MANA_COST) {
                    continue;
                }
                hasAffordableCard = true;

                // 이번 소환이 놓인 뒤에도 봇 보드가 플레이어 보드보다 약해야 한다. 지킬 수 없으면
                // 소환하지 않는다 - 데드라인이 있어도 이 규칙은 깨지 않는다. 플레이어보다 센 것을
                // 한 번 내놓는 순간 접대는 실패하고, 그건 잠깐 조용한 것보다 나쁘다.
                if (cost >= manaBudget) {
                    continue;
                }

                if (cost < cheapestCost) {
                    cheapestCost = cost;
                    cheapestAffordable = magic;
                }

                buildPlay(magic, cost, aggression, spellStats, threats, enemies, playerPos, botSide, random)
                        .ifPresent(plays::add);
            }

            if (!plays.isEmpty()) {
                ScoredPlay chosen = choosePlay(plays);
                log.debug("[Bot {}] Chose {} at {} (score={}, cost={}, pressure={})",
                        botSide, chosen.magicId(), chosen.target(), chosen.score(), chosen.cost(), threats.pressure());
                return chosen.toDecision();
            }

            // Holding for mana is the right play for a bot that is trying to win. For one that has
            // been quiet too long it is the wrong one: the player reads the pause, not the reason
            // for it. A card is one magic now, so there is nothing cheaper to cycle into when
            // nothing in hand is affordable - the only thing left is to wait.
            if (!hasAffordableCard) {
                log.debug("[Bot {}] Waiting for mana; nothing in hand is affordable. mana={}", botSide, mana);
                return null;
            }

            // Something is affordable but nothing scored a target, which is what happens to an
            // offensive card with an empty field. Overdue, the bot spends the cheapest one anyway.
            if (overdue && cheapestAffordable != null) {
                Vector3 target = PlacementPlanner.plan(
                        playerPos, spellStats.castRange(cheapestAffordable), botSide, threats, random);
                log.debug("[Bot {}] Chose to cycle card: {} at {}", botSide, cheapestAffordable.name, target);
                return new InputDecision(
                        cheapestAffordable.id, target, CYCLE_RULE,
                        "No card scored a target; cycling the cheapest affordable card "
                                + cheapestAffordable.name + ".");
            }

            log.trace("[Bot {}] No valid actions found this tick", botSide);
        } catch (Exception e) {
            log.error("[Bot {}] Bot think error", botSide, e);
        }
        return null;
    }

    /** The hand, as the magics it names. A card whose magic is unknown is simply not playable. */
    private static List<Magic> resolveHand(DatabaseMagicParser dbParser, List<Long> cardList) {
        List<Magic> hand = new ArrayList<>();
        for (Long magicId : new LinkedHashSet<>(cardList)) {
            Magic magic = dbParser.getMagic(magicId);
            if (magic != null) {
                hand.add(magic);
            }
        }
        return hand;
    }

    private Optional<ScoredPlay> buildPlay(Magic magic,
                                           int cost,
                                           double aggression,
                                           BotSpellStats spellStats,
                                           ThreatAssessment threats,
                                           List<BotVisibleObject> enemies,
                                           Vector3 playerPos,
                                           Master botSide,
                                           Random random) {
        double counterValue = counterValue(magic, enemies, aggression);
        double castRange = spellStats.castRange(magic);

        if (!isSummon(magic)) {
            return BlastTargetSelector.bestImpact(
                            threats.threats(),
                            playerPos,
                            castRange,
                            spellStats.blastRadius(magic),
                            spellStats.damagePerTarget(magic))
                    .map(impact -> {
                        double value = impact.expectedDamage()
                                + KILL_TEMPO_BONUS * impact.lethalCount()
                                + counterValue;
                        return scored(magic, impact.center(), cost, value / cost, VALUE_RULE,
                                "Best offensive value covers " + impact.coveredCount()
                                        + " target(s) with expected damage " + impact.expectedDamage() + ".",
                                random);
                    });
        }

        double value = PLACEMENT_VALUE_PER_CAST
                * (1 + DEFENSE_URGENCY_WEIGHT * threats.pressure())
                + counterValue;
        Vector3 target = PlacementPlanner.plan(playerPos, castRange, botSide, threats, random);
        return Optional.of(scored(magic, target, cost, value / cost, VALUE_RULE,
                "Best placement value for current defensive pressure " + threats.pressure() + ".", random));
    }

    /**
     * A magic that leaves a body on the field builds the bot's own board; everything else lands on
     * a target. {@code ObjectSummoningMagic} is the same declaration the board pricing reads, so
     * the two never disagree about what a summon is.
     */
    private static boolean isSummon(Magic magic) {
        return magic instanceof ObjectSummoningMagic;
    }

    /**
     * The counter term, in whichever direction this persona's aggression asks for.
     *
     * <p>A positive aggression scores how much the magic beats the enemy board, which is what
     * every ordinary bot wants. A negative one scores how much the enemy board beats the magic
     * and adds it with the same sign, so the play the enemy answers best ranks highest. That is
     * the hospitality bot: it keeps committing real units, and the units it commits lose to what
     * is already on the field. Simply ranking low on the attacking direction would not do it -
     * "does not beat them" is satisfied by any irrelevant play, including standing still.
     */
    private double counterValue(Magic magic, List<BotVisibleObject> enemies, double aggression) {
        if (aggression == 0.0) {
            return 0.0;
        }
        double matchup = aggression > 0.0
                ? counterEvaluator.evaluate(magic, enemies)
                : counterEvaluator.evaluateVulnerability(magic, enemies);
        return matchup * Math.abs(aggression) * COUNTER_WEIGHT;
    }

    /** Every candidate already carries exactly one tier-scaled random perturbation. */
    private ScoredPlay choosePlay(List<ScoredPlay> plays) {
        return plays.stream()
                .max(Comparator.comparingDouble(ScoredPlay::noisyScore))
                .orElse(plays.getFirst());
    }

    static double noiseAmplitude(BotTier tier) {
        return switch (tier) {
            case HOSPITALITY, ELITE -> 0.0;
            case INTRO -> 0.50;
            case BEGINNER -> 0.30;
            case INTERMEDIATE -> 0.18;
            case ADVANCED -> 0.08;
        };
    }

    private ScoredPlay scored(Magic magic,
                              Vector3 target,
                              int cost,
                              double score,
                              String ruleId,
                              String reason,
                              Random random) {
        double amplitude = noiseAmplitude(persona.tier());
        double noisyScore = score * (1.0 + (random.nextDouble() * 2.0 - 1.0) * amplitude);
        return new ScoredPlay(magic.id, target, cost, score, noisyScore, ruleId, reason);
    }

    /**
     * The counter aggression to score this tick with. Ordinary personas use the value stored on
     * them; the hospitality bot has its value chosen per cast from the state of the board, which is
     * how "lose to what is already summoned" is expressed without ever skipping a cast.
     */
    private double aggression(BotEye botEye, ThreatAssessment threats, Master botSide) {
        if (hospitalityDirector == null) {
            return persona.normalizedCounterAggression();
        }
        Master enemySide = BotSideUtil.getEnemySide(botSide);
        int playerUnits = countUnits(botEye, enemySide);
        int botUnits = countUnits(botEye, botSide);
        double aggression = hospitalityDirector.aggression(playerUnits, botUnits, threats.pressure());
        log.debug("[Bot {}] Hospitality aggression={} (player units={}, own units={}, pressure={})",
                botSide, aggression, playerUnits, botUnits, threats.pressure());
        return aggression;
    }

    /** Bodies on the field for one side. The player core is not a body anyone summoned. */
    private static int countUnits(BotEye botEye, Master side) {
        int count = 0;
        for (BotVisibleObject object : botEye.gameObjectList()) {
            if (object.master() == side && object.type() != PrefabType.Player && object.targetable()) {
                count++;
            }
        }
        return count;
    }

    private Optional<ScoredPlay> findSeedSpiritCombo(List<Magic> hand,
                                                      int mana,
                                                      BotSpellStats spellStats,
                                                      List<BotVisibleObject> objects,
                                                      Vector3 playerPos,
                                                      Master botSide,
                                                      Random random) {
        List<BotVisibleObject> seedSpirits = objects.stream()
                .filter(object -> object.master() == botSide)
                .filter(BotVisibleObject::targetable)
                .filter(object -> object.type() == PrefabType.SeedSpirit)
                .toList();
        if (seedSpirits.isEmpty()) {
            return Optional.empty();
        }

        List<ScoredPlay> candidates = new ArrayList<>();
        for (Magic magic : hand) {
            if (!(magic instanceof VineTossMagic) && !(magic instanceof OvergrowthMagic)) {
                continue;
            }
            int cost = spellStats.manaCost(magic);
            if (cost > mana) {
                continue;
            }
            double castRange = spellStats.castRange(magic);
            for (BotVisibleObject seedSpirit : seedSpirits) {
                if (seedSpirit.position().distance(playerPos) > castRange) {
                    continue;
                }
                String magicName = magic.getClass().getSimpleName();
                candidates.add(scored(magic, seedSpirit.position(), cost, 1.0 / cost, SEED_SPIRIT_RULE,
                        magicName + " targets allied SeedSpirit " + seedSpirit.id() + ".", random));
            }
        }
        return candidates.stream().max(Comparator.comparingDouble(ScoredPlay::noisyScore));
    }

    private Optional<ScoredPlay> findMobClusterCombo(List<Magic> hand,
                                                      int mana,
                                                      BotSpellStats spellStats,
                                                      List<BotVisibleObject> objects,
                                                      Vector3 playerPos,
                                                      Master enemySide,
                                                      Random random) {
        List<BotVisibleObject> enemyMobs = objects.stream()
                .filter(object -> object.master() == enemySide && object.mob() && object.targetable())
                .toList();
        if (enemyMobs.size() < COMBO_CLUSTER_MIN_MOBS) {
            return Optional.empty();
        }

        List<ScoredPlay> candidates = new ArrayList<>();
        for (Magic magic : hand) {
            if (!(magic instanceof AbstractExplosionMagic)) {
                continue;
            }
            int cost = spellStats.manaCost(magic);
            if (cost > mana) {
                continue;
            }
            findBestMobCluster(enemyMobs, playerPos, spellStats.castRange(magic)).ifPresent(cluster ->
                    candidates.add(scored(magic, cluster.center(), cost, (double) cluster.count() / cost,
                            MOB_CLUSTER_RULE,
                            magic.getClass().getSimpleName() + " targets a cluster of "
                                    + cluster.count() + " enemy mobs.", random)));
        }
        return candidates.stream().max(Comparator.comparingDouble(ScoredPlay::noisyScore));
    }

    private static Optional<MobCluster> findBestMobCluster(List<BotVisibleObject> mobs,
                                                            Vector3 playerPos,
                                                            double castRange) {
        MobCluster best = null;
        for (BotVisibleObject anchor : mobs) {
            List<BotVisibleObject> cluster = mobs.stream()
                    .filter(mob -> mob.position().distance(anchor.position()) <= COMBO_CLUSTER_RADIUS)
                    .toList();
            if (cluster.size() < COMBO_CLUSTER_MIN_MOBS) {
                continue;
            }
            Vector3 initialCenter = averagePosition(cluster);
            cluster = mobs.stream()
                    .filter(mob -> mob.position().distance(initialCenter) <= COMBO_CLUSTER_RADIUS)
                    .toList();
            if (cluster.size() < COMBO_CLUSTER_MIN_MOBS) {
                continue;
            }
            Vector3 center = averagePosition(cluster);
            if (center.distance(playerPos) > castRange) {
                continue;
            }
            MobCluster candidate = new MobCluster(center, cluster.size());
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

    private record MobCluster(Vector3 center, int count) {}

    private record ScoredPlay(long magicId,
                              Vector3 target,
                              int cost,
                              double score,
                              double noisyScore,
                              String ruleId,
                              String reason) {
        private InputDecision toDecision() {
            return new InputDecision(magicId, target, ruleId, reason);
        }
    }
}
