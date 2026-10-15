package com.wordonline.server.bot.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
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
    private final boolean cardMenu;
    private final MagicDescriptions descriptions;

    /** {@code counterEvaluator} may be null, in which case every magic is described as neutral. */
    public LlmCandidateBuilder(LongFunction<Magic> magicLookup, BotCounterEvaluator counterEvaluator) {
        this(magicLookup, counterEvaluator, ThreadLocalRandom::current);
    }

    LlmCandidateBuilder(LongFunction<Magic> magicLookup, BotCounterEvaluator counterEvaluator,
                        Supplier<Random> random) {
        this(magicLookup, counterEvaluator, random, false, MagicDescriptions.bundled());
    }

    LlmCandidateBuilder(LongFunction<Magic> magicLookup, BotCounterEvaluator counterEvaluator,
                        Supplier<Random> random, boolean cardMenu, MagicDescriptions descriptions) {
        this.magicLookup = magicLookup;
        this.counterEvaluator = counterEvaluator;
        this.random = random;
        this.cardMenu = cardMenu;
        this.descriptions = descriptions;
    }

    /**
     * A builder whose menu is {@link #buildCards one option per affordable card}, for the CARD mode,
     * where the model decides what to play and code only decides where.
     */
    public static LlmCandidateBuilder cards(LongFunction<Magic> magicLookup, BotCounterEvaluator counterEvaluator) {
        return new LlmCandidateBuilder(magicLookup, counterEvaluator, ThreadLocalRandom::current, true,
                MagicDescriptions.bundled());
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
        if (cardMenu) {
            return buildCards(botEye, parameters, botSide);
        }
        Turn turn = new Turn(botEye, parameters, botSide);

        List<Draft> drafts = new ArrayList<>();
        if (standard != null) {
            // The rule brain never picks what it cannot pay for; an older or foreign decision might.
            Magic magic = turn.affordable.stream()
                    .filter(candidate -> candidate.id == standard.magicId())
                    .findFirst()
                    .orElse(null);
            if (magic != null && standard.target() != null) {
                Draft plain = turn.draft(magic, standard.target());
                drafts.add(new Draft(magic, standard.target(),
                        STANDARD_TACTIC + " (what the rule bot would do: " + shorten(standard.reason()) + "): "
                                + plain.description(), plain.impact()));
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
                    draft.magic() instanceof ObjectSummoningMagic, draft.impact().baseDamage(),
                    draft.impact().unitDamage()));
        }
        return new LlmCandidates(plays, turn.waitDescription(), turn.state());
    }

    /**
     * One option per affordable card, keyed by the magic's name, plus waiting. Each option says what
     * the card does in the words the player reads (the magic book text), what the unit it leaves
     * does, and what the card would do on this board right now. No option is marked as preferred:
     * deciding what to play is the model's job. The point is picked by code, the same way the rule
     * brain picks it, and picked again from the current snapshot when the answer is cast. A damage
     * spell whose best target hits no unit and not the base is left out, since casting it would hit nothing.
     */
    public LlmCandidates buildCards(BotEye botEye, Parameters parameters, Master botSide) {
        Turn turn = new Turn(botEye, parameters, botSide);
        Map<String, LlmCandidates.Play> plays = new LinkedHashMap<>();
        for (Magic magic : turn.affordable) {
            if (plays.size() >= MAX_PLAYS) {
                break;
            }
            int cost = turn.stats.manaCost(magic);
            StringBuilder text = new StringBuilder(magic.name);
            Vector3 target;
            Impact impact = Impact.NONE;
            boolean summon = magic instanceof ObjectSummoningMagic;
            if (magic instanceof ObjectSummoningMagic summoning) {
                target = PlacementPlanner.plan(
                        turn.ownBase, turn.stats.castRange(magic), botSide, turn.threats, random.get());
                text.append(" (summon, ").append(cost).append(" mana): ").append(cardText(magic));
                PrefabType unit = summoning.summonedPrefab();
                text.append(" Puts ").append(summoning.summonedQuantity()).append(' ').append(unit)
                        .append(". It ").append(turn.guide.role(unit)).append(". ");
                text.append("Right now: ").append(turn.summonNow(unit, target)).append('.');
            } else {
                Optional<BlastTargetSelector.Impact> best = BlastTargetSelector.bestImpact(
                        turn.threats.threats(), turn.ownBase, turn.stats.castRange(magic),
                        turn.stats.blastRadius(magic), turn.stats.damagePerTarget(magic));
                if (best.isEmpty()) {
                    continue;
                }
                target = best.get().center();
                impact = turn.impact(target, turn.stats.blastRadius(magic), turn.stats.damagePerTarget(magic));
                if (impact.hit().isEmpty() && !impact.baseHit()) {
                    continue;
                }
                text.append(" (spell, ").append(cost).append(" mana, range ")
                        .append(number(turn.stats.castRange(magic))).append("): ").append(cardText(magic))
                        .append(" Right now its best target ");
                turn.appendImpact(text, impact, cost);
                text.append('.');
            }
            plays.put(magic.name, new LlmCandidates.Play(magic.id, magic.name, cost, new Vector3(target),
                    text.toString(), summon, impact.baseDamage(), impact.unitDamage()));
        }
        String wait = plays.isEmpty() ? null : turn.keepManaDescription();
        return new LlmCandidates(plays, wait, turn.state());
    }

    private String cardText(Magic magic) {
        String text = descriptions.describe(magic.name);
        if (text == null) {
            return "";
        }
        return text.endsWith(".") ? text : text + ".";
    }

    /** What a damage spell lands where it is aimed; all zero for a summon. */
    private record Impact(List<String> hit, double unitDamage, double baseDamage, int kills, boolean baseHit) {
        static final Impact NONE = new Impact(List.of(), 0, 0, 0, false);
    }

    private record Draft(Magic magic, Vector3 target, String description, Impact impact) {
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
        final UnitGuide guide;
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
            this.guide = new UnitGuide(parameters);
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
            Impact impact = magic instanceof ObjectSummoningMagic
                    ? Impact.NONE
                    : impact(target, stats.blastRadius(magic), stats.damagePerTarget(magic));
            return new Draft(magic, target, describe(magic, target, impact), impact);
        }

        private String describe(Magic magic, Vector3 target, Impact impact) {
            int cost = stats.manaCost(magic);
            String where = " at " + coordinates(target) + ", " + number(target.distance(ownBase)) + " from our base";
            StringBuilder text = new StringBuilder(magic.name);
            if (magic instanceof ObjectSummoningMagic summoning) {
                text.append(" (summon, cost ").append(cost).append(')').append(where).append(": puts ")
                        .append(summoning.summonedQuantity()).append(' ').append(summoning.summonedPrefab())
                        .append(summoning.summonsAirborne() ? " (flying)" : "")
                        .append(guide.stats(summoning.summonedPrefab()).known()
                                ? " [" + guide.describe(summoning.summonedPrefab()) + "]" : "")
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
                appendImpact(text, impact, cost);
                text.append(". ");
            }
            text.append(counter(magic));
            return text.toString();
        }

        Impact impact(Vector3 target, double radius, double damage) {
            List<String> hit = new ArrayList<>();
            double unitDamage = 0;
            double baseDamage = 0;
            int kills = 0;
            boolean baseHit = false;
            for (EnemyThreat threat : threats.threats()) {
                if (threat.position().distance(target) > radius) {
                    continue;
                }
                if (threat.playerCore()) {
                    baseHit = true;
                    baseDamage += Math.min(damage, threat.hp());
                    continue;
                }
                // Projectiles in flight count as threats for the rule brain but are not units to
                // the reader; they leave the field on their own.
                BotVisibleObject object = objects.get(threat.objectId());
                if (object == null || !object.mob()) {
                    continue;
                }
                hit.add(threatName(object));
                unitDamage += Math.min(damage, threat.hp());
                if (damage >= threat.hp()) {
                    kills++;
                }
            }
            return new Impact(hit, unitDamage, baseDamage, kills, baseHit);
        }

        void appendImpact(StringBuilder text, Impact impact, int cost) {
            List<String> hit = impact.hit();
            if (hit.isEmpty() && !impact.baseHit()) {
                text.append("hits no enemy unit");
                return;
            }
            if (!hit.isEmpty()) {
                text.append("hits ").append(hit.size()).append(" enemy unit").append(hit.size() == 1 ? "" : "s")
                        .append(" (").append(String.join(", ", hit)).append(')');
            }
            if (impact.baseHit()) {
                text.append(hit.isEmpty() ? "hits the enemy base" : " and the enemy base");
            }
            double total = impact.unitDamage() + impact.baseDamage();
            text.append(" for ").append(number(total)).append(" total damage (")
                    .append(number(cost > 0 ? total / cost : total)).append(" per mana), kills ")
                    .append(impact.kills());
        }

        long copiesInHand(Magic magic) {
            return eye.cardList().stream().filter(id -> id != null && id == magic.id).count();
        }

        /** Where a summon would stand and which enemies are near that spot. */
        String summonNow(PrefabType unit, Vector3 spot) {
            StringBuilder text = new StringBuilder("it would be placed ")
                    .append(number(spot.distance(ownBase))).append(" from our base");
            if (enemyUnits.isEmpty()) {
                return text.append(", and no enemy unit is on the field").toString();
            }
            BotVisibleObject nearest = enemyUnits.stream()
                    .min(Comparator.comparingDouble(enemy -> enemy.position().distance(spot)))
                    .orElseThrow();
            text.append("; the nearest enemy unit to that spot is a ").append(typeName(nearest)).append(' ')
                    .append(number(nearest.position().distance(spot))).append(" away");
            double reach = guide.reach(unit);
            if (guide.stats(unit).speed() <= 0 && reach > 0) {
                long inReach = enemyUnits.stream()
                        .filter(enemy -> enemy.position().distance(spot) <= reach)
                        .count();
                text.append("; ").append(inReach).append(" enemy unit").append(inReach == 1 ? " is" : "s are")
                        .append(" within its reach of ").append(number(reach)).append(" now");
            }
            return text.toString();
        }

        /** Waiting, with the cards it would save for. */
        String keepManaDescription() {
            List<String> later = hand.stream()
                    .filter(magic -> stats.manaCost(magic) > eye.mana() && stats.manaCost(magic) < UNPRICED_COST)
                    .map(magic -> magic.name + " (" + stats.manaCost(magic) + " mana)")
                    .toList();
            boolean full = eye.cardList().size() >= PlayerData.MAX_CARD_NUM;
            return "Cast nothing now and keep the mana; mana is " + eye.mana()
                    + (later.isEmpty() ? ", and every card in hand is already affordable."
                            : ", and these cards in hand are not affordable yet: " + String.join(", ", later) + ".")
                    + (full ? " The hand is full, so no new card is drawn while nothing is played." : "");
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
                card.put("copies", copiesInHand(magic));
                return card;
            }).toList());
            state.put("handSlotsUsed", eye.cardList().size() + " of " + PlayerData.MAX_CARD_NUM);
            state.put("enemyUnits", summarise(enemyUnits, MAX_STATE_ENEMIES, ownBase, "toOurBase", guide));
            state.put("ourUnits", summarise(ownUnits, MAX_STATE_ALLIES, enemyBase, "toEnemyBase", guide));
            state.put("unitGuide", unitGuide());
            return state;
        }

        /** Every unit type on the field or summonable from hand, with what it does. */
        private Map<String, String> unitGuide() {
            Map<String, String> types = new TreeMap<>();
            for (BotVisibleObject unit : enemyUnits) {
                types.computeIfAbsent(typeName(unit), name -> guide.describe(unit.type()));
            }
            for (BotVisibleObject unit : ownUnits) {
                types.computeIfAbsent(typeName(unit), name -> guide.describe(unit.type()));
            }
            for (Magic magic : hand) {
                if (magic instanceof ObjectSummoningMagic summoning && summoning.summonedPrefab() != null) {
                    types.computeIfAbsent(summoning.summonedPrefab().name(),
                            name -> guide.describe(summoning.summonedPrefab()));
                }
            }
            return types;
        }

        /** "WindSpirit (hits our base for 300 in 2.3s)" for an enemy unit that is under a blast. */
        private String threatName(BotVisibleObject object) {
            UnitGuide.Stats unit = guide.stats(object.type());
            Double eta = unit.secondsToReach(object.position().distance(ownBase));
            if (unit.damage() <= 0 || eta == null) {
                return typeName(object);
            }
            return typeName(object) + " (hits our base for " + number(unit.damage()) + " in "
                    + number(eta) + "s)";
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
                                                       String distanceName, UnitGuide guide) {
        return objects.stream()
                .limit(limit)
                .map(object -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    double distance = object.position().distance(from);
                    row.put("type", typeName(object));
                    row.put("hp", object.hp());
                    row.put(distanceName, round(distance));
                    Double eta = guide.stats(object.type()).secondsToReach(distance);
                    if (eta != null) {
                        row.put("secondsToHitBase", round(eta));
                    }
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
