# Game Loop & Systems Reference

This document details the fixed-rate simulation loop, tick rate, and system update ordering inside the Word Online game server.

## Fixed Ticks & Delta Time

- **Tick Rate (FPS)**: A per-session setting, `GameContext.getTickRate()`. The default is `game.tick-rate` (`GameTickProperties`, 20); a session created with `tickRate` in its `CreateSessionRequest` runs at that rate instead. The shared servers run at **20 FPS**.
- **Target Delta Time**: `1 / tickRate` seconds per tick (50ms at 20 FPS). The loop sleeps until a nanosecond deadline, so 60 really runs 60 frames a second.
- **Delta Calculation**: Measured inside `GameLoop.runLoop()` as the length of the previous frame (never zero), and stored as `deltaTime` in `GameContext`. It is not a fixed step.
- **Time, not frames**: Everything that happens on a period counts seconds of game time, so a match plays out the same at any tick rate. Per-object timers accumulate `deltaTime` and keep the remainder when they fire (`IntervalTimer`). Values stored in the database as frame counts (`bot_personas.reaction_interval_frames`, the `FrameNumGte` trigger) are read as 20-FPS frames (`GameLoop.LEGACY_TICK_RATE`).

---

## The Tick Update Sequence

[WordOnlineLoop.update()](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/service/WordOnlineLoop.java#L127-L159) defines the precise execution order of systems during each tick:

### 1. Early State & Card/Mana Operations
- **`frameDataSystem.earlyUpdate(gameContext)`**:
  - Calculates remaining game time.
  - Accumulates player mana: [ManaCharger.chargeMana(...)](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/service/ManaCharger.java#L38-L43) increases mana every `MANA_CHARGE_INTERVAL` (0.25s of game time) by the player's mana charge rate, capped at max mana.
  - Draws cards: [CardDeck.drawCard(...)](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/service/CardDeck.java#L33-L44) draws cards every `cardDrawInterval` (default 1s of game time, 0.5s during fever) if the player's hand size is below `MAX_CARD_NUM` (6).

### 2. Gameplay Updates & Bot AI
- **`feverTimeSystem.update(gameContext)`**: If the remaining game time falls below the fever time threshold, it activates fever mode (accelerating card draw speeds).
- **`botSystem.update(gameContext)`**: Executes the AI agent ticks for training practice bots or disconnected human fallback bots.

### 3. Match Evaluation
- Evaluates if the match timer has expired.
- **`ResultChecker.checkResult()`**: Checks player health. If either player reaches 0 HP, it triggers game shutdown (`handleGameEnd()`).

### 4. Initialization of New Entities
- **`gameObjectStateInitialSystem.update(gameContext)`**: Scans for newly created GameObjects that are not yet marked `Idle` and switches them to the `Idle` status (allowing them to participate in updates and collisions).

### 5. Component Logic Updates
- **`componentUpdateSystem.update(gameContext)`**: Iterates over all active, non-destroyed GameObjects and invokes `update()` on all registered behaviors (e.g. cloud movements, projectile ticks, summon AI).

### 6. Physics Integration & Collision Processing
- **`physicSystem.update(gameContext)`**: Checks overlap states, resolves overlapping boundaries, triggers collision events, and integrates velocities into spatial positions.

### 7. Entity List Synchronization
- **`gameObjectAddRemoteSystem.update(gameContext)`**:
  - Removes destroyed GameObjects.
  - Mutates component lists by executing `flushComponents()` (calls `start()` or `onDestroy()` on newly added/removed components).
  - Appends newly instantiated GameObjects from the `gameObjectsToAdd` buffer to the active list.

### 8. State Sync
- **`buildSnapshot()`**: Gathers the updated state of all active GameObjects.
- **`frameDataSystem.lateUpdate(gameContext)`**: Transmits the update packet. On the first frame and then every half second of game time (`GameLoop.isSyncFrame`), [SyncFrameDataSystem](file:///Users/jeong-yunseong/development/word-online/dev/game-server/src/main/java/com/wordonline/server/game/service/system/SyncFrameDataSystem.java) broadcasts a full snapshot to force client alignment. The `sync` message carries `tickRate`, which the client uses to size its interpolation.
