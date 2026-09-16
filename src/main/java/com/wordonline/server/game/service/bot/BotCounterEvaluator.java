package com.wordonline.server.game.service.bot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.bot.BotVisibleObject;
import com.wordonline.server.game.repository.TagRepository;
import com.wordonline.server.game.service.MagicMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotCounterEvaluator {

    private final MagicMetadataService magicMetadataService;
    private final TagRepository tagRepository;

    /** How much this magic beats what the enemy has on the field. */
    public double evaluate(Magic magic, List<BotVisibleObject> enemies) {
        return score(magic, enemies, Direction.ATTACKING);
    }

    /**
     * How much what the enemy has on the field beats this magic - the same table read the
     * other way round. A bot that is meant to lose needs this: it still commits a real unit
     * every time, but the unit it commits is the one the enemy board answers best. Scoring
     * low on {@link #evaluate} is not the same thing, because that only says "this does not
     * beat them" and is satisfied by anything irrelevant.
     */
    public double evaluateVulnerability(Magic magic, List<BotVisibleObject> enemies) {
        return score(magic, enemies, Direction.DEFENDING);
    }

    private double score(Magic magic, List<BotVisibleObject> enemies, Direction direction) {
        try {
            if (magic == null || magic.id <= 0 || enemies.isEmpty()) {
                return 0.0;
            }

            Set<String> magicTags = magicMetadataService.getMagicTags(magic.id);
            if (magicTags.isEmpty()) {
                return 0.0;
            }

            double score = 0.0;
            for (BotVisibleObject enemy : enemies) {
                Set<String> enemyTags = tagRepository.getGameObjectTags(enemy.type());
                score += direction == Direction.ATTACKING
                        ? tagRepository.getCounterWeight(magicTags, enemyTags)
                        : tagRepository.getCounterWeight(enemyTags, magicTags);
            }
            return score;
        } catch (RuntimeException e) {
            log.warn("[BotCounterEvaluator] Counter scoring unavailable; using neutral score. magic={}",
                    magic == null ? null : magic.id, e);
            return 0.0;
        }
    }

    /** Which side of the matchup is the attacker in the counter table lookup. */
    private enum Direction {
        ATTACKING,
        DEFENDING
    }
}
