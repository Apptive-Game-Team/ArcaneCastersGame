package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.object.Vector3;
import com.wordonline.server.game.dto.Master;

import java.util.List;
import java.util.Random;

/**
 * Everything one think pass knows, handed to each {@link PriorityRule} so a new rule needs no new
 * plumbing through the brain.
 *
 * @param hand      the magics in hand, already resolved
 * @param mana      mana available right now
 * @param objects   every object the bot saw this frame
 * @param playerPos where the bot's own player stands, which is where every cast starts from
 * @param threats   hostile objects, as the bot's skill assesses them
 */
record ThinkContext(List<Magic> hand,
                    int mana,
                    BotSpellStats spellStats,
                    List<BotVisibleObject> objects,
                    Vector3 playerPos,
                    Master botSide,
                    Master enemySide,
                    ThreatAssessment threats,
                    BotSkill skill,
                    Random random) {

    /**
     * Wraps a candidate with exactly one tier-scaled random perturbation of its score. Every
     * candidate goes through here once, so the noise a tier adds never compounds.
     */
    ScoredPlay scored(Magic magic, Vector3 target, int cost, double score, String ruleId, String reason) {
        double amplitude = skill.noiseAmplitude();
        double noisyScore = score * (1.0 + (random.nextDouble() * 2.0 - 1.0) * amplitude);
        return new ScoredPlay(magic.id, target, cost, score, noisyScore, ruleId, reason);
    }
}
