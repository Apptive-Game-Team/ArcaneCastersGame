package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.implement.explode.OvergrowthMagic;
import com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic;
import com.wordonline.server.game.domain.object.prefab.PrefabType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Vine Toss or Overgrowth onto an allied SeedSpirit, which is what grows it. */
final class SeedSpiritComboRule implements PriorityRule {

    static final String RULE_ID = "combo.seed-spirit";

    @Override
    public BotCapability capability() {
        return BotCapability.SEED_SPIRIT_COMBO;
    }

    @Override
    public Optional<ScoredPlay> find(ThinkContext context) {
        List<BotVisibleObject> seedSpirits = context.objects().stream()
                .filter(object -> object.master() == context.botSide())
                .filter(BotVisibleObject::targetable)
                .filter(object -> object.type() == PrefabType.SeedSpirit)
                .toList();
        if (seedSpirits.isEmpty()) {
            return Optional.empty();
        }

        List<ScoredPlay> candidates = new ArrayList<>();
        for (Magic magic : context.hand()) {
            if (!(magic instanceof VineTossMagic) && !(magic instanceof OvergrowthMagic)) {
                continue;
            }
            int cost = context.spellStats().manaCost(magic);
            if (cost > context.mana()) {
                continue;
            }
            double castRange = context.spellStats().castRange(magic);
            for (BotVisibleObject seedSpirit : seedSpirits) {
                if (seedSpirit.position().distance(context.playerPos()) > castRange) {
                    continue;
                }
                String magicName = magic.getClass().getSimpleName();
                candidates.add(context.scored(magic, seedSpirit.position(), cost, 1.0 / cost, RULE_ID,
                        magicName + " targets allied SeedSpirit " + seedSpirit.id() + "."));
            }
        }
        return candidates.stream().max(Comparator.comparingDouble(ScoredPlay::noisyScore));
    }
}
