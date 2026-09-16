INSERT INTO parameters(name)
SELECT 'quantity'
WHERE NOT EXISTS (
    SELECT 1
    FROM parameters
    WHERE name = 'quantity'
);

INSERT INTO parameters(name)
SELECT 'projectile_speed'
WHERE NOT EXISTS (
    SELECT 1
    FROM parameters
    WHERE name = 'projectile_speed'
);

INSERT INTO parameters(name)
SELECT 'z_force'
WHERE NOT EXISTS (
    SELECT 1
    FROM parameters
    WHERE name = 'z_force'
);

INSERT INTO game_objects(name)
SELECT 'ember_spirit'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'ember_spirit');

INSERT INTO game_objects(name)
SELECT 'seed_spirit'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'seed_spirit');

INSERT INTO game_objects(name)
SELECT 'water_slime'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'water_slime');

INSERT INTO game_objects(name)
SELECT 'fire_slime'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'fire_slime');

INSERT INTO game_objects(name)
SELECT 'electric_slime'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'electric_slime');

INSERT INTO game_objects(name)
SELECT 'leaf_slime'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'leaf_slime');

INSERT INTO game_objects(name)
SELECT 'rock_slime'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'rock_slime');

INSERT INTO game_objects(name)
SELECT 'wind_slime'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'wind_slime');

INSERT INTO game_objects(name)
SELECT 'water_shot'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'water_shot');

INSERT INTO game_objects(name)
SELECT 'fire_shot'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'fire_shot');

INSERT INTO game_objects(name)
SELECT 'electric_shot'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'electric_shot');

INSERT INTO game_objects(name)
SELECT 'rock_rolling'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'rock_rolling');

INSERT INTO game_objects(name)
SELECT 'wind_blade'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'wind_blade');

INSERT INTO game_objects(name)
SELECT 'will_o_wisp'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'will_o_wisp');

INSERT INTO game_objects(name)
SELECT 'bubble_spirit'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'bubble_spirit');

INSERT INTO game_objects(name)
SELECT 'bubble_generator'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'bubble_generator');

INSERT INTO game_objects(name)
SELECT 'fire_explode'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'fire_explode');

INSERT INTO game_objects(name)
SELECT 'water_explode'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'water_explode');

INSERT INTO game_objects(name)
SELECT 'water_explosion'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'water_explosion');

INSERT INTO game_objects(name)
SELECT 'leaf_explode'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'leaf_explode');

INSERT INTO game_objects(name)
SELECT 'rock_explode'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'rock_explode');

INSERT INTO game_objects(name)
SELECT 'electric_explode'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'electric_explode');

INSERT INTO game_objects(name)
SELECT 'wind_explode'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'wind_explode');

INSERT INTO game_objects(name)
SELECT 'magma_explosion'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'magma_explosion');

INSERT INTO game_objects(name)
SELECT 'shock_overload'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'shock_overload');

INSERT INTO game_objects(name)
SELECT 'fire_drop'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'fire_drop');

INSERT INTO game_objects(name)
SELECT 'nature_drop'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'nature_drop');

INSERT INTO game_objects(name)
SELECT 'rock_drop'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'rock_drop');

INSERT INTO game_objects(name)
SELECT 'lightning_drop'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'lightning_drop');

INSERT INTO game_objects(name)
SELECT 'leaf_drop'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'leaf_drop');

INSERT INTO game_objects(name)
SELECT 'wind_drop'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'wind_drop');

INSERT INTO game_objects(name)
SELECT 'sand_storm'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'sand_storm');

INSERT INTO game_objects(name)
SELECT 'will_o_wisp'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'will_o_wisp');

INSERT INTO game_objects(name)
SELECT 'fire_summon'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'fire_summon');

INSERT INTO game_objects(name)
SELECT 'electric_summon'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'electric_summon');

INSERT INTO game_objects(name)
SELECT 'rock_summon'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'rock_summon');

INSERT INTO game_objects(name)
SELECT 'wind_summon'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'wind_summon');

INSERT INTO game_objects(name)
SELECT 'fire_field'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'fire_field');

INSERT INTO game_objects(name)
SELECT 'water_field'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'water_field');

