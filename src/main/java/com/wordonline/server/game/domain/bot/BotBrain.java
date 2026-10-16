package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotPersona;
import com.wordonline.server.bot.domain.BotTier;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.PlayerData;
import com.wordonline.server.game.domain.magic.parser.MagicParser;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.service.bot.BotCounterEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Decides what the bot casts and where.
 *
 * <p>Every affordable card in hand becomes a candidate. Offensive cards are scored by the damage
 * the blast would actually convert at its best landing spot; placement cards are scored by how much
 * the bot needs a body on the field right now. Both are divided by the card's real mana cost, so
 * the choice is value per mana rather than "the biggest thing in hand".
 *
 * <p>How well each of those judgements is made depends on the bot's {@link BotSkill}: a
 * {@link BotCapability} switches on each refinement, and {@link PriorityRule}s that the skill knows
 * are tried before any of the scoring.
 */
@Slf4j
public class BotBrain {

    public record InputDecision(long magicId, Vector3 target, String ruleId, String reason) {}

    static final String VALUE_RULE = "score.best-value";
    static final String CYCLE_RULE = "cycle.low-utility";

    /**
     * Tried in this order; the first rule that finds a play wins. Finishing the game comes before
     * any combo.
     */
    private static final List<PriorityRule> PRIORITY_RULES = List.of(
            new LethalRule(),
            new SeedSpiritComboRule(),
            new MobClusterComboRule());

    /** Tempo value of removing one enemy from the field, on top of the damage itself. */
    static final double KILL_TEMPO_BONUS = 8.0;

    /**
     * Board value a placement card is worth. One card is one cast now, so it is a flat term. With
     * {@link BotCapability#SUMMON_STRENGTH} this is what the catalogue's average summon is worth.
     */
    static final double PLACEMENT_VALUE_PER_CAST = 6.0;

    /** How much full defensive pressure inflates the value of putting a body down. */
    static final double DEFENSE_URGENCY_WEIGHT = 1.5;

    /** Scales the tag-based counter score into the same range as the damage-based scores. */
    static final double COUNTER_WEIGHT = 3.0;

    /**
     * Rough time from the cast going out to the spell landing, on top of the persona's thinking
     * time. Spells do not describe their own delay in a way the bot can read, so this is a modest
     * allowance rather than a measurement.
     */
    static final double CAST_RESOLUTION_SECONDS = 0.25;

    /** Below this pressure the bot is calm enough to save mana instead of spending it. */
    static final double CALM_PRESSURE = 0.35;

    /** How much better per mana a card must be before it is worth waiting for. */
    static final double SAVE_MARGIN = 1.25;

