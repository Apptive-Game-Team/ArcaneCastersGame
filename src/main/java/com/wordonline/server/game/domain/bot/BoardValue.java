package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.dto.Master;
import com.wordonline.server.game.repository.TagRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a side's board is worth, priced in the mana that was spent to put it there.
 *
 * <p>Counting bodies would call a two-mana critter and a twelve-mana golem the same thing, which is
 * no basis for "stay weaker than what they have out". Mana is the game's own measure of investment,
 * the bot already reasons in it, and it follows balance changes on its own - unlike a per-unit
 * strength number, which would be one more hand-maintained value to forget when a unit is added.
 *
 * <p>Prices come from {@link ObjectSummoningMagic}: the magic that puts a prefab on the field is
 * the only thing that reliably knows it does. Matching game objects to magics by name does not
 * work - {@code ember_spirit_swarm} summons {@code ember_spirit}, and most of what actually stands
 * on the field has no magic of its own name at all.
 *
 * <p>A body is worth what it cost scaled by the health it has left. A tower on its last sliver is
 * not the wall it was bought as, and without this the hospitality bot keeps its whole allowance
 * against an enemy board that is already gone, and re-summons every time its own units die.
 */
@Slf4j
final class BoardValue {

    /**
     * Charged for a unit whose summoning magic is unknown. Free rather than guessed: over-pricing
     * the enemy board would let the hospitality bot summon something stronger than the rule intends,
     * while under-pricing only makes it play weaker.
     */
    static final int UNKNOWN_COST = 0;

    private final Map<PrefabType, Integer> costByPrefab;
    private final Parameters parameters;

    BoardValue(DatabaseMagicParser magicParser, BotSpellStats spellStats, Parameters parameters) {
        this.costByPrefab = priceEverySummon(magicParser, spellStats);
        this.parameters = parameters;
    }

    /** Mana the given side has standing on the field. The player core is not something anyone summoned. */
    int manaOnField(List<BotVisibleObject> objects, Master side) {
        int total = 0;
        for (BotVisibleObject object : objects) {
            if (object.master() != side || object.type() == PrefabType.Player || !object.targetable()) {
                continue;
            }
            total += (int) Math.round(manaCostOf(object.type()) * healthFraction(object));
        }
        return total;
    }

    /**
     * How much of the unit is left, from 0 to 1. Falls back to whole when the prefab has no hp
     * parameter to compare against: pretending an unknown unit is nearly dead would hand the
     * hospitality bot room it has not earned.
     */
    private double healthFraction(BotVisibleObject object) {
        double maxHp = parameters.getValueOrDefault(
                TagRepository.toGameObjectName(object.type()), ParameterKey.HP.dbName(), 0);
        if (maxHp <= 0) {
            return 1.0;
        }
        return Math.clamp(object.hp() / maxHp, 0.0, 1.0);
    }

    /** Mana one body of this prefab represents, or {@link #UNKNOWN_COST} when nothing summons it. */
    int manaCostOf(PrefabType type) {
        return costByPrefab.getOrDefault(type, UNKNOWN_COST);
    }

    /**
     * One body's share of what its magic cost. Dividing by the quantity is what keeps a swarm from
     * pricing as its whole cost per body; five bodies from one twenty-mana cast are worth twenty
     * together, not a hundred.
     */
    private static Map<PrefabType, Integer> priceEverySummon(DatabaseMagicParser magicParser,
                                                             BotSpellStats spellStats) {
        Map<PrefabType, Integer> prices = new EnumMap<>(PrefabType.class);
        try {
            for (Magic magic : magicParser.getAllMagics()) {
                if (!(magic instanceof ObjectSummoningMagic summoning)) {
                    continue;
                }

                int castCost = spellStats.manaCost(magic);
                if (castCost >= BotSpellStats.UNKNOWN_MANA_COST) {
                    continue;
                }

                int quantity = Math.max(1, summoning.summonedQuantity());
                prices.merge(summoning.summonedPrefab(), castCost / quantity, Math::min);
            }
        } catch (RuntimeException e) {
            log.debug("[BoardValue] Could not price the summon catalogue; every unit counts as free", e);
        }
        return prices;
    }
}
