# Offline Fire Shot example capture

## Ordered representative scenarios

Run `./gradlew test --tests com.wordonline.server.game.preview.MagicScenarioPreviewTest
-PpreviewExport=true` to export nine recordings into `build/previews/scenarios/`.
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

Production magics, initializers, attacks, effects, lifecycle and physics run
offline. Only the parameter store, caster and passive targets are fixtures.
Fixed illustrative parameters intentionally keep demonstrations short; they are
not live balance. Unknown prefab/parameter reads fail. Capturing twice must yield
identical JSON. Assertions cover ground knockback/death rubble, air self-destruct,
Towerback melee and anti-air splash, healing, Burn/Wet interaction, Shock, ongoing
SandStorm damage, Frenzy neutral ownership/attack acceleration/restoration and
both EarthCall conversions followed by the resulting creature's attack.

This is representative coverage, not a claim that all magic or elemental
interactions are covered. No production gameplay or database change is required.

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
