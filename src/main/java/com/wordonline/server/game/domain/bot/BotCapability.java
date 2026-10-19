package com.wordonline.server.game.domain.bot;

/**
 * One piece of play a bot either knows or does not.
 *
 * <p>Difficulty used to be a single noise dial on top of one fixed way of playing. Each constant
 * here is a decision the brain can make better, switched on per tier by {@link BotSkill}, so a
 * weaker bot is not only a noisier copy of a strong one but one that genuinely knows less.
 *
 * <p>Adding a tactic means adding a constant, reading it where the decision is made (or writing a
 * {@link PriorityRule} that names it), and granting it to the tiers that should have it in
 * {@link BotSkill#forTier}.
 */
public enum BotCapability {

    /** Feed an allied SeedSpirit with Vine Toss or Overgrowth before anything else. */
    SEED_SPIRIT_COMBO,

    /** Drop an explosion on three or more enemy mobs standing together. */
    MOB_CLUSTER_COMBO,

    /** Finish the enemy player when one affordable spell in hand can. */
    LETHAL,

    /**
     * Defend the single most pressured lane. Without it the bot averages every incoming lane and,
     * when two lanes push at once, places its blocker in the empty space between them.
     */
    STRONGEST_LANE,

    /**
     * Price a summon by the combat power it buys per mana. Without it every summon is worth the
     * same flat amount, so dividing by cost always picks the cheapest one in hand.
     */
    SUMMON_STRENGTH,

    /** Weigh enemy pressure by each unit's combat power rather than counting bodies. */
    THREAT_STRENGTH,

    /** Let allied units already standing in defence take pressure off, instead of over-defending. */
    ALLY_COVER,

    /** While nothing is pushing, save up for a clearly better card in hand instead of spending at once. */
    MANA_DISCIPLINE,

    /** Put ranged units behind the line a melee blocker would hold. */
    ROLE_PLACEMENT,

    /** Aim blasts where moving enemies will be once the cast actually goes out. */
    LEAD_TARGETING,

    /**
     * Skip a summon that has nothing to do on the field ({@link SummonRelevance}): an anti-air unit
     * with no enemy flyer, a building that waits for enemies when none is coming. Mana near the cap
     * may still go to one, so a dead card does not block a hand slot forever.
     */
    SUMMON_RELEVANCE,

    /**
     * Play a plan across casts ({@link GamePlanner}): save for a wave of fighters and release it on
     * one spot, or swarm the lane with cheap fighters, instead of spending each tick's mana at once.
     */
    GAME_PLAN
}
