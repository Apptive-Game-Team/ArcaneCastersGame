package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ends the match when a single affordable spell can.
 *
 * <p>The value scorer already weighs damage on the enemy player up, but only as a factor: a juicy
 * cluster of summons can still outrank the hit that wins. Nothing on the field is worth more than
 * that hit, so it is taken before anything is weighed.
 */
final class LethalRule implements PriorityRule {

    static final String RULE_ID = "finish.lethal";

    @Override
    public BotCapability capability() {
        return BotCapability.LETHAL;
    }

    @Override
    public Optional<ScoredPlay> find(ThinkContext context) {
        Optional<EnemyThreat> core = context.threats().threats().stream()
                .filter(EnemyThreat::playerCore)
                .findFirst();
        if (core.isEmpty()) {
            return Optional.empty();
        }
        EnemyThreat enemyPlayer = core.get();

        List<ScoredPlay> candidates = new ArrayList<>();
        for (Magic magic : context.hand()) {
            if (magic instanceof ObjectSummoningMagic) {
                continue;
            }
            int cost = context.spellStats().manaCost(magic);
            if (cost > context.mana()) {
                continue;
            }
            if (enemyPlayer.position().distance(context.playerPos()) > context.spellStats().castRange(magic)) {
                continue;
            }
            // Only a damage the parameters actually state. The scorer's fallback guess is fine for
            // ranking blasts, but a finisher that does not finish wastes the turn that mattered.
            double damage = context.spellStats().statedDamage(magic);
            if (damage <= 0 || damage < enemyPlayer.hp()) {
                continue;
            }
            // Cheapest finisher first: the game ends either way, so there is nothing to gain from a
            // bigger one, and the cheaper one leaves mana for anything that goes wrong.
            candidates.add(context.scored(magic, enemyPlayer.position(), cost, 1.0 / cost, RULE_ID,
                    magic.name + " finishes the enemy player at "
                            + enemyPlayer.hp() + " hp."));
        }
        return candidates.stream().max(Comparator.comparingDouble(ScoredPlay::score));
    }
}
