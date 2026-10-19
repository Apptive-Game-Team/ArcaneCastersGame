package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.object.Vector3;

/**
 * One candidate cast and how much the brain likes it.
 *
 * @param score      value per mana before any noise
 * @param noisyScore the score the candidate is actually ranked by, after the tier's noise
 */
record ScoredPlay(long magicId,
                  Vector3 target,
                  int cost,
                  double score,
                  double noisyScore,
                  String ruleId,
                  String reason) {

    BotBrain.InputDecision toDecision() {
        return new BotBrain.InputDecision(magicId, target, ruleId, reason);
    }
}
