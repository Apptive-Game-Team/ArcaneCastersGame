# Offline Fire Shot example capture

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