INSERT INTO game_objects(name)
SELECT 'electric_field'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'electric_field');

INSERT INTO game_objects(name)
SELECT 'leaf_field'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'leaf_field');

INSERT INTO game_objects(name)
SELECT 'fire_spirit'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'fire_spirit');

INSERT INTO game_objects(name)
SELECT 'magma_spirit'
WHERE NOT EXISTS (SELECT 1 FROM game_objects WHERE name = 'magma_spirit');

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 10
FROM game_objects go
JOIN parameters p ON p.name = 'quantity'
WHERE go.name IN ('ember_spirit', 'seed_spirit', 'water_slime')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1
FROM game_objects go
JOIN parameters p ON p.name = 'mass'
WHERE go.name IN ('ember_spirit', 'seed_spirit', 'water_slime', 'fire_slime', 'electric_slime', 'leaf_slime', 'rock_slime', 'wind_slime')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.5
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name IN ('ember_spirit', 'seed_spirit', 'water_slime', 'fire_slime', 'electric_slime', 'leaf_slime', 'rock_slime', 'wind_slime')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 2
FROM game_objects go
JOIN parameters p ON p.name = 'hp'
WHERE go.name IN ('ember_spirit', 'seed_spirit', 'water_slime', 'fire_slime', 'electric_slime', 'leaf_slime', 'rock_slime', 'wind_slime')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.8
FROM game_objects go
JOIN parameters p ON p.name = 'speed'
WHERE go.name IN ('ember_spirit', 'seed_spirit', 'water_slime', 'fire_slime', 'electric_slime', 'leaf_slime', 'rock_slime', 'wind_slime')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1
FROM game_objects go
JOIN parameters p ON p.name = 'damage'
WHERE go.name IN ('ember_spirit', 'seed_spirit', 'water_slime', 'fire_slime', 'electric_slime', 'leaf_slime', 'rock_slime', 'wind_slime')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1
FROM game_objects go
JOIN parameters p ON p.name = 'attack_interval'
WHERE go.name IN ('ember_spirit', 'seed_spirit', 'water_slime', 'fire_slime', 'electric_slime', 'leaf_slime', 'rock_slime', 'wind_slime')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.5
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name IN ('water_shot', 'fire_shot', 'electric_shot', 'rock_rolling', 'wind_blade', 'will_o_wisp')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.5
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name = 'leaf_drop'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.5
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name = 'will_o_wisp'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 8
FROM game_objects go
JOIN parameters p ON p.name = 'speed'
WHERE go.name = 'will_o_wisp'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 10
FROM game_objects go
JOIN parameters p ON p.name = 'damage'
WHERE go.name IN ('water_shot', 'fire_shot', 'electric_shot', 'rock_rolling', 'wind_blade')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 8
FROM game_objects go
JOIN parameters p ON p.name = 'speed'
WHERE go.name IN ('water_shot', 'fire_shot', 'electric_shot', 'rock_rolling', 'wind_blade', 'will_o_wisp')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.45
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name = 'bubble_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 18
FROM game_objects go
JOIN parameters p ON p.name = 'hp'
WHERE go.name = 'bubble_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1
FROM game_objects go
JOIN parameters p ON p.name = 'mass'
WHERE go.name = 'bubble_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.7
FROM game_objects go
JOIN parameters p ON p.name = 'speed'
WHERE go.name = 'bubble_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 5
FROM game_objects go
JOIN parameters p ON p.name = 'damage'
WHERE go.name = 'bubble_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1.2
FROM game_objects go
JOIN parameters p ON p.name = 'attack_interval'
WHERE go.name = 'bubble_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 4.5
FROM game_objects go
JOIN parameters p ON p.name = 'attack_range'
WHERE go.name = 'bubble_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 8
FROM game_objects go
JOIN parameters p ON p.name = 'projectile_speed'
WHERE go.name = 'bubble_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.65
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name = 'bubble_generator'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 18
FROM game_objects go
JOIN parameters p ON p.name = 'hp'
WHERE go.name = 'bubble_generator'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1000
FROM game_objects go
JOIN parameters p ON p.name = 'mass'
WHERE go.name = 'bubble_generator'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1.2
FROM game_objects go
JOIN parameters p ON p.name = 'attack_interval'
WHERE go.name = 'bubble_generator'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 2.5
FROM game_objects go
JOIN parameters p ON p.name = 'attack_range'
WHERE go.name = 'bubble_generator'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 12
FROM game_objects go
JOIN parameters p ON p.name = 'duration'
WHERE go.name = 'bubble_generator'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 8
FROM game_objects go
JOIN parameters p ON p.name = 'projectile_speed'
WHERE go.name = 'bubble_generator'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.5
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name IN ('fire_explode', 'water_explode', 'leaf_explode', 'rock_explode', 'electric_explode', 'wind_explode', 'magma_explosion', 'shock_overload', 'sand_storm', 'fire_field', 'water_field', 'electric_field', 'leaf_field', 'fire_drop', 'nature_drop', 'rock_drop', 'lightning_drop', 'wind_drop', 'fire_summon', 'electric_summon', 'rock_summon', 'wind_summon')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 8
FROM game_objects go
JOIN parameters p ON p.name = 'damage'
WHERE go.name IN ('fire_explode', 'water_explode', 'leaf_explode', 'rock_explode', 'electric_explode', 'wind_explode', 'magma_explosion', 'shock_overload', 'fire_drop', 'nature_drop', 'rock_drop', 'lightning_drop', 'wind_drop', 'leaf_drop')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 3
FROM game_objects go
JOIN parameters p ON p.name = 'duration'
WHERE go.name IN ('sand_storm', 'fire_field', 'water_field', 'electric_field', 'leaf_field')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 5
FROM game_objects go
JOIN parameters p ON p.name = 'hp'
WHERE go.name IN ('fire_summon', 'electric_summon', 'rock_summon', 'wind_summon')
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1
FROM game_objects go
JOIN parameters p ON p.name = 'mass'
WHERE go.name = 'fire_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.35
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name = 'fire_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 9
FROM game_objects go
JOIN parameters p ON p.name = 'hp'
WHERE go.name = 'fire_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.8
FROM game_objects go
JOIN parameters p ON p.name = 'speed'
WHERE go.name = 'fire_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 4
FROM game_objects go
JOIN parameters p ON p.name = 'damage'
WHERE go.name = 'fire_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1
FROM game_objects go
JOIN parameters p ON p.name = 'attack_interval'
WHERE go.name = 'fire_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 4
FROM game_objects go
JOIN parameters p ON p.name = 'attack_range'
WHERE go.name = 'fire_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 3
FROM game_objects go
JOIN parameters p ON p.name = 'mass'
WHERE go.name = 'magma_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.9
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name = 'magma_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 45
FROM game_objects go
JOIN parameters p ON p.name = 'hp'
WHERE go.name = 'magma_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.45
FROM game_objects go
JOIN parameters p ON p.name = 'speed'
WHERE go.name = 'magma_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 8
FROM game_objects go
JOIN parameters p ON p.name = 'damage'
WHERE go.name = 'magma_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 1.6
FROM game_objects go
JOIN parameters p ON p.name = 'attack_interval'
WHERE go.name = 'magma_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 4
FROM game_objects go
JOIN parameters p ON p.name = 'attack_range'
WHERE go.name = 'magma_spirit'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 0.5
FROM game_objects go
JOIN parameters p ON p.name = 'radius'
WHERE go.name = 'water_explosion'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 8
FROM game_objects go
JOIN parameters p ON p.name = 'damage'
WHERE go.name = 'water_explosion'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT go.id, p.id, 3
FROM game_objects go
JOIN parameters p ON p.name = 'z_force'
WHERE go.name = 'water_explosion'
  AND NOT EXISTS (
      SELECT 1
      FROM parameter_values pv
      WHERE pv.game_object_id = go.id
        AND pv.parameter_id = p.id
  );

