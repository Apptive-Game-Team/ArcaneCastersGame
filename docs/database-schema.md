# Database Schema & Parameters Reference

This document describes the database schema, key tables, and parameter system used to configure gameplay values in the Word Online game server.

---

## Core Tables

The shared game DB schema and data migrations are defined in the
[database repository](../../database/migration). The key gameplay-related
tables are described below:

```
                  +------------------+
                  |      users       |
                  +------------------+
                            |
                            | (owns)
                            v
                  +------------------+    +------------+
                  |   user_magics    |--->|   magics   |
                  +------------------+    +------------+
                                                ^
                                                | (one card is one magic)
                                          +---------------+
                                          |  deck_cards   |
                                          +---------------+
```

### 1. Magic Catalogue & Inventory
- **`magics`**: The card catalogue. One row is one card and one spell.
  - Columns: `id` (bigserial), `name` (varchar unique), `element` (varchar: `Fire`, `Water`, `Lightning`, `Rock`, `Nature`, `Wind`, `None`), `access_type` (varchar).
  - **Rule**: `magics.name` value must match the corresponding Spring magic bean component name in lowercase (e.g. `fire_shot`, `leafair`).
- **`user_magics`**: How many copies of each magic a user owns.
  - Columns: `id`, `user_id`, `magic_id`, `count`.

`cards`, `user_cards` and `magic_cards` are gone. There is no card combination to resolve.

### 2. Player Progress & Decks
- **`users`**: User account credentials, statistics, status, and selected deck reference.
  - Columns: `id`, `mmr` (default 1000), `status` (varchar: 'Online', 'OnMatching', 'OnPlaying').
- **`decks`**: Saved card decks.
  - Columns: `id`, `name`, `user_id`.
- **`deck_cards`**: Junction table detailing which magic cards are in a user's deck.
  - Columns: `id`, `deck_id`, `magic_id`, `count`.

---

## The Dynamic Parameter System

To tweak gameplay metrics without re-compiling Java code, dimensions like range, damage, speed, and mana costs are stored in the DB and loaded dynamically at startup:

- **`game_objects`**: Defines physical objects/prefabs in the game, and the magics themselves.
  - Columns: `id` (bigserial), `name` (varchar, unique, e.g., `leafair_prefab`).
  - `game_objects.name` equals `magics.name` for every magic, which is how a cast reads its own
    `mana_cost` and `range`. There is no cast type left to key them by.
- **`parameters`**: Defines parameter names.
  - Columns: `id` (bigserial), `name` (varchar, unique, e.g., `damage`, `radius`, `speed`, `range`, `mana_cost`).
- **`parameter_values`**: Junction table mapping objects and parameters to numeric values.
  - Columns: `id`, `parameter_id`, `game_object_id`, `value` (double).

### Querying Parameters at Runtime
At runtime, classes can access these values using the [Parameters](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/domain/Parameters.java) domain object.
- **API Call**: `parameters.getValue(objectName, parameterName)`
- **Example Usage**:
  `int damage = (int) parameters.getValue("leafair", "damage");`
  `float range = (float) parameters.getValue("fire_shot", "range");`
- **Scaffolding Requirements**: When adding a new magic spell, default values for parameters (e.g. `mana_cost`, `range`, `aim_shape`) must be populated in `parameter_values` using the spell's Spring bean name, which is also `magics.name` (for casting validations), and the spawned entity's prefab name (for radius/damage calculations).
- **`aim_shape`**: `1` draws a straight aiming line, `0` a circle. The client reads it; the game server does not.
