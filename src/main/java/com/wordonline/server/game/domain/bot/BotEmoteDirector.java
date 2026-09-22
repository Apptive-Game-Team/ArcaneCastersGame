package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.game.dto.Emote;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides when a bot sends an emote and which one, from its temperament and the health on both
 * sides.
 *
 * <p>Three situations produce an emote: the greeting a few seconds into the match, being clearly
 * ahead, and being clearly behind. The temperament picks the emote within a situation and how often
 * the bot speaks at all - that is the whole {@link #PROFILES} table below, which is why the database
 * stores only the name.
 *
 * <p>What keeps this from reading as a machine is the limits rather than the wording. A bot sends at
 * most {@link #MATCH_BUDGET} emotes in a match, waits at least {@link #MIN_GAP_MILLIS} between two
 * of them on top of the 3 second cooldown every side already has, and spends a separate budget per
 * situation so a long lead produces two taunts rather than a stream of them. Every delay carries
 * jitter, so the greeting does not land on the same second of every match.
 *
 * <p>Called from the loop thread only, by {@link BotAgent#updateEmote}, and holds its per-match
 * state in plain fields for that reason.
 */
public final class BotEmoteDirector {

    /** What the bot is reacting to. Each one spends its own per-match budget. */
    public enum Situation {
        GREETING, AHEAD, BEHIND
    }

    /** Earliest the greeting may land after the first frame, and how far past that it may slide. */
    static final long GREETING_MIN_DELAY_MILLIS = 3_000;
    static final long GREETING_DELAY_SPREAD_MILLIS = 5_000;

    /** Gap between two emotes from one bot, on top of the cooldown every side has. */
    static final long MIN_GAP_MILLIS = 20_000;
    static final long GAP_SPREAD_MILLIS = 10_000;

    /** Emotes one bot may send in one match, the greeting included. */
    static final int MATCH_BUDGET = 4;

    /** Emotes one situation may produce in one match. */
    static final int SITUATION_BUDGET = 2;

    /** How often the bot looks at the score once it is allowed to speak again. */
    static final long CONSIDER_INTERVAL_MILLIS = 2_000;

    /** One side counts as clearly ahead once the other is under this share of its health. */
    static final double CLEAR_LEAD_RATIO = 0.7;

    /**
     * Weight per emote in {@link Emote} declaration order - Laugh, Greet, Taunt, Cry, Surprised -
     * for each of the three situations, plus the chance the bot speaks at all when a situation is
     * open to it. A row of zeros means it says nothing in that situation.
     */
    private record Profile(double rate, double[] greeting, double[] ahead, double[] behind) {

        double[] weights(Situation situation) {
            return switch (situation) {
                case GREETING -> greeting;
                case AHEAD -> ahead;
                case BEHIND -> behind;
            };
        }
    }

    //                                                     Laugh  Greet  Taunt  Cry  Surprised
    private static final Map<BotTemperament, Profile> PROFILES = new EnumMap<>(Map.of(
            BotTemperament.WARM, new Profile(0.8,
                    /* greeting */ new double[] {0, 1, 0, 0, 0},
                    /* ahead    */ new double[] {3, 0, 0, 0, 1},
                    /* behind   */ new double[] {0, 0, 0, 1, 2}),
            BotTemperament.SMUG, new Profile(0.9,
                    /* greeting */ new double[] {1, 2, 0, 0, 0},
                    /* ahead    */ new double[] {1, 0, 4, 0, 0},
                    /* behind   */ new double[] {0, 0, 0, 1, 2}),
            BotTemperament.TIMID, new Profile(0.7,
                    /* greeting */ new double[] {0, 1, 0, 0, 0},
                    /* ahead    */ new double[] {1, 0, 0, 0, 2},
                    /* behind   */ new double[] {0, 0, 0, 3, 2}),
            BotTemperament.STOIC, new Profile(0.15,
                    /* greeting */ new double[] {0, 1, 0, 0, 0},
                    /* ahead    */ new double[] {0, 0, 0, 0, 0},
                    /* behind   */ new double[] {0, 0, 0, 0, 0})));

    // A row is read by Emote ordinal, so a sixth emote added to the enum has to be given a weight
    // in every row here. Failing at class load says so; a short row would otherwise just never be
    // chosen, which is the same silence a deliberate zero produces and reads as intended.
    static {
        for (Map.Entry<BotTemperament, Profile> entry : PROFILES.entrySet()) {
            for (Situation situation : Situation.values()) {
                int length = entry.getValue().weights(situation).length;
                if (length != Emote.values().length) {
                    throw new IllegalStateException("Emote weights for " + entry.getKey() + " " + situation
                            + " cover " + length + " of " + Emote.values().length + " emotes.");
                }
            }
        }
    }

    private final Profile profile;

    private boolean clockStarted;
    private long greetingAtMillis;
    private boolean greetingSettled;
    private long nextAllowedAtMillis;
    private long nextConsiderAtMillis;
    private int sentCount;
    private final EnumMap<Situation, Integer> sentPerSituation = new EnumMap<>(Situation.class);

    public BotEmoteDirector(BotTemperament temperament) {
        this.profile = PROFILES.getOrDefault(temperament, PROFILES.get(BotTemperament.WARM));
    }

    /**
     * The emote the bot sends on this frame, or {@code null} for the usual answer of saying nothing.
     * The bot brain draws from {@link ThreadLocalRandom} rather than the session seed, so this
     * follows it; a seeded {@link Random} goes through the overload below.
     */
    public Emote nextEmote(long nowMillis, int ownHp, int enemyHp) {
        return nextEmote(nowMillis, ownHp, enemyHp, ThreadLocalRandom.current());
    }

    Emote nextEmote(long nowMillis, int ownHp, int enemyHp, Random random) {
        if (!clockStarted) {
            startClock(nowMillis, random);
        }
        if (sentCount >= MATCH_BUDGET) {
            return null;
        }

        Emote greeting = greetingIfDue(nowMillis, random);
        if (greeting != null) {
            return greeting;
        }

        if (nowMillis < nextAllowedAtMillis || nowMillis < nextConsiderAtMillis) {
            return null;
        }
        nextConsiderAtMillis = nowMillis + CONSIDER_INTERVAL_MILLIS;

        Situation situation = situationFor(ownHp, enemyHp);
        if (situation == null || !hasBudget(situation) || random.nextDouble() >= profile.rate()) {
            return null;
        }
        return take(situation, nowMillis, random);
    }

    // The clock starts on the first frame the director sees rather than at construction. For a
    // practice match that is the first tick of the loop; for a bot that takes over a disconnected
    // player mid-match it is the takeover, and the greeting is the bot saying hello as it sits
    // down. Situational emotes wait out one full gap, which leaves the greeting the opening word.
    private void startClock(long nowMillis, Random random) {
        clockStarted = true;
        greetingAtMillis = nowMillis + GREETING_MIN_DELAY_MILLIS + jitter(random, GREETING_DELAY_SPREAD_MILLIS);
        nextAllowedAtMillis = nowMillis + MIN_GAP_MILLIS;
    }

    // The greeting gets one chance. A temperament that does not take it stays quiet for the rest of
    // the match rather than greeting late, which is what makes a STOIC greeting occasional.
    private Emote greetingIfDue(long nowMillis, Random random) {
        if (greetingSettled || nowMillis < greetingAtMillis) {
            return null;
        }
        greetingSettled = true;
        if (random.nextDouble() >= profile.rate()) {
            return null;
        }
        return take(Situation.GREETING, nowMillis, random);
    }

    private Emote take(Situation situation, long nowMillis, Random random) {
        Emote emote = pick(profile.weights(situation), random);
        if (emote == null) {
            return null;
        }
        sentCount++;
        sentPerSituation.merge(situation, 1, Integer::sum);
        nextAllowedAtMillis = nowMillis + MIN_GAP_MILLIS + jitter(random, GAP_SPREAD_MILLIS);
        return emote;
    }

    private boolean hasBudget(Situation situation) {
        return sentPerSituation.getOrDefault(situation, 0) < SITUATION_BUDGET;
    }

    // A dead side is a finished match, not a situation to react to.
    private static Situation situationFor(int ownHp, int enemyHp) {
        if (ownHp <= 0 || enemyHp <= 0) {
            return null;
        }
        if (enemyHp < ownHp * CLEAR_LEAD_RATIO) {
            return Situation.AHEAD;
        }
        if (ownHp < enemyHp * CLEAR_LEAD_RATIO) {
            return Situation.BEHIND;
        }
        return null;
    }

    private static Emote pick(double[] weights, Random random) {
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        if (total <= 0) {
            return null;
        }

        double roll = random.nextDouble() * total;
        Emote[] emotes = Emote.values();
        for (int i = 0; i < emotes.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return emotes[i];
            }
        }
        return emotes[emotes.length - 1];
    }

    private static long jitter(Random random, long spreadMillis) {
        return (long) (random.nextDouble() * spreadMillis);
    }
}
