package com.wordonline.server.bot.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.bot.BlastTargetSelector;
import com.wordonline.server.game.domain.bot.BotBrain;
import com.wordonline.server.game.domain.bot.BotEye;
import com.wordonline.server.game.domain.bot.BotSideUtil;
import com.wordonline.server.game.domain.bot.BotSpellStats;
import com.wordonline.server.game.domain.bot.BotVisibleObject;
import com.wordonline.server.game.domain.bot.EnemyThreat;
import com.wordonline.server.game.domain.bot.PlacementPlanner;
import com.wordonline.server.game.domain.bot.ThreatAssessment;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;

/**
 * Turns one bot snapshot into the complete plays and the state an LLM request carries.
 *
 * <p>A play is one affordable magic and the point it lands on, priced and aimed with the same
 * helpers the rule brain uses: {@link ThreatAssessment} for what is coming, {@link BotSpellStats}
 * for cost, range and damage, {@link BlastTargetSelector} for where a damage spell converts the
 * most damage, and {@link PlacementPlanner} for where a summon blocks the contested lane. A damage
 * spell also gets one play on the most advanced enemy unit and one on the enemy base when those
 * are not already under its best blast. The rule brain's own pick, when given, comes first and is
 * marked as the standard tactic.
 *
 * <p>Each description says in words what the play does to the board, how it matches up against the
 * enemy units on the field, and how far from the bot's base it lands, because the provider reads
 * only the descriptions and the state. Runs on the bot executor and reads only the snapshot and the
 * cached parameters.
 */
public final class LlmCandidateBuilder {

    /** Upper bound on plays, so the prompt stays small and the choice stays easy. */
    public static final int MAX_PLAYS = 16;
    /** Enemy units listed in the state. */
    static final int MAX_STATE_ENEMIES = 12;
    /** Own units listed in the state. */
    static final int MAX_STATE_ALLIES = 8;
    /** Pressure at or above which waiting is not offered. */
    static final double URGENT_PRESSURE = 0.3;
    /** An enemy unit at most this far from the bot's base makes the turn urgent. */
    static final double URGENT_DISTANCE = 6.0;
    /** Two plays of the same magic whose targets are this close are the same play. */
    static final double SAME_TARGET_DISTANCE = 1.0;
    /** Net counter score at or above which a magic is strong against the field, and its negative weak. */
    static final double COUNTER_THRESHOLD = 1.0;
    /** Mana cost {@link BotSpellStats} reports for a magic with no price; such a magic is never offered. */
    static final int UNPRICED_COST = 9_999;
    static final String STANDARD_TACTIC = "STANDARD TACTIC";
    /** Longest rule-brain reason copied into the standard play's description. */
    private static final int MAX_STANDARD_REASON = 90;

    private final LongFunction<Magic> magicLookup;
    private final BotCounterEvaluator counterEvaluator;
    private final Supplier<Random> random;

    /** {@code counterEvaluator} may be null, in which case every magic is described as neutral. */
    public LlmCandidateBuilder(LongFunction<Magic> magicLookup, BotCounterEvaluator counterEvaluator) {
        this(magicLookup, counterEvaluator, ThreadLocalRandom::current);
    }

    LlmCandidateBuilder(LongFunction<Magic> magicLookup, BotCounterEvaluator counterEvaluator,
                        Supplier<Random> random) {
        this.magicLookup = magicLookup;
        this.counterEvaluator = counterEvaluator;
        this.random = random;
    }

    public LlmCandidates build(BotEye botEye, Parameters parameters, Master botSide) {
        return build(botEye, parameters, botSide, null);
    }

