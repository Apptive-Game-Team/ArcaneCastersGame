# Offline Fire Shot example capture

## Ordered representative scenarios

Run `./gradlew test --tests com.wordonline.server.game.preview.MagicScenarioPreviewTest
-PpreviewExport=true` to export 21 recordings (30 scenarios) into `build/previews/scenarios/`.
Copy those generated JSON files into the client `Assets/Resources/MagicPreviews/`.
The old exporter below remains a version-1 compatibility fixture; do not use it to
overwrite the new multi-scenario Fire Shot asset during normal regeneration.

Version 2 has root `magic`, `frameDuration`, `source`, and ordered `scenarios`.
Each scenario carries `id`, Korean/English caption, duration and real object DTO
frames plus hit events. The fixture requests real snapshots to include initial
HP, effects and master; create DTOs alone do not include gauges. Normalize both
projection endpoints (`start` and `end`), events and object IDs consistently.
Independent collision updates/events are sorted because collision pairs use a
HashSet and their enumeration has no stable wire order.

Each scenario also exports `fixtureTargetIds`, containing only normalized passive
target IDs, and `parameters`, containing the fixture's exact `owner.key` reads.
The client uses them to initialize native presentation without the live parameter
cache. Initial ElectricSlime death-energy absorbers are not fixture targets;
never infer fixture identity from type, team or initial-frame membership.
These recording metadata fields are not production wire DTO changes.

Production magics, initializers, attacks, effects, lifecycle and physics run
offline. The parameter store, caster, passive targets and the preview-only
omission of automatic DeathField.spawn are fixtures.
Fixed illustrative parameters intentionally keep demonstrations short; they are
not live balance. Unknown prefab/parameter reads fail. Capturing twice must yield
identical JSON. Assertions cover ground knockback/death rubble, air self-destruct,
Towerback melee and anti-air splash, healing, Burn/Wet interaction, Shock, ongoing
SandStorm damage, Frenzy neutral ownership/attack acceleration/restoration and
both EarthCall conversions followed by the resulting creature's attack.

This is representative coverage, not a claim that all magic or elemental
interactions are covered. No production gameplay or database change is required.

The additional batch covers water/lightning shots, piercing WindBlade,
reflecting RollingRock, Fire/Wind drops, Water/Wind explosions, both RockBlast
remnant sizes and MiniRock/ThunderBird/WaterSlime swarms. Assertions also cover
friendly Overcharge/extra shots, ThunderBird death-energy transfer (without a death field) and
WaterSlime trail/Wet. All three swarm attack masks are GROUND; aerial appearance
does not imply anti-air attacks. WaterExplosion currently applies Burn and launch,
while ordinary Drop has no Burn/Knockback provider; recordings preserve this.

One-cast counts come from PreviewSummonQuantities, an explicit V001 parameter_id=9
snapshot: MiniRock 2, EmberSpirit 5, SeedSpirit 4, ThunderBird/WaterSlime 3,
VineSpirit/ZapMouse 2; ordinary summons default to 1. Do not use a universal 3.
Quantity on GrassGenerator counts later fields, not the initial building; keep
initial cast bodies separate from offspring, evolution inputs and support allies.
Capture.cast checks the queued initial batch against ObjectSummoningMagic and the
independent snapshot. Parameter ownership comes from the spell, not prefab aliases.
The fixture runs the real spawn spell then stages only
newly pending swarm positions deterministically inside its +/-1 spawn range,
preserving height. This does not replace attack/movement/effect logic. Its
Vector3 is also retained by the already queued CreatedObjectDto: mutate those
coordinates before capture instead of replacing the Vector3, or the first create
frame keeps the random spawn coordinates despite later updates being fixed.
Avoid simultaneous first-contact ties in projectile fixtures: sorting DTOs does
not make HashSet collision processing deterministic. Water/Electric shot radius
is an explicit illustrative 0.3 so only the primary target collides first;
the adjacent target still receives real impact splash damage.

Validation for this batch: full server suite 664 passed, repeated capture equal.

## Remaining catalog expansion

Run `./gradlew test -PpreviewExport=true` to export the complete 85-magic catalog
(129 scenarios). `RemainingMagicPreviewTest` explicitly adds 64 names discovered
from production Spring magic beans, including existing PVE nests. Every remaining
bean must have an explicit scenario registration; unknown constructor dependencies,
prefabs and parameter reads fail. Each parameterized row captures one magic twice
and compares complete JSON, keeping memory bounded rather than retaining two
all-catalog trees. Full suite: 728 tests passed with Gradle's default 512MB test heap.

Read `captureAll`, `support`, and `secondary` in that test as the scenario matrix:
supported ground/air attacks precede support, aura, multiple-target, evolution and
remnant/energy-transfer situations. Special assertions cover RockMage concentrated double
shots and multiple targets; EvilEnt light-target grab/pull/FireFist/Burn versus
heavy-target punch; CloudDragon chain and Wet aura; SeaSerpent aligned beam hits,
off-axis miss and water trail; FireLord's delayed children; DimensionToad panic
and alternating children; StormStag tiers 2/3/4 and electric impact; Fire/Magma
auras; rock remnants and electrical death-energy transfer; seed evolution and
TreeGolem healing/trail. The 35 generic combat_death cases have been removed.

