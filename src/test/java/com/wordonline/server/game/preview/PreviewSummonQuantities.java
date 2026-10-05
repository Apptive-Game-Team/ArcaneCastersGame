package com.wordonline.server.game.preview;

import java.util.Map;

/** Recording snapshot: database V001_20260916 parameter_values, parameter_id=9.
 * Keep independent of the mock parameter reader so a generic fixture value cannot
 * silently turn every spell into a three-unit cast. Owners absent in that snapshot
 * use AbstractSpawnMagic's production default of one. Not live balance data. */
final class PreviewSummonQuantities {
    private static final Map<String, Integer> QUANTITIES = Map.ofEntries(
            Map.entry("aqua_archer", 1), Map.entry("ember_spirit", 5),
            Map.entry("cloud_dragon", 1), Map.entry("fire_spirit", 1),
            Map.entry("ground_cannon", 1), Map.entry("ground_tower", 1),
            Map.entry("healing_totem", 1), Map.entry("life_tree", 1),
            Map.entry("magma_spirit", 1), Map.entry("mana_well", 1),
            Map.entry("seed_spirit", 4), Map.entry("mini_rock", 2),
            Map.entry("overgrowth", 2), Map.entry("rock_golem", 1),
            Map.entry("rock_mage", 1), Map.entry("rock_turret", 1),
            Map.entry("sand_storm", 1), Map.entry("slime", 10),
            Map.entry("storm_rider", 1), Map.entry("thunder_bird", 3),
            Map.entry("thunder_spirit", 1), Map.entry("tree_golem", 1),
            Map.entry("vine_colony", 1), Map.entry("wind_spirit", 1),
            Map.entry("vine_spirit", 2), Map.entry("water_slime", 3),
            Map.entry("lightning_cloud", 3), Map.entry("zap_mouse", 2),
            Map.entry("evil_ent", 1), Map.entry("sea_serpent", 1),
            Map.entry("storm_stag", 1), Map.entry("wall_golem", 1),
            Map.entry("grass_generator", 6), Map.entry("bomb_sprite", 1));

    static int forOwner(String owner) {
        return QUANTITIES.getOrDefault(owner, 1);
    }

    private PreviewSummonQuantities() { }
}