    /**
     * @param standard the rule brain's decision for the same snapshot, or null; offered first and
     *                 marked as the standard tactic
     */
    public LlmCandidates build(BotEye botEye, Parameters parameters, Master botSide,
                               BotBrain.InputDecision standard) {
        Turn turn = new Turn(botEye, parameters, botSide);

        List<Draft> drafts = new ArrayList<>();
        if (standard != null) {
            // The rule brain never picks what it cannot pay for; an older or foreign decision might.
            Magic magic = turn.affordable.stream()
                    .filter(candidate -> candidate.id == standard.magicId())
                    .findFirst()
                    .orElse(null);
            if (magic != null && standard.target() != null) {
                drafts.add(new Draft(magic, standard.target(),
                        STANDARD_TACTIC + " (what the rule bot would do: " + shorten(standard.reason()) + "): "
                                + turn.describe(magic, standard.target())));
            }
        }
        List<Draft> alternatives = new ArrayList<>();
        for (Magic magic : turn.affordable) {
            if (magic instanceof ObjectSummoningMagic) {
                Vector3 target = PlacementPlanner.plan(
                        turn.ownBase, turn.stats.castRange(magic), botSide, turn.threats, random.get());
                drafts.add(turn.draft(magic, target));
                continue;
            }
            Optional<BlastTargetSelector.Impact> best = BlastTargetSelector.bestImpact(
                    turn.threats.threats(), turn.ownBase, turn.stats.castRange(magic),
                    turn.stats.blastRadius(magic), turn.stats.damagePerTarget(magic));
            if (best.isEmpty()) {
                continue;
            }
            Vector3 center = best.get().center();
            drafts.add(turn.draft(magic, center));
            for (Vector3 extra : turn.extraTargets(magic, center)) {
                alternatives.add(turn.draft(magic, extra));
            }
        }
        drafts.addAll(alternatives);

        Map<String, LlmCandidates.Play> plays = new LinkedHashMap<>();
        List<Draft> kept = new ArrayList<>();
        for (Draft draft : drafts) {
            if (plays.size() >= MAX_PLAYS) {
                break;
            }
            if (kept.stream().anyMatch(other -> other.sameAs(draft))) {
                continue;
            }
            kept.add(draft);
            plays.put("p" + plays.size(), new LlmCandidates.Play(draft.magic().id, draft.magic().name,
                    turn.stats.manaCost(draft.magic()), new Vector3(draft.target()), draft.description(),
                    draft.magic() instanceof ObjectSummoningMagic));
        }
        return new LlmCandidates(plays, turn.waitDescription(), turn.state());
    }

    private record Draft(Magic magic, Vector3 target, String description) {
        boolean sameAs(Draft other) {
            return magic.id == other.magic.id && target.distance(other.target) <= SAME_TARGET_DISTANCE;
        }
    }

    /** Everything one build reads off the snapshot, computed once. */
    private final class Turn {
        final Master botSide;
        final Vector3 ownBase;
        final Vector3 enemyBase;
        final BotEye eye;
        final BotSpellStats stats;
        final ThreatAssessment threats;
        final List<BotVisibleObject> enemyUnits;
        final List<BotVisibleObject> ownUnits;
        final List<Magic> hand = new ArrayList<>();
        final List<Magic> affordable = new ArrayList<>();
        final Map<Long, String> counterText = new HashMap<>();
        final Map<Integer, BotVisibleObject> objects = new HashMap<>();

        Turn(BotEye eye, Parameters parameters, Master botSide) {
            this.eye = eye;
            this.botSide = botSide;
            Master enemySide = BotSideUtil.getEnemySide(botSide);
            this.ownBase = BotSideUtil.getPlayerPosition(botSide);
            this.enemyBase = BotSideUtil.getPlayerPosition(enemySide);
            this.stats = new BotSpellStats(parameters);
            this.threats = ThreatAssessment.observe(eye.gameObjectList(), enemySide, ownBase, eye.enemyPlayerHp());
            this.enemyUnits = units(eye, enemySide, ownBase);
            this.ownUnits = units(eye, botSide, enemyBase);
            eye.gameObjectList().forEach(object -> objects.putIfAbsent(object.id(), object));
            for (Long magicId : new LinkedHashSet<>(eye.cardList())) {
                Magic magic = magicId == null ? null : magicLookup.apply(magicId);
                if (magic == null) {
                    continue;
                }
                hand.add(magic);
                int cost = stats.manaCost(magic);
                if (cost <= eye.mana() && cost < UNPRICED_COST) {
                    affordable.add(magic);
                }
            }
        }

        Draft draft(Magic magic, Vector3 target) {
            return new Draft(magic, target, describe(magic, target));
        }

