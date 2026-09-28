package com.wordonline.server.game.domain.parameter;

public enum ParameterKey {
    ACCELERATION("acceleration"),
    ATTACK_INTERVAL("attack_interval"),
    ATTACK_OFFSET("attack_offset"),
    ATTACK_RANGE("attack_range"),
    BEAM_WIDTH("beam_width"),
    BUILDING_SNARE_HEAL("building_snare_heal"),
    BURN_DURATION("burn_duration"),
    BURN_TOTAL_DAMAGE("burn_total_damage"),
    BUFF_DURATION("buff_duration"),
    CHAIN_COUNT("chain_count"),
    CHAIN_DAMAGE("chain_damage"),
    CHAIN_LIGHTNING_COOLDOWN("chain_lightning_cooldown"),
    CHAIN_RADIUS("chain_radius"),
    DAMAGE("damage"),
    DETECTION_RANGE("detection_range"),
    DURATION("duration"),
    EFFECT_RADIUS("effect_radius"),
    FEVER_DURATION("fever_duration"),
    FALL_GRAVITY("fall_gravity"),
    HEAL_AMOUNT("heal_amount"),
    HEAL_INTERVAL("heal_interval"),
    HP("hp"),
    LEAF_FIELD_HEAL_AMOUNT("leaf_field_heal_amount"),
    LEAF_FIELD_HEAL_DURATION("leaf_field_heal_duration"),
    MASS("mass"),
    MAX_MANA("max_mana"),
    MIN_DAMAGE("min_damage"),
    PROJECTILE_SPEED("projectile_speed"),
    PULL_MASS_LIMIT("pull_mass_limit"),
    PUSH_FORCE("push_force"),
    PUSH_RANGE_X("push_range_x"),
    PUSH_RANGE_Y("push_range_y"),
    PUSH_RANGE_Z("push_range_z"),
    PANIC_DURATION("panic_duration"),
    QUANTITY("quantity"),
    RADIUS("radius"),
    RANGE("range"),
    SANDSTORM_EFFECT_DAMAGE("sandstorm_effect_damage"),
    SANDSTORM_EFFECT_DURATION("sandstorm_effect_duration"),
    SHOCK_REFRESH_DURATION("shock_refresh_duration"),
    SHOCK_STUN_DURATION("shock_stun_duration"),
    SNARE_DURATION("snare_duration"),
    SNARE_FIRE_DAMAGE("snare_fire_damage"),
    SNARE_SLOW_PERCENT("snare_slow_percent"),
    SPAWN_HEIGHT("spawn_height"),
    SPAWN_INTERVAL("spawn_interval"),
    SUB_DAMAGE("sub_damage"),
    SUB_SPEED("sub_speed"),
    VINE_COUNT("vine_count"),
    VINE_SPACING("vine_spacing"),
    VINE_SPAWN_INTERVAL("vine_spawn_interval"),
    WET_DURATION("wet_duration"),
    WET_NATURE_HEAL("wet_nature_heal"),
    Z_FORCE("z_force"),
    SPEED("speed"),

    SUB_ATTACK_RANGE("sub_attack_range"),
    SUB_ATTACK_INTERVAL("sub_attack_interval"),

    TRIGGER_DELAY("trigger_delay"),
    STUN_DURATION("stun_duration");

    private final String dbName;

    ParameterKey(String dbName) {
        this.dbName = dbName;
    }

    public String dbName() {
        return dbName;
    }
}