Automatic death-created ground fields are intentionally omitted at their source
with a scoped test-only DeathField static mock. Production code is unchanged; no
field-type DTO filtering is allowed, since it would hide legitimate trails/areas
and leave dangling references. Rock remnants and energy absorption remain.
SeaSerpent water trails, TreeGolem leaf trails, Crater landing FireFields and
WindSpirit's self-destruct attack are still asserted.

Area units' ordinary attack cases now have separated enemy groups: Tower,
Towerback (air), BubbleSpirit, FireSpirit, CloudDragon, SeaSerpent, ElectricTower,
DragonTower, BombSprite, FireworkTower, MagmaSpirit and TitanRemnant. Require hit
events on multiple fixture IDs from the same actor in one damage batch; CloudDragon
requires the same ChainLightning ID across frames. Final HP alone could represent
multiple independent single-target attacks and is not adequate evidence. Small
splash and collinear beam layouts stay within real attack ranges. Blast parameters
for BombSpriteBomb (2), FireworkShell (1.5), MagmaFist (1), TitanFist (1.25) use the
baseline snapshot instead of the generic .4 collider fixture.

Support cases verify LifeTree healing, Bubble protection, Rallying movement and
Inspired, real ManaCharger rate rise/restoration, RepairTotem TTL freeze versus an
unprotected control and expiry after the aura, Leafair allied HP/TTL recovery,
WindTotem push and delayed ShockTrap activation. SpiritBomb records ally HP
10000→5000, four roughly one-second beam ticks totaling rounded 70% absorbed HP,
actual beam width/duration and energy absorption. WillOWisp changes ownership
and attacks the former side. Crater separately shows enemy collision and neutral
FireField landing; Firework shell creation occurs after its aim projection.

Preserve current mechanics, including surprising ones: owned LeafField's provider
currently targets enemies, so GrassGenerator heals/snares the enemy, not the ally;
AquaArcher's projected WaterShot alone does not apply Wet. StormStag has no
electrical death-energy component. Four legacy slime nests have no matching client
runtime art and use an explicitly documented SeedNest preview proxy. This is not
exhaustive coverage of every elemental permutation or exact live database balance.

### Fixture traps

- Prefab initializer discovery must key by Spring bean name. SeedSpirit inherits
  LeafSlime's initializer type field and EmberSpirit uses the `fire_slime_prefab`
  bean; indexing only `prefabType` silently wires the wrong initializer.
- Integer parameter reads need nonzero integer intervals: VineColony reads
  `attack_interval` with `intValue`. An illustrative 0.8 truncates to zero and its
  real catch-up loop never terminates, exhausting heap. Use 1 in this fixture;
  do not modify production mechanics or claim this caused an unrelated app crash.
- Seed random draws before constructors cache trajectories (especially Crater),
  not only after spawning. Seed `ThreadLocalRandom` draws and random unit vectors
  in the test scope; retain real production algorithms and spawn heights.
- CollisionPair reorders endpoints by identity hash as well as storing pairs in
  a HashSet. Sorting DTOs or replacing only the collection does not stabilize
  orientation. The expanded fixture uses a LinkedHashSet and a per-physics-tick
  stub-only Pair construction scope preserving broadphase argument order. Close
  mocks each tick and clear inline mocks per capture to avoid retention.
- Keep position projection endpoints' flat `x/y/z` when exporting. The preview
  consumer must not confuse them with a game object's nested `position` vector.
- Passive targets have trigger-only colliders. CombatRange does not count those as
  body radii, whereas overlapSphereAll uses hitboxes. Place MagmaFist victims within
  its actual radius; do not inflate the blast just because a sphere fixture worked.

Run `./gradlew test --tests com.wordonline.server.game.preview.FireShotPreviewTest
-PpreviewExport=true --rerun-tasks` (one command) to write
`build/previews/fire_shot.json`. Normal tests do not export files.

The test runs the real spell, initializer and physics against stationary fixtures,
normalizes object IDs and asserts direct damage, splash damage, a missed distant
target and one damage-confirmed impact. Parameters are fixed illustrative values
from database V001, not a promise of current live balance. No DB or server is needed.
Copy the output to the client's `Assets/Resources/MagicPreviews/fire_shot.json`.
Schema version 1 is a local example format, not a new public game protocol.

Windows/JDK 21 test troubleshooting: if Gradle fails before running tests with
`Unable to establish loopback connection` / `UnixDomainSockets.connect0: Invalid
argument`, a named-pipe-backed JDK selector may be failing. In the affected shell,
setting `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<nonexistent-directory>`
forced the JDK's TCP fallback and allowed the test suite to run. Use a verified
nonexistent path, keep this a per-invocation workaround, and do not change the
project's runtime configuration for this machine-specific failure.