        String describe(Magic magic, Vector3 target) {
            int cost = stats.manaCost(magic);
            String where = " at " + coordinates(target) + ", " + number(target.distance(ownBase)) + " from our base";
            StringBuilder text = new StringBuilder(magic.name);
            if (magic instanceof ObjectSummoningMagic summoning) {
                text.append(" (summon, cost ").append(cost).append(')').append(where).append(": puts ")
                        .append(summoning.summonedQuantity()).append(' ').append(summoning.summonedPrefab())
                        .append(summoning.summonsAirborne() ? " (flying)" : "")
                        .append(" in lane z=").append(number(target.getZ())).append("; ");
                if (enemyUnits.isEmpty()) {
                    text.append("no enemy unit is on the field, so it starts an advance");
                } else {
                    BotVisibleObject nearest = enemyUnits.getFirst();
                    text.append("blocks the nearest enemy ").append(typeName(nearest)).append(" (")
                            .append(number(nearest.position().distance(ownBase))).append(" from our base)");
                }
                text.append("; pressure on our base is ").append(pressureLabel()).append(". ");
            } else {
                double radius = stats.blastRadius(magic);
                double damage = stats.damagePerTarget(magic);
                text.append(" (damage spell, cost ").append(cost).append(", ").append(number(damage))
                        .append(" damage in radius ").append(number(radius)).append(')').append(where).append(": ");
                appendImpact(text, target, radius, damage, cost);
                text.append(". ");
            }
            text.append(counter(magic));
            return text.toString();
        }

        private void appendImpact(StringBuilder text, Vector3 target, double radius, double damage, int cost) {
            List<String> hit = new ArrayList<>();
            double total = 0;
            int kills = 0;
            boolean baseHit = false;
            for (EnemyThreat threat : threats.threats()) {
                if (threat.position().distance(target) > radius) {
                    continue;
                }
                if (threat.playerCore()) {
                    baseHit = true;
                    total += Math.min(damage, threat.hp());
                    continue;
                }
                // Projectiles in flight count as threats for the rule brain but are not units to
                // the reader; they leave the field on their own.
                BotVisibleObject object = objects.get(threat.objectId());
                if (object == null || !object.mob()) {
                    continue;
                }
                hit.add(typeName(object));
                total += Math.min(damage, threat.hp());
                if (damage >= threat.hp()) {
                    kills++;
                }
            }
            if (hit.isEmpty() && !baseHit) {
                text.append("hits no enemy unit");
                return;
            }
            if (!hit.isEmpty()) {
                text.append("hits ").append(hit.size()).append(" enemy unit").append(hit.size() == 1 ? "" : "s")
                        .append(" (").append(String.join(", ", hit)).append(')');
            }
            if (baseHit) {
                text.append(hit.isEmpty() ? "hits the enemy base" : " and the enemy base");
            }
            text.append(" for ").append(number(total)).append(" total damage (")
                    .append(number(cost > 0 ? total / cost : total)).append(" per mana), kills ").append(kills);
        }

        /** The most advanced enemy unit and the enemy base, when in range and not under the best blast. */
        List<Vector3> extraTargets(Magic magic, Vector3 center) {
            double range = stats.castRange(magic);
            double radius = stats.blastRadius(magic);
            List<Vector3> extras = new ArrayList<>();
            enemyUnits.stream()
                    .filter(unit -> unit.position().distance(ownBase) <= range)
                    .findFirst()
                    .filter(unit -> unit.position().distance(center) > radius)
                    .ifPresent(unit -> extras.add(unit.position()));
            threats.threats().stream()
                    .filter(EnemyThreat::playerCore)
                    .filter(threat -> threat.position().distance(ownBase) <= range)
                    .filter(threat -> threat.position().distance(center) > radius)
                    .findFirst()
                    .ifPresent(threat -> extras.add(threat.position()));
            return extras;
        }

        String counter(Magic magic) {
            return counterText.computeIfAbsent(magic.id, id -> {
                if (enemyUnits.isEmpty()) {
                    return "No enemy units on the field to counter.";
                }
                if (counterEvaluator == null) {
                    return "Neutral against the enemy units on the field.";
                }
                double net = counterEvaluator.evaluate(magic, enemyUnits)
                        - counterEvaluator.evaluateVulnerability(magic, enemyUnits);
                String label = net >= COUNTER_THRESHOLD ? "Strong" : net <= -COUNTER_THRESHOLD ? "Weak" : "Neutral";
                return label + " against the enemy units on the field (counter score "
                        + (net > 0 ? "+" : "") + number(net) + ").";
            });
        }