    private final MagicParser magicParser;
    private final BotCounterEvaluator counterEvaluator;
    private final BotPersona persona;
    private final BotSkill skill;
    private final MotionTracker motionTracker = new MotionTracker();
    private final Supplier<List<Long>> deckCards;
    // Read once per match on the first think that needs it; the deck does not change.
    private volatile DeckProfile deckProfile;

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
        this(magicParser, counterEvaluator, persona, opponentNoviceProgress, BotSkill.forTier(persona.tier()));
    }

    /**
     * @param skill what this bot knows how to do; normally {@link BotSkill#forTier} of the persona
     */
    public BotBrain(MagicParser magicParser,
                    BotCounterEvaluator counterEvaluator,
                    BotPersona persona,
                    double opponentNoviceProgress,
                    BotSkill skill) {
        this(magicParser, counterEvaluator, persona, opponentNoviceProgress, skill, List::of);
    }

    /**
     * @param deckCards every card of the bot's deck, copies included, as the session dealt it; an
     *                  empty list when it is unknown
     */
    public BotBrain(MagicParser magicParser,
                    BotCounterEvaluator counterEvaluator,
                    BotPersona persona,
                    double opponentNoviceProgress,
                    BotSkill skill,
                    Supplier<List<Long>> deckCards) {
        this.deckCards = deckCards;
        this.magicParser = magicParser;
        this.counterEvaluator = counterEvaluator;
        this.persona = persona;
        this.skill = skill;
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
            // Watched before anything can return early, so the next think measures motion against
            // this frame rather than one several reactions old.
            Map<Integer, Vector3> velocities = skill.has(BotCapability.LEAD_TARGETING)
                    ? motionTracker.observe(botEye)
                    : Map.of();
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
            UnitCatalogue units = needsUnitCatalogue()
                    ? new UnitCatalogue(dbParser, spellStats, parameters)
                    : null;
            ThreatAssessment threats = ThreatAssessment.observe(
                    botEye.gameObjectList(), enemySide, playerPos, botEye.enemyPlayerHp(),
                    units != null && skill.has(BotCapability.THREAT_STRENGTH) ? units::relativeThreat : object -> 1.0,
                    skill.has(BotCapability.ALLY_COVER));
            ThreatAssessment aimedThreats = threats.ledBy(velocities, leadSeconds());
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

            ThinkContext context = new ThinkContext(
                    hand, mana, spellStats, botEye.gameObjectList(), playerPos, botSide, enemySide,
                    threats, skill, random);

            // Tutorial hospitality rules only permit summons. Priority rules are combat tactics,
            // so they run for ordinary tiers and still precede the value scorer.
            if (hospitalityDirector == null) {
                for (PriorityRule rule : PRIORITY_RULES) {
                    if (!skill.has(rule.capability())) {
                        continue;
                    }
                    Optional<ScoredPlay> play = rule.find(context);
                    if (play.isPresent()) {
                        ScoredPlay chosen = play.get();
                        log.debug("[Bot {}] Chose priority rule {}: {}", botSide, chosen.ruleId(), chosen.reason());
                        return chosen.toDecision();
                    }
                }
            }

            int maxMana = maxMana(parameters);
            if (hospitalityDirector == null && skill.has(BotCapability.GAME_PLAN) && !overdue
                    && threats.pressure() < CALM_PRESSURE) {
                GamePlanner.Plan plan = GamePlanner.plan(context, maxMana, parameters,
                        deckProfile(dbParser, spellStats),
                        magic -> PlacementPlanner.plan(playerPos, spellStats.castRange(magic), botSide, threats,
                                placementOptions(magic, units), random));
                if (plan.saving()) {
                    log.debug("[Bot {}] Saving mana for a wave of fighters. mana={}", botSide, mana);
                    return null;
                }
                if (plan.play().isPresent()) {
                    ScoredPlay chosen = plan.play().get();
                    log.debug("[Bot {}] Chose plan {}: {}", botSide, chosen.ruleId(), chosen.reason());
                    return chosen.toDecision();
                }
            }

            List<ScoredPlay> plays = new ArrayList<>();
            List<ScoredPlay> worthSavingFor = new ArrayList<>();
            boolean hasAffordableCard = false;
            Magic cheapestAffordable = null;
            int cheapestCost = Integer.MAX_VALUE;

            for (Magic magic : hand) {
                if (hospitalityDirector != null && !isSummon(magic)) {
                    continue;
                }

                int cost = spellStats.manaCost(magic);
                if (cost >= BotSpellStats.UNKNOWN_MANA_COST) {
                    continue;
                }
                if (cost > mana) {
                    // Not castable now, but a card the bot will be able to pay for is worth
                    // knowing about when deciding whether to spend what it has.
                    if (skill.has(BotCapability.MANA_DISCIPLINE) && cost <= maxMana) {
                        buildPlay(magic, cost, aggression, context, aimedThreats, units, enemies)
                                .ifPresent(worthSavingFor::add);
                    }
                    continue;
                }
                // A summon with nothing to do is mana for nothing, unless the hand is full or mana is
                // about to overflow anyway: then casting it frees its slot for a card that does.
                if (skill.has(BotCapability.SUMMON_RELEVANCE) && isSummon(magic)
                        && !SummonRelevance.relevant(magic, context, parameters)
                        && !handFull(cardList)
                        && !(maxMana > 0 && mana >= maxMana - GamePlanner.CAP_MARGIN)) {
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

                buildPlay(magic, cost, aggression, context, aimedThreats, units, enemies)
                        .ifPresent(plays::add);
            }

            if (!plays.isEmpty()) {
                ScoredPlay chosen = choosePlay(plays);
                Optional<ScoredPlay> saving = handFull(cardList)
                        ? Optional.empty()
                        : savingFor(chosen, worthSavingFor, threats.pressure(), overdue);
                if (saving.isPresent()) {
                    log.debug("[Bot {}] Saving mana for {} (score={}) over {} (score={}), pressure={}",
                            botSide, saving.get().magicId(), saving.get().score(),
                            chosen.magicId(), chosen.score(), threats.pressure());
                    return null;
                }
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
                        playerPos, spellStats.castRange(cheapestAffordable), botSide, threats,
                        placementOptions(cheapestAffordable, units), random);
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

    /**
     * @param aimedThreats the threats where offensive spells should aim: led along their motion
     *                     when the bot leads its targets, otherwise the threats as observed
     */
    private Optional<ScoredPlay> buildPlay(Magic magic,
                                           int cost,
                                           double aggression,
                                           ThinkContext context,
                                           ThreatAssessment aimedThreats,
                                           UnitCatalogue units,
                                           List<BotVisibleObject> enemies) {
        double counterValue = counterValue(magic, enemies, aggression);
        BotSpellStats spellStats = context.spellStats();
        double castRange = spellStats.castRange(magic);

        if (!isSummon(magic)) {
            return BlastTargetSelector.bestImpact(
                            aimedThreats.threats(),
                            context.playerPos(),
                            castRange,
                            spellStats.blastRadius(magic),
                            spellStats.damagePerTarget(magic))
                    .map(impact -> {
                        double value = impact.expectedDamage()
                                + KILL_TEMPO_BONUS * impact.lethalCount()
                                + counterValue;
                        return context.scored(magic, impact.center(), cost, value / cost, VALUE_RULE,
                                "Best offensive value covers " + impact.coveredCount()
                                        + " target(s) with expected damage " + impact.expectedDamage() + ".");
                    });
        }

        ThreatAssessment threats = context.threats();
        double value = summonValue(magic, cost, units)
                * (1 + DEFENSE_URGENCY_WEIGHT * threats.pressure())
                + counterValue;
        Vector3 target = PlacementPlanner.plan(
                context.playerPos(), castRange, context.botSide(), threats,
                placementOptions(magic, units), context.random());
        return Optional.of(context.scored(magic, target, cost, value / cost, VALUE_RULE,
                "Best placement value for current defensive pressure " + threats.pressure() + "."));
    }

    /**
     * What one cast of this summon is worth before urgency and counters.
     *
     * <p>Without {@link BotCapability#SUMMON_STRENGTH} every summon is worth the same, so once the
     * score is divided by cost the cheapest always wins. With it, a summon is worth what the average
     * summon is, scaled by how much more it costs and by how much more combat power it buys per
     * mana. An average summon of any price then scores the same per mana as any other, and only
     * buying more or less fight for the mana moves it.
     */
    private double summonValue(Magic magic, int cost, UnitCatalogue units) {
        if (!skill.has(BotCapability.SUMMON_STRENGTH) || units == null || units.averageSummonCost() <= 0) {
            return PLACEMENT_VALUE_PER_CAST;
        }
        return PLACEMENT_VALUE_PER_CAST
                * (cost / units.averageSummonCost())
                * units.relativeEfficiency(magic, cost);
    }

    private PlacementPlanner.Options placementOptions(Magic magic, UnitCatalogue units) {
        boolean ranged = skill.has(BotCapability.ROLE_PLACEMENT) && units != null && units.summonsRanged(magic);
        return new PlacementPlanner.Options(skill.has(BotCapability.STRONGEST_LANE), ranged);
    }

    /**
     * The better card to keep mana for instead of casting {@code chosen}, if there is one.
     *
     * <p>Only while calm and not overdue, and only for a card the bot can actually pay for in full:
     * mana stops at its maximum, so waiting for a card that costs more would wait forever. Since
     * the card costs more than the bot has and no more than the cap, waiting can never run the
     * mana into the cap and waste the charge either.
     */
    private Optional<ScoredPlay> savingFor(ScoredPlay chosen,
                                           List<ScoredPlay> candidates,
                                           double pressure,
                                           boolean overdue) {
        if (overdue || candidates.isEmpty() || pressure >= CALM_PRESSURE) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(candidate -> candidate.score() > chosen.score() * SAVE_MARGIN)
                .max(Comparator.comparingDouble(ScoredPlay::score));
    }

    /**
     * Whether every hand slot is taken. A card leaves the hand only when it is played, and a slot
     * draws the next card only once it is free, so holding cards back with a full hand stops the
     * draw. Measured on the Skies deck: saving for thunder_bird_swarm instead of casting
     * wind_totem kept totems in hand, the bot cast chain_lightning at the enemy base 8 times a game
     * instead of 12, and won 5 of 24 instead of 16.
     */
    private static boolean handFull(List<Long> cardList) {
        return cardList.size() >= PlayerData.MAX_CARD_NUM;
    }

    /** Most the bot can ever hold, or 0 when the parameters do not say, which disables saving. */
    private static int maxMana(Parameters parameters) {
        return (int) parameters.getValueOrDefault(
                GameObjectKey.PLAYER.dbName(), ParameterKey.MAX_MANA.dbName(), 0.0);
    }

    private DeckProfile deckProfile(DatabaseMagicParser dbParser, BotSpellStats spellStats) {
        DeckProfile profile = deckProfile;
        if (profile == null) {
            profile = DeckProfile.of(deckCards.get(), dbParser, spellStats);
            deckProfile = profile;
        }
        return profile;
    }

    private double leadSeconds() {
        return persona.normalizedThinkingTimeMs() / 1000.0 + CAST_RESOLUTION_SECONDS;
    }

    private boolean needsUnitCatalogue() {
        return skill.has(BotCapability.SUMMON_STRENGTH)
                || skill.has(BotCapability.THREAT_STRENGTH)
                || skill.has(BotCapability.ROLE_PLACEMENT);
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
}
