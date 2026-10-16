package com.wordonline.server.game.domain.bot;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.wordonline.server.game.domain.magic.Magic;
import com.wordonline.server.game.domain.magic.ObjectSummoningMagic;

import lombok.extern.slf4j.Slf4j;

/**
 * What a summon card is for, which the parameters cannot say.
 *
 * <p>Who a unit attacks is decided in its prefab initializer ({@code TargetMask} in about forty
 * places), not in any parameter row, and a unit's damage row looks the same whether it hits ground,
 * air or both. {@code wind_spirit} has 300 damage and only ever dives at flying units. So the role of
 * each summon is written down here, read from the code. A summon missing from this table has no
 * role, and the brain treats it the way it did before roles existed. Most summons are built from
 * their database row rather than a class of their own, so no test can list them; the first time a
 * bot holds a summon with no row, one WARN names it.
 */
@Slf4j
enum CardRole {

    /** Moves, and hits ground units, buildings or the enemy base. */
    FIGHTER,

    /** Only ever attacks flying units. */
    ANTI_AIR,

    /** Never moves; hits enemies that come within its reach. */
    DEFENSE,

    /** Never moves; pushes enemy ground units back and deals no damage. */
    PUSHER,

    /** Heals, shields or extends allies; does no damage of its own. */
    SUPPORT,

    /** Speeds up the bot's own mana. */
    ECONOMY;

    private static final Map<String, CardRole> BY_MAGIC = Map.ofEntries(
            Map.entry("aqua_archer", FIGHTER),
            Map.entry("bean_shooter", FIGHTER),
            Map.entry("bomb_sprite", FIGHTER),
            Map.entry("cloud_dragon", FIGHTER),
            Map.entry("dimension_toad", FIGHTER),
            Map.entry("ember_spirit_swarm", FIGHTER),
            Map.entry("evil_ent", FIGHTER),
            Map.entry("fire_lord_spirit", FIGHTER),
            Map.entry("fire_spirit", FIGHTER),
            Map.entry("magma_spirit", FIGHTER),
            Map.entry("mini_rock_swarm", FIGHTER),
            Map.entry("rock_golem", FIGHTER),
            Map.entry("rock_mage", FIGHTER),
            Map.entry("sea_serpent", FIGHTER),
            Map.entry("seed_nest", FIGHTER),
            Map.entry("storm_rider", FIGHTER),
            Map.entry("storm_stag", FIGHTER),
            Map.entry("thunder_bird_swarm", FIGHTER),
            Map.entry("thunder_spirit", FIGHTER),
            Map.entry("towerback", FIGHTER),
            Map.entry("tree_golem", FIGHTER),
            Map.entry("vine_spirit", FIGHTER),
            Map.entry("wall_golem", FIGHTER),
            Map.entry("water_slime_swarm", FIGHTER),
            Map.entry("zap_mouse", FIGHTER),
            Map.entry("bubble_spirit", ANTI_AIR),
            Map.entry("tower", ANTI_AIR),
            Map.entry("wind_spirit", ANTI_AIR),
            Map.entry("cannon", DEFENSE),
            Map.entry("crater", DEFENSE),
            Map.entry("dragon_tower", DEFENSE),
            Map.entry("electric_tower", DEFENSE),
            Map.entry("firework_tower", DEFENSE),
            Map.entry("rock_turret", DEFENSE),
            Map.entry("shock_trap", DEFENSE),
            Map.entry("titan_remnant", DEFENSE),
            Map.entry("vine_colony", DEFENSE),
            Map.entry("wind_totem", PUSHER),
            Map.entry("bubble_generator", SUPPORT),
            Map.entry("grass_generator", SUPPORT),
            Map.entry("healing_totem", SUPPORT),
            Map.entry("life_tree", SUPPORT),
            Map.entry("repair_totem", SUPPORT),
            Map.entry("mana_well", ECONOMY));

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** The role of this magic, or null when it has none written down. */
    static CardRole of(Magic magic) {
        if (magic == null) {
            return null;
        }
        CardRole role = of(magic.name);
        if (role == null && magic instanceof ObjectSummoningMagic && WARNED.add(magic.name)) {
            log.warn("[Bot] Summon {} has no CardRole; it is played as if roles did not exist", magic.name);
        }
        return role;
    }

    static CardRole of(String magicName) {
        return magicName == null ? null : BY_MAGIC.get(magicName);
    }

    static Map<String, CardRole> table() {
        return BY_MAGIC;
    }
}