        boolean urgent() {
            return threats.pressure() >= URGENT_PRESSURE
                    || (!enemyUnits.isEmpty() && enemyUnits.getFirst().position().distance(ownBase) <= URGENT_DISTANCE);
        }

        /** Offered only when nothing is urgent and the dearest magic in hand is not affordable yet. */
        String waitDescription() {
            if (urgent()) {
                return null;
            }
            Magic dearest = hand.stream()
                    .filter(magic -> stats.manaCost(magic) < UNPRICED_COST)
                    .max(Comparator.comparingInt(stats::manaCost))
                    .orElse(null);
            if (dearest == null || stats.manaCost(dearest) <= eye.mana()) {
                return null;
            }
            String nearest = enemyUnits.isEmpty()
                    ? "no enemy unit is on the field"
                    : "the nearest enemy unit is " + number(enemyUnits.getFirst().position().distance(ownBase))
                            + " from our base";
            return "Cast nothing now and save mana for " + dearest.name + " (cost " + stats.manaCost(dearest)
                    + "), mana is " + eye.mana() + " now; nothing is urgent: " + nearest + ".";
        }

        Map<String, Object> state() {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("side", botSide == Master.LeftPlayer ? "left" : "right");
            state.put("ourHp", ownHp());
            state.put("enemyHp", eye.enemyPlayerHp());
            state.put("mana", eye.mana());
            state.put("pressure", round2(threats.pressure()));
            state.put("enemyNearOurBase", urgent());
            state.put("hand", hand.stream().map(magic -> {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("magic", magic.name);
                card.put("cost", stats.manaCost(magic));
                return card;
            }).toList());
            state.put("enemyUnits", summarise(enemyUnits, MAX_STATE_ENEMIES, ownBase, "toOurBase"));
            state.put("ourUnits", summarise(ownUnits, MAX_STATE_ALLIES, enemyBase, "toEnemyBase"));
            return state;
        }

        private Integer ownHp() {
            return eye.gameObjectList().stream()
                    .filter(object -> object.master() == botSide && object.type() == PrefabType.Player)
                    .map(BotVisibleObject::hp)
                    .findFirst()
                    .orElse(null);
        }

        private String pressureLabel() {
            double pressure = threats.pressure();
            String label = pressure >= 0.7 ? "high" : pressure >= URGENT_PRESSURE ? "medium" : "low";
            return label + " (" + round2(pressure) + ")";
        }

    }

    /** Living units of one side, nearest to {@code from} first; the player bodies are left out. */
    private static List<BotVisibleObject> units(BotEye botEye, Master side, Vector3 from) {
        return botEye.gameObjectList().stream()
                .filter(object -> object.master() == side)
                .filter(BotVisibleObject::targetable)
                .filter(BotVisibleObject::mob)
                .filter(object -> object.type() != PrefabType.Player)
                .sorted(Comparator.comparingDouble(object -> object.position().distance(from)))
                .toList();
    }

    private static List<Map<String, Object>> summarise(List<BotVisibleObject> objects, int limit, Vector3 from,
                                                       String distanceName) {
        return objects.stream()
                .limit(limit)
                .map(object -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", typeName(object));
                    row.put("hp", object.hp());
                    row.put(distanceName, round(object.position().distance(from)));
                    row.put("z", round(object.position().getZ()));
                    return row;
                })
                .toList();
    }

    private static String typeName(BotVisibleObject object) {
        return object.type() == null ? "Unknown" : object.type().name();
    }

    private static String shorten(String reason) {
        if (reason == null || reason.isBlank()) {
            return "no reason given";
        }
        String text = reason.strip().replaceAll("\\s+", " ");
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.length() <= MAX_STANDARD_REASON ? text : text.substring(0, MAX_STANDARD_REASON);
    }

    private static String coordinates(Vector3 position) {
        return "(" + number(position.getX()) + ", " + number(position.getZ()) + ")";
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String number(double value) {
        double rounded = round(value);
        return rounded == Math.rint(rounded)
                ? Long.toString((long) rounded)
                : String.format(Locale.ROOT, "%.1f", rounded);
    }
}
