package com.wordonline.server.game.domain.bot;

import java.util.Optional;

/**
 * A play the bot makes before weighing value per mana, whenever its situation arises.
 *
 * <p>Rules are tried in the order {@link BotBrain} lists them and the first one that finds a play
 * wins. A rule only runs for a bot whose {@link BotSkill} has its {@link #capability()}, so a new
 * rule is a new class, a new {@link BotCapability}, and an entry in each tier that should know it.
 */
interface PriorityRule {

    /** What a bot must know for this rule to run at all. */
    BotCapability capability();

    /** The play this rule insists on right now, if its situation is on the field. */
    Optional<ScoredPlay> find(ThinkContext context);
}
