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
    (11, 'cannon');

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
    (9, 'aim_shape');

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
    (11, 7, 30), (11, 8, 6), (11, 9, 0);

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
