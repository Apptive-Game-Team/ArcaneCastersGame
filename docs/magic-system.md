# Magic System Reference

This document explains the spell-casting, card recipe parsing, and magic execution processes in the Word Online game server.

---

## The Spell Casting Flow

Casting is two steps: the player picks a card from hand, which decides the magic, and then picks a
position, which casts it. The cast follows a strict validation pipeline from client input to runtime
execution:

```
Client (WS JSON)
   |
   v
[InputController] -- (Validates authorization token)
   |
   v
[MagicInputHandler.handleInput()]   <- { "type": "useMagic", "magicId": 34, "id": 7, "position": {...} }
   |
   +--> 1. Check if the card is in hand (PlayerData.cards contains magicId)
   +--> 2. Look the magic up (DatabaseMagicParser.parseMagic)
   |      |--> Check if the id names a known magic
   |      |--> Check if user has unlocked the magic in their inventory
   +--> 3. Calculate target distance vs the magic's own range parameter
   +--> 4. Deduct the card and its mana cost (PlayerData.useCard)
   +--> 5. Call magic.run()
   +--> 6. Return the card to the bottom of the player's deck
   +--> 7. Publish execution status (STOMP event)
```

---

## Magic Lookup & Database Binding

A card is one magic, so there is no combination to resolve. Cards are looked up by id in
[DatabaseMagicParser.java](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/domain/magic/parser/DatabaseMagicParser.java):

1. **Spring Bean Matching**: At startup, `DatabaseMagicParser` queries the `magics` table:
   - For each magic row, it checks if a Spring component bean matching `magics.name` exists (e.g., `@Component("leafair")` for [LeafairMagic.java](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/domain/magic/implement/drop/LeafairMagic.java)).
   - Copies `magics.id`, `magics.name` and `magics.element` onto the bean and registers it: `magicIdMap.put(magic.id, magicBean)`.
2. **Id Lookup**: `parseMagic(userId, magicId)` reads that map. The id comes from the card the player is holding, which the client sends as `magicId`.
3. **Ownership Validation**: Before executing a parsed spell, `parseMagic` verifies the player owns the magic using `magicRepository.existUserMagic(userId, magic.id)`.

## Aiming

`selectCard` and `unselectCard` carry the same `magicId` and only tell the server which card the
player is aiming with. [CardSelectVisualizer](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/service/CardSelectVisualizer.java)
puts an idle aura of `magics.element` on the caster and takes it off again. A magic whose element is
`None` gets no aura, the way a cast type card used to get none.

---

## Spell Execution (`Magic.run()`)

All spells extend [Magic.java](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/domain/magic/Magic.java) and implement:
`public abstract void run(GameContext gameContext, Master master, Vector3 position);`

### Basic Magic Subclasses:
- **`AbstractDropMagic`**:
  - Spawns a GameObject at target position, but sets initial height `Z` to `GameConfig.DROP_MAGIC_INITIAL_HEIGHT`.
  - The object falls under the control of a `Drop` component.
- **`AbstractShotMagic`**:
  - Spawns a projectile GameObject at the caster's location.
  - Attaches a `Shot` component and sets target coordinates to calculate flight vectors.

---

## Scaffolding Guidelines

When adding a new magic spell, developers use the following steps:

1. **Create the Magic Class**:
   - Save the class under the correct element family package (e.g. `domain/magic/implement/drop/`).
   - Annotate with `@Component("magic_name")` in lowercase.
2. **Database Registration**:
   - Add a versioned Flyway migration under `../database/migration/` and
     publish it before game-server code that requires the new magic.
   - Seed a row in `magics` (naming must match the Spring component name exactly) with its `element`.
   - Register the default gameplay parameters (such as `mana_cost`, `range`, `damage`, `radius`) in `parameter_values`, keyed by a `game_objects` row of the same name as the magic.
3. **Define a Prefab Type**:
   - If the spell spawns a new physical entity, define a new `PrefabType` and implement a corresponding `PrefabInitializer` (see [Prefab System Reference](file:///Users/jeong-yunseong/development/word-online/dev/game-server/docs/prefab-system.md)).
