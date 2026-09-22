package com.wordonline.server.game.domain.bot;

import com.wordonline.server.bot.domain.BotTemperament;
import com.wordonline.server.game.dto.Emote;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides when a bot sends an emote and which one, from its temperament, the health on both sides,
 * and what the opponent just sent it.
 *
 * <p>Four things produce an emote: the greeting a few seconds into the match, being clearly ahead,
 * being clearly behind, and answering an emote from the other side. The temperament picks the emote
 * within each of them and how often the bot speaks at all - that is the whole {@link #PROFILES}
 * table below, which is why the database stores only the name.
 *
 * <p>What keeps this from reading as a machine is the limits rather than the wording. A bot sends at
 * most {@link #MATCH_BUDGET} emotes in a match, of which at most {@link #REPLY_BUDGET} are replies
 * and at most {@link #SITUATIONAL_MATCH_BUDGET} are its own, waits at least {@link #MIN_GAP_MILLIS}
 * between two situational ones, and spends a separate budget per situation so a long lead produces
 * two taunts rather than a stream of them. Every delay carries jitter, so neither the greeting nor
 * an answer lands on the same second of every match.
 *
 * <p>A reply is deliberately outside the situational gap. The greeting goes out three to eight
 * seconds in, and a human greeting back at ten seconds would fall inside a twenty second gap - the
 * bot would stare past an outstretched hand, which reads worse than never greeting at all. Replies
 * carry their own budget and their own shorter gap instead, and win whenever both come due.
 *
 * <p>Called from the loop thread only - by {@link BotAgent#updateEmote} every frame and by
 * {@link BotAgent#onOpponentEmote} from an action the input thread queued - and holds its per-match
 * state in plain fields for that reason.
 */
public final class BotEmoteDirector {

    /** What the bot is reacting to on its own. Each one spends its own per-match budget. */
    public enum Situation {
        GREETING, AHEAD, BEHIND
    }

    /** Earliest the greeting may land after the first frame, and how far past that it may slide. */
    static final long GREETING_MIN_DELAY_MILLIS = 3_000;
    static final long GREETING_DELAY_SPREAD_MILLIS = 5_000;

    /** Gap between two situational emotes, on top of the cooldown every side has. */
    static final long MIN_GAP_MILLIS = 20_000;
    static final long GAP_SPREAD_MILLIS = 10_000;

    /** How long the bot waits before answering, so the answer reads as a pause and not a reflex. */
    static final long REPLY_MIN_DELAY_MILLIS = 800;
    static final long REPLY_DELAY_SPREAD_MILLIS = 1_700;

    /** Gap between two replies. A human working through the emote wheel gets one answer, not five. */
    static final long REPLY_GAP_MILLIS = 8_000;

    /** Emotes one bot may send in one match, replies and the greeting included. */
    static final int MATCH_BUDGET = 6;

    /** Of those, how many the bot may send on its own account. */
    static final int SITUATIONAL_MATCH_BUDGET = 4;

    /** Of those, how many may be answers to the other side. */
    static final int REPLY_BUDGET = 2;

    /** Emotes one situation may produce in one match. */
    static final int SITUATION_BUDGET = 2;

    /** How often the bot looks at the score once it is allowed to speak again. */
    static final long CONSIDER_INTERVAL_MILLIS = 2_000;

    /** One side counts as clearly ahead once the other is under this share of its health. */
    static final double CLEAR_LEAD_RATIO = 0.7;

    /**
     * The cooldown {@link com.wordonline.server.game.domain.SessionObject} enforces on every side.
     * The director spaces its own sends by the same amount, so an answer that comes due right
     * behind a greeting waits a moment instead of being dropped on arrival.
     */
    static final long SERVER_COOLDOWN_MILLIS = 3_000;

    /**
     * Weight per emote in {@link Emote} declaration order - Laugh, Greet, Taunt, Cry, Surprised -
     * for each of the three situations and for each emote the opponent may send, plus the chance
     * the bot speaks at all. A row of zeros means it says nothing to that.
     *
     * @param reply one row per incoming emote, also in {@link Emote} declaration order
     */
    private record Profile(double rate,
                           double replyRate,
                           double[] greeting,
                           double[] ahead,
                           double[] behind,
                           double[][] reply) {

        double[] weights(Situation situation) {
            return switch (situation) {
                case GREETING -> greeting;
                case AHEAD -> ahead;
                case BEHIND -> behind;
            };
        }

        double[] replyTo(Emote incoming) {
            return reply[incoming.ordinal()];
        }
    }

    //                                                          Laugh  Greet  Taunt  Cry  Surprised
    private static final Map<BotTemperament, Profile> PROFILES = new EnumMap<>(Map.of(
            BotTemperament.WARM, new Profile(0.8, 0.95,
                    /* greeting        */ new double[] {0, 1, 0, 0, 0},
                    /* ahead           */ new double[] {3, 0, 0, 0, 1},
                    /* behind          */ new double[] {0, 0, 0, 1, 2},
                    new double[][] {
                            /* to Laugh     */ {4, 0, 0, 0, 1},
                            /* to Greet     */ {0, 5, 0, 0, 0},
                            /* to Taunt     */ {3, 0, 0, 0, 1},
                            /* to Cry       */ {0, 1, 0, 0, 2},
                            /* to Surprised */ {1, 0, 0, 0, 3}}),
            BotTemperament.SMUG, new Profile(0.9, 0.85,
                    /* greeting        */ new double[] {1, 2, 0, 0, 0},
                    /* ahead           */ new double[] {1, 0, 4, 0, 0},
                    /* behind          */ new double[] {0, 0, 0, 1, 2},
                    new double[][] {
                            /* to Laugh     */ {4, 0, 0, 0, 0},
                            /* to Greet     */ {2, 3, 0, 0, 0},
                            /* to Taunt     */ {1, 0, 4, 0, 0},
                            /* to Cry       */ {1, 0, 3, 0, 0},
                            /* to Surprised */ {3, 0, 1, 0, 0}}),
            BotTemperament.TIMID, new Profile(0.7, 0.8,
                    /* greeting        */ new double[] {0, 1, 0, 0, 0},
                    /* ahead           */ new double[] {1, 0, 0, 0, 2},
                    /* behind          */ new double[] {0, 0, 0, 3, 2},
                    new double[][] {
                            /* to Laugh     */ {3, 0, 0, 0, 1},
                            /* to Greet     */ {0, 4, 0, 0, 1},
                            /* to Taunt     */ {0, 0, 0, 3, 1},
                            /* to Cry       */ {0, 0, 0, 4, 0},
                            /* to Surprised */ {0, 0, 0, 1, 4}}),
            BotTemperament.STOIC, new Profile(0.15, 0.2,
                    /* greeting        */ new double[] {0, 1, 0, 0, 0},
                    /* ahead           */ new double[] {0, 0, 0, 0, 0},
                    /* behind          */ new double[] {0, 0, 0, 0, 0},
                    new double[][] {
                            /* to Laugh     */ {1, 0, 0, 0, 0},
                            /* to Greet     */ {0, 1, 0, 0, 0},
                            /* to Taunt     */ {0, 0, 0, 0, 0},
                            /* to Cry       */ {0, 0, 0, 0, 0},
                            /* to Surprised */ {0, 0, 0, 0, 0}})));

    // A row is read by Emote ordinal, so a sixth emote added to the enum has to be given a weight in
    // every row here, and a row of its own to be answered with. Failing at class load says so; a
    // short row would otherwise just never be chosen, which is the same silence a deliberate zero
    // produces and reads as intended.
    static {
        for (Map.Entry<BotTemperament, Profile> entry : PROFILES.entrySet()) {
            for (Situation situation : Situation.values()) {
                requireFullRow(entry.getKey(), situation.name(), entry.getValue().weights(situation).length);
            }
            requireFullRow(entry.getKey(), "reply", entry.getValue().reply().length);
            for (Emote incoming : Emote.values()) {
                requireFullRow(entry.getKey(), "reply to " + incoming, entry.getValue().replyTo(incoming).length);
            }
        }
    }

    private static void requireFullRow(BotTemperament temperament, String row, int length) {
        if (length != Emote.values().length) {
            throw new IllegalStateException("Emote weights for " + temperament + " " + row
                    + " cover " + length + " of " + Emote.values().length + " emotes.");
        }
    }

    /** An emote the opponent sent, waiting out the pause before the bot answers it. */
    private record PendingReply(Emote incoming, long dueAtMillis) {
    }

    private final Profile profile;

    private boolean clockStarted;
    private long greetingAtMillis;
    private boolean greetingSettled;
    private long nextAllowedAtMillis;
    private long nextConsiderAtMillis;
    private long nextReplyAllowedAtMillis;
    private long lastSentAtMillis = Long.MIN_VALUE / 2;
    private PendingReply pendingReply;
    private int sentCount;
    private int situationalSentCount;
    private int replySentCount;
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
        if (sentCount >= MATCH_BUDGET || nowMillis < lastSentAtMillis + SERVER_COOLDOWN_MILLIS) {
            return null;
        }

        // An answer outranks anything the bot wanted to say on its own. A bot that ignores the hand
        // held out to it so it can announce that it is winning is the wrong bot.
        Emote reply = replyIfDue(nowMillis, random);
        if (reply != null) {
            return reply;
        }

        Emote greeting = greetingIfDue(nowMillis, random);
        if (greeting != null) {
            return greeting;
        }

        if (situationalSentCount >= SITUATIONAL_MATCH_BUDGET
                || nowMillis < nextAllowedAtMillis
                || nowMillis < nextConsiderAtMillis) {
            return null;
        }
        nextConsiderAtMillis = nowMillis + CONSIDER_INTERVAL_MILLIS;

        Situation situation = situationFor(ownHp, enemyHp);
        if (situation == null || !hasBudget(situation) || random.nextDouble() >= profile.rate()) {
            return null;
        }
        return take(situation, nowMillis, random);
    }

    /**
     * Tells the director that the other side just emoted at this bot. The answer itself goes out
     * through {@link #nextEmote} a beat later, so the bot answers after a pause rather than in the
     * same instant.
     */
    public void onOpponentEmote(Emote emote, long nowMillis) {
        onOpponentEmote(emote, nowMillis, ThreadLocalRandom.current());
    }

    void onOpponentEmote(Emote emote, long nowMillis, Random random) {
        if (!clockStarted) {
            startClock(nowMillis, random);
        }
        if (emote == null
                || pendingReply != null
                || replySentCount >= REPLY_BUDGET
                || sentCount >= MATCH_BUDGET
                || nowMillis < nextReplyAllowedAtMillis) {
            return;
        }
        pendingReply = new PendingReply(emote,
                nowMillis + REPLY_MIN_DELAY_MILLIS + jitter(random, REPLY_DELAY_SPREAD_MILLIS));
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

    private Emote replyIfDue(long nowMillis, Random random) {
        PendingReply pending = pendingReply;
        if (pending == null || nowMillis < pending.dueAtMillis()) {
            return null;
        }
        pendingReply = null;

        // Whether the bot answers or lets it pass, the next answer waits out the gap. Otherwise a
        // temperament that declines one emote would simply answer the next one of the burst.
        nextReplyAllowedAtMillis = nowMillis + REPLY_GAP_MILLIS;
        if (replySentCount >= REPLY_BUDGET || random.nextDouble() >= profile.replyRate()) {
            return null;
        }

        Emote emote = pick(profile.replyTo(pending.incoming()), random);
        if (emote == null) {
            return null;
        }
        replySentCount++;
        recordSent(nowMillis);
        return emote;
    }

    // The greeting gets one chance. A temperament that does not take it stays quiet for the rest of
    // the match rather than greeting late, which is what makes a STOIC greeting occasional.
    private Emote greetingIfDue(long nowMillis, Random random) {
        if (greetingSettled
                || nowMillis < greetingAtMillis
                || situationalSentCount >= SITUATIONAL_MATCH_BUDGET) {
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
        situationalSentCount++;
        sentPerSituation.merge(situation, 1, Integer::sum);
        nextAllowedAtMillis = nowMillis + MIN_GAP_MILLIS + jitter(random, GAP_SPREAD_MILLIS);
        recordSent(nowMillis);
        return emote;
    }

    private void recordSent(long nowMillis) {
        sentCount++;
        lastSentAtMillis = nowMillis;
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