WITH required_tags AS (
    SELECT tag_name
    FROM (
        VALUES
            ('TYPE_Unit'),
            ('CAT_Small'),
            ('CAT_Ranged'),
            ('CAT_Flying'),
            ('CAT_AoE'),
            ('CAT_Building')
    ) AS tags(tag_name)
),
inserted_tags AS (
    INSERT INTO tags(name)
    SELECT tag_name
    FROM required_tags rt
    WHERE NOT EXISTS (
        SELECT 1
        FROM tags t
        WHERE t.name = rt.tag_name
    )
    RETURNING id, name
),
target_tags AS (
    SELECT id, name FROM inserted_tags
    UNION ALL
    SELECT t.id, t.name
    FROM tags t
    JOIN required_tags rt ON rt.tag_name = t.name
),
prefab_tag_seed AS (
    SELECT *
    FROM (
        VALUES
            ('ember_spirit', 'TYPE_Unit'),
            ('ember_spirit', 'CAT_Small'),
            ('seed_spirit', 'TYPE_Unit'),
            ('seed_spirit', 'CAT_Small'),
            ('water_slime', 'TYPE_Unit'),
            ('water_slime', 'CAT_Small'),
            ('fire_slime', 'TYPE_Unit'),
            ('fire_slime', 'CAT_Small'),
            ('electric_slime', 'TYPE_Unit'),
            ('electric_slime', 'CAT_Small'),
            ('leaf_slime', 'TYPE_Unit'),
            ('leaf_slime', 'CAT_Small'),
            ('rock_slime', 'TYPE_Unit'),
            ('rock_slime', 'CAT_Small'),
            ('wind_slime', 'TYPE_Unit'),
            ('wind_slime', 'CAT_Small'),
            ('water_shot', 'TYPE_Unit'),
            ('water_shot', 'CAT_Ranged'),
            ('fire_shot', 'TYPE_Unit'),
            ('fire_shot', 'CAT_Ranged'),
            ('electric_shot', 'TYPE_Unit'),
            ('electric_shot', 'CAT_Ranged'),
            ('rock_rolling', 'TYPE_Unit'),
            ('rock_rolling', 'CAT_Ranged'),
            ('wind_blade', 'TYPE_Unit'),
            ('wind_blade', 'CAT_Ranged'),
            ('will_o_wisp', 'TYPE_Unit'),
            ('will_o_wisp', 'CAT_Ranged'),
            ('bubble_spirit', 'TYPE_Unit'),
            ('bubble_spirit', 'CAT_Ranged'),
            ('bubble_spirit', 'CAT_Flying'),
            ('bubble_generator', 'TYPE_Unit'),
            ('bubble_generator', 'CAT_Building'),
            ('fire_explode', 'TYPE_Unit'),
            ('fire_explode', 'CAT_AoE'),
            ('water_explode', 'TYPE_Unit'),
            ('water_explode', 'CAT_AoE'),
            ('water_explosion', 'TYPE_Unit'),
            ('water_explosion', 'CAT_AoE'),
            ('leaf_explode', 'TYPE_Unit'),
            ('leaf_explode', 'CAT_AoE'),
            ('rock_explode', 'TYPE_Unit'),
            ('rock_explode', 'CAT_AoE'),
            ('electric_explode', 'TYPE_Unit'),
            ('electric_explode', 'CAT_AoE'),
            ('wind_explode', 'TYPE_Unit'),
            ('wind_explode', 'CAT_AoE'),
            ('magma_explosion', 'TYPE_Unit'),
            ('magma_explosion', 'CAT_AoE'),
            ('shock_overload', 'TYPE_Unit'),
            ('shock_overload', 'CAT_AoE'),
            ('fire_drop', 'TYPE_Unit'),
            ('fire_drop', 'CAT_AoE'),
            ('nature_drop', 'TYPE_Unit'),
            ('nature_drop', 'CAT_AoE'),
            ('rock_drop', 'TYPE_Unit'),
            ('rock_drop', 'CAT_AoE'),
            ('lightning_drop', 'TYPE_Unit'),
            ('lightning_drop', 'CAT_AoE'),
            ('leaf_drop', 'TYPE_Unit'),
            ('leaf_drop', 'CAT_AoE'),
            ('wind_drop', 'TYPE_Unit'),
            ('wind_drop', 'CAT_AoE'),
            ('sand_storm', 'TYPE_Unit'),
            ('sand_storm', 'CAT_AoE'),
            ('fire_summon', 'TYPE_Unit'),
            ('fire_summon', 'CAT_Building'),
            ('electric_summon', 'TYPE_Unit'),
            ('electric_summon', 'CAT_Building'),
            ('rock_summon', 'TYPE_Unit'),
            ('rock_summon', 'CAT_Building'),
            ('wind_summon', 'TYPE_Unit'),
            ('wind_summon', 'CAT_Building'),
            ('fire_field', 'TYPE_Unit'),
            ('fire_field', 'CAT_AoE'),
            ('water_field', 'TYPE_Unit'),
            ('water_field', 'CAT_AoE'),
            ('electric_field', 'TYPE_Unit'),
            ('electric_field', 'CAT_AoE'),
            ('leaf_field', 'TYPE_Unit'),
            ('leaf_field', 'CAT_AoE'),
            ('fire_spirit', 'TYPE_Unit'),
            ('fire_spirit', 'CAT_Ranged'),
            ('fire_spirit', 'CAT_Flying'),
            ('magma_spirit', 'TYPE_Unit'),
            ('magma_spirit', 'CAT_Ranged')
    ) AS seed(game_object_name, tag_name)
)
INSERT INTO game_object_tags(game_object_id, tag_id)
SELECT go.id, tt.id
FROM prefab_tag_seed pts
JOIN game_objects go ON go.name = pts.game_object_name
JOIN target_tags tt ON tt.name = pts.tag_name
WHERE NOT EXISTS (
    SELECT 1
    FROM game_object_tags got
    WHERE got.game_object_id = go.id
      AND got.tag_id = tt.id
);

