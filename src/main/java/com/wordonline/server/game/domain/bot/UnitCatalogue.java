package com.wordonline.server.game.domain.bot;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;
import com.wordonline.server.game.domain.magic.parser.DatabaseMagicParser;
import com.wordonline.server.game.domain.object.prefab.PrefabType;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import com.wordonline.server.game.repository.TagRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * How strong a unit is in a fight, read from the same prefab parameters its initializer reads.
 *
 * <p>Combat power is {@code sqrt(hp * dps)}: a unit is only as useful as the damage it deals for as
 * long as it survives, and the square root keeps a glass cannon and a wall of the same product
 * level. Every judgement is relative to the average summon in the catalogue, so the numbers stay
 * on the scale the rest of the scorer was tuned on and follow balance changes on their own.
 *
 * <p>A unit whose parameters say nothing about its fighting - a mana well, a totem, a prefab with
 * no damage row - is treated as exactly average. Guessing it weak or strong would let one missing
 * row push the bot towards or away from a whole family of cards.
 */
@Slf4j
final class UnitCatalogue {

    /** Seconds between attacks when a prefab has no {@code attack_interval} row. */
    static final double DEFAULT_ATTACK_INTERVAL = 1.0;

    /** A unit whose attack reaches this far fights from behind the line rather than on it. */
    static final double RANGED_ATTACK_RANGE = 3.0;

    /** Bounds on how much better or worse than average a summon may be judged per mana. */
    static final double MIN_RELATIVE_EFFICIENCY = 0.5;
    static final double MAX_RELATIVE_EFFICIENCY = 2.0;

    /** Bounds on how much one enemy may count as, in average summons. */
    static final double MIN_RELATIVE_THREAT = 0.25;
    static final double MAX_RELATIVE_THREAT = 3.0;

    private final Parameters parameters;
    private final double averagePower;
    private final double averagePowerPerMana;
    private final double averageSummonCost;

    UnitCatalogue(DatabaseMagicParser magicParser, BotSpellStats spellStats, Parameters parameters) {
        this.parameters = parameters;

        double powerSum = 0;
        int powerCount = 0;
        double perManaSum = 0;
        int perManaCount = 0;
        double costSum = 0;
        int costCount = 0;
        try {
            for (Magic magic : magicParser.getAllMagics()) {
                if (!(magic instanceof ObjectSummoningMagic summoning)) {
                    continue;
                }
                int cost = spellStats.manaCost(magic);
                if (cost <= 0 || cost >= BotSpellStats.UNKNOWN_MANA_COST) {
                    continue;
                }
                costSum += cost;
                costCount++;

                double power = power(summoning.summonedPrefab(), maxHp(summoning.summonedPrefab()));
                if (power > 0) {
                    powerSum += power;
                    powerCount++;
                    perManaSum += power * Math.max(1, summoning.summonedQuantity()) / cost;
                    perManaCount++;
                }
            }
        } catch (RuntimeException e) {
            log.debug("[UnitCatalogue] Could not read the summon catalogue; every unit counts as average", e);
        }
        this.averagePower = powerCount == 0 ? 0 : powerSum / powerCount;
        this.averagePowerPerMana = perManaCount == 0 ? 0 : perManaSum / perManaCount;
        this.averageSummonCost = costCount == 0 ? 0 : costSum / costCount;
    }

    /** What the average summon costs, or 0 when nothing in the catalogue is priced. */
    double averageSummonCost() {
        return averageSummonCost;
    }

    /**
     * How much combat power this summon buys per mana, against the catalogue's average. 1 is
     * average, and anything the parameters cannot judge is average.
     */
    double relativeEfficiency(Magic magic, int cost) {
        if (!(magic instanceof ObjectSummoningMagic summoning) || cost <= 0 || averagePowerPerMana <= 0) {
            return 1.0;
        }
        double power = power(summoning.summonedPrefab(), maxHp(summoning.summonedPrefab()));
        if (power <= 0) {
            return 1.0;
        }
        double perMana = power * Math.max(1, summoning.summonedQuantity()) / cost;
        return Math.clamp(perMana / averagePowerPerMana, MIN_RELATIVE_EFFICIENCY, MAX_RELATIVE_EFFICIENCY);
    }

    /**
     * How many average summons this object is worth in a fight, at the health it has left. The
     * player core is not a fighter and counts as nothing here.
     */
    double relativeThreat(BotVisibleObject object) {
        if (object.type() == PrefabType.Player) {
            return 0.0;
        }
        if (averagePower <= 0) {
            return 1.0;
        }
        double power = power(object.type(), object.hp());
        if (power <= 0) {
            return 1.0;
        }
        return Math.clamp(power / averagePower, MIN_RELATIVE_THREAT, MAX_RELATIVE_THREAT);
    }

    /** Whether this summon's unit attacks from range, and so belongs behind the line. */
    boolean summonsRanged(Magic magic) {
        if (!(magic instanceof ObjectSummoningMagic summoning)) {
            return false;
        }
        return prefabValue(summoning.summonedPrefab(), ParameterKey.ATTACK_RANGE) >= RANGED_ATTACK_RANGE;
    }

    private double power(PrefabType type, double hp) {
        double damage = prefabValue(type, ParameterKey.DAMAGE);
        if (hp <= 0 || damage <= 0) {
            return 0;
        }
        double interval = prefabValue(type, ParameterKey.ATTACK_INTERVAL);
        double dps = damage / (interval > 0 ? interval : DEFAULT_ATTACK_INTERVAL);
        return Math.sqrt(hp * dps);
    }

    private double maxHp(PrefabType type) {
        return prefabValue(type, ParameterKey.HP);
    }

    private double prefabValue(PrefabType type, ParameterKey key) {
        return parameters.getValueOrDefault(TagRepository.toGameObjectName(type), key.dbName(), 0.0);
    }
}
