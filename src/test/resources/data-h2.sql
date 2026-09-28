-- game_objects.name equals magics.name for anything a cast reads its own values under. The
-- shoot / explode / spawn / build rows were the cast type axis and are gone with it.
INSERT INTO game_objects
VALUES
    (1, 'slime'),
    (6, 'field'),
    (7, 'fire_shot'),
    (8, 'water_shot'),
    (9, 'fire_explosion'),
    (10, 'fire_slime_swarm'),
    (11, 'cannon'),
    (12, 'game');

INSERT INTO parameters
VALUES
    (1, 'speed'),
    (2, 'damage'),
    (3, 'radius'),
    (4, 'hp'),
    (5, 'mass'),
    (6, 'duration'),
    (7, 'mana_cost'),
    (8, 'range'),
    (9, 'aim_shape'),
    (10, 'building_snare_heal'),
    (11, 'burn_duration'),
    (12, 'burn_total_damage'),
    (13, 'wet_duration'),
    (14, 'wet_nature_heal'),
    (15, 'shock_stun_duration'),
    (16, 'shock_refresh_duration'),
    (17, 'snare_duration'),
    (18, 'snare_fire_damage'),
    (19, 'snare_slow_percent'),
    (20, 'leaf_field_heal_amount'),
    (21, 'leaf_field_heal_duration'),
    (22, 'sandstorm_effect_damage'),
    (23, 'sandstorm_effect_duration');

INSERT INTO parameter_values(game_object_id, parameter_id, value)
VALUES
    (1, 3, 0.5),
    (6, 3, 0.5),
    (7, 3, 0.5),
    (9, 3, 0.5),

    (1, 2, 3),
    (7, 2, 10),
    (9, 2, 8),

    (1, 4, 8),
    (11, 4, 5),

    (1, 1, 0.8),

    (1, 5, 1),

    (6, 6, 3),

    -- mana_cost, range and aim_shape are keyed by the magic name now. The values are the ones the
    -- old combinations summed to: fire_shot was Fire(10) + Shoot(15) with the Shoot card's range 18.
    (7, 7, 25), (7, 8, 18), (7, 9, 1),
    (8, 7, 25), (8, 8, 18), (8, 9, 1),
    (9, 7, 20), (9, 8, 9), (9, 9, 0),
    (10, 7, 25), (10, 8, 6), (10, 9, 0),
    (11, 7, 30), (11, 8, 6), (11, 9, 0),

    -- Shared status effect tuning mirrors the production migration.
    (12, 10, 10), (12, 11, 3), (12, 12, 30),
    (12, 13, 3), (12, 14, 30), (12, 15, 0.5),
    (12, 16, 3), (12, 17, 3), (12, 18, 50),
    (12, 19, 0.5), (12, 20, 10), (12, 21, 3),
    (12, 22, 10), (12, 23, 0.5);

INSERT INTO magics(id, name, element)
VALUES
    (1, 'fire_slime_swarm', 'Fire'),
    (2, 'water_slime_swarm', 'Water'),
    (3, 'lightning_slime_swarm', 'Lightning'),
    (4, 'nature_slime_swarm', 'Nature'),
    (5, 'rock_slime_swarm', 'Rock'),
    (6, 'wind_slime_swarm', 'Wind'),

    (7, 'fire_shot', 'Fire'),
    (8, 'water_shot', 'Water'),
    (9, 'lightning_shot', 'Lightning'),
    (10, 'nature_shot', 'Nature'),
    (11, 'rock_shot', 'Rock'),
    (12, 'wind_shot', 'Wind'),

    (13, 'fire_slime_nest', 'Fire'),
    (14, 'water_slime_nest', 'Water'),
    (15, 'lightning_slime_nest', 'Lightning'),
    (16, 'nature_slime_nest', 'Nature'),
    (17, 'rock_slime_nest', 'Rock'),
    (18, 'wind_slime_nest', 'Wind'),

    (19, 'fire_explosion', 'Fire'),
    (20, 'water_explosion', 'Water'),
    (21, 'lightning_explosion', 'Lightning'),
    (22, 'nature_explosion', 'Nature'),
    (23, 'rock_explosion', 'Rock'),
    (24, 'wind_explosion', 'Wind'),

    (25, 'cannon', 'None'),
    (26, 'tower', 'None'),
    (27, 'mana_well', 'None'),
    (28, 'aqua_archer', 'Water'),
    (29, 'rock_golem', 'Rock'),
    (30, 'storm_rider', 'Wind'),
    (31, 'thunder_spirit', 'Lightning'),
    (32, 'fire_spirit', 'Fire');