WITH required_game_objects AS (
    SELECT object_name
    FROM (
        VALUES
            ('tide_call'),
            ('towerback')
    ) AS required(object_name)
),
inserted_game_objects AS (
    INSERT INTO game_objects(name)
    SELECT object_name
    FROM required_game_objects required
    WHERE NOT EXISTS (
        SELECT 1
        FROM game_objects go
        WHERE go.name = required.object_name
    )
    RETURNING id, name
),
target_game_objects AS (
    SELECT id, name FROM inserted_game_objects
    UNION ALL
    SELECT go.id, go.name
    FROM game_objects go
    JOIN required_game_objects required ON required.object_name = go.name
),
required_parameters AS (
    SELECT parameter_name
    FROM (
        VALUES
            ('radius'),
            ('damage'),
            ('speed'),
            ('duration'),
            ('sub_attack_range')
    ) AS params(parameter_name)
),
inserted_parameters AS (
    INSERT INTO parameters(name)
    SELECT parameter_name
    FROM required_parameters rp
    WHERE NOT EXISTS (
        SELECT 1
        FROM parameters p
        WHERE p.name = rp.parameter_name
    )
    RETURNING id, name
),
target_parameters AS (
    SELECT id, name FROM inserted_parameters
    UNION ALL
    SELECT p.id, p.name
    FROM parameters p
    JOIN required_parameters rp ON rp.parameter_name = p.name
),
parameter_seed_values AS (
    SELECT *
    FROM (
        VALUES
            ('tide_call', 'radius', 0.8),
            ('tide_call', 'damage', 3.0),
            ('tide_call', 'speed', 4.5),
            ('tide_call', 'duration', 3.0),
            ('towerback', 'sub_attack_range', 1.15)
    ) AS seed(game_object_name, parameter_name, parameter_value)
)
INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT tgo.id, tp.id, psv.parameter_value
FROM target_game_objects tgo
JOIN parameter_seed_values psv ON psv.game_object_name = tgo.name
JOIN target_parameters tp ON tp.name = psv.parameter_name
WHERE NOT EXISTS (
    SELECT 1
    FROM parameter_values pv
    WHERE pv.game_object_id = tgo.id
      AND pv.parameter_id = tp.id
);

UPDATE parameter_values pv
SET value = 5.0
FROM game_objects go, parameters p
WHERE pv.game_object_id = go.id
  AND pv.parameter_id = p.id
  AND go.name = 'dimension_toad'
  AND p.name = 'panic_duration';

WITH required_game_objects AS (
    SELECT object_name
    FROM (
        VALUES
            ('evil_ent')
    ) AS required(object_name)
),
inserted_game_objects AS (
    INSERT INTO game_objects(name)
    SELECT object_name
    FROM required_game_objects required
    WHERE NOT EXISTS (
        SELECT 1
        FROM game_objects go
        WHERE go.name = required.object_name
    )
    RETURNING id, name
),
target_game_objects AS (
    SELECT id, name FROM inserted_game_objects
    UNION ALL
    SELECT go.id, go.name
    FROM game_objects go
    JOIN required_game_objects required ON required.object_name = go.name
),
required_parameters AS (
    SELECT parameter_name
    FROM (
        VALUES
            ('mass'),
            ('radius'),
            ('hp'),
            ('speed'),
            ('damage'),
            ('attack_interval'),
            ('attack_range'),
            ('projectile_speed'),
            ('sub_damage'),
            ('sub_attack_range'),
            ('sub_speed'),
            ('sub_attack_interval'),
            ('pull_mass_limit'),
            ('quantity')
    ) AS params(parameter_name)
),
inserted_parameters AS (
    INSERT INTO parameters(name)
    SELECT parameter_name
    FROM required_parameters rp
    WHERE NOT EXISTS (
        SELECT 1
        FROM parameters p
        WHERE p.name = rp.parameter_name
    )
    RETURNING id, name
),
target_parameters AS (
    SELECT id, name FROM inserted_parameters
    UNION ALL
    SELECT p.id, p.name
    FROM parameters p
    JOIN required_parameters rp ON rp.parameter_name = p.name
),
parameter_seed_values AS (
    SELECT *
    FROM (
        VALUES
            ('evil_ent', 'mass', 10.0),
            ('evil_ent', 'radius', 1.2),
            ('evil_ent', 'hp', 180.0),
            ('evil_ent', 'speed', 0.45),
            ('evil_ent', 'damage', 9.0),
            ('evil_ent', 'attack_interval', 1.8),
            ('evil_ent', 'attack_range', 5.0),
            ('evil_ent', 'projectile_speed', 14.0),
            ('evil_ent', 'sub_damage', 28.0),
            ('evil_ent', 'sub_attack_range', 6.0),
            ('evil_ent', 'sub_speed', 4.0),
            ('evil_ent', 'sub_attack_interval', 12.0),
            ('evil_ent', 'pull_mass_limit', 5.0),
            ('evil_ent', 'quantity', 1.0)
    ) AS seed(game_object_name, parameter_name, parameter_value)
)
INSERT INTO parameter_values(game_object_id, parameter_id, value)
SELECT tgo.id, tp.id, psv.parameter_value
FROM target_game_objects tgo
JOIN parameter_seed_values psv ON psv.game_object_name = tgo.name
JOIN target_parameters tp ON tp.name = psv.parameter_name
WHERE NOT EXISTS (
    SELECT 1
    FROM parameter_values pv
    WHERE pv.game_object_id = tgo.id
      AND pv.parameter_id = tp.id
);

WITH required_tags AS (
    SELECT tag_name
    FROM (
        VALUES
            ('TYPE_Unit'),
            ('CAT_Large'),
            ('CAT_Ranged'),
            ('CAT_CC')
    ) AS tags(tag_name)
),
inserted_tags AS (
    INSERT INTO tags(name)
    SELECT tag_name
    FROM required_tags rt
    WHERE NOT EXISTS (
        SELECT 1
        FROM tags t
        WHERE t.name = rt.tag_name
    )
    RETURNING id, name
),
target_tags AS (
    SELECT id, name FROM inserted_tags
    UNION ALL
    SELECT t.id, t.name
    FROM tags t
    JOIN required_tags rt ON rt.tag_name = t.name
),
target_game_object AS (
    SELECT id
    FROM game_objects
    WHERE name = 'evil_ent'
)
INSERT INTO game_object_tags(game_object_id, tag_id)
SELECT tgo.id, tt.id
FROM target_game_object tgo
JOIN target_tags tt ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM game_object_tags got
    WHERE got.game_object_id = tgo.id
      AND got.tag_id = tt.id
);
