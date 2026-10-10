# Server-generated magic previews

`preview/PreviewRecorder` runs the production magic and prefab implementation with
one immutable database parameter snapshot. `RepresentativePreviewScenarios` and
`RemainingPreviewScenarios` own the ordered 85-magic / 129-situation registry.
The old offline capture tests remain independent deterministic mechanical regression
fixtures; their exported illustrative values are no longer player assets.

`MagicPreviewService` starts one background worker at application readiness.
`POST /api/admin/invalidate` queues a fresh generation after invalidating game data.
Repeated invalidations coalesce; an obsolete generation is never published.
Each magic can fail independently. No SessionService, normal loop lifecycle,
statistics, user, rating, quest or STOMP path runs. Every capture owns its context,
physics, pending objects and DTO buffers, and uses the same object/physics system order.

Service tokens with `WORDONLINE_SERVER` can call:

- `GET /api/server/magic-previews`: status (`generating`, `ready`, `failed`),
  revision and `magics` entries (`name`, SHA-256 `hash`, UTF-8 `bytes`, `available`).
- `GET /api/server/magic-previews/{name}?revision=...`: compact v2 JSON.
  Revision mismatch is 409; unavailable magic is 404; generation in progress is 503.
  Both responses use Cache-Control: no-store. The recording ETag is its content hash.

The revision hashes the complete catalog and reflects emitted simulation content.
A new generation uses normal production random draws; byte-identical captures
across server restarts are not promised. Client file hashes still avoid downloading
unchanged content. No global random generator is seeded for previews.

Defaults under `preview` are 512 objects, 600 ticks per scenario and 4 MiB per magic.
Non-finite values and zero intervals fail that recording, including fractional
intervals that truncate to zero when an initializer requests an integer. These
guards belong to the capture's parameter source, never to ordinary gameplay.
Unknown constructor dependencies, prefabs and scenarios produce unavailable entries.

Only passive target creation replaces an initializer, in that context. Production
prefabs resolve by Spring bean name and read the snapshot, including constructor
parameters. Death-field suppression is an explicit preview opt-in; trails, landing
fields, rock remnants and energy remain native. Do not mutate PrefabProvider globally.

Destroyed during `start()` does not imply failure: WaterField merges with an existing
field and destroys its duplicate. Reject captures through the explicit failure
callback, not a Destroyed check after creation. DTOs are converted immediately to
JSON trees before retained position references can change.

Validation: `./gradlew test` covers all registrations, snapshot damage/quantity
changes, invalidation races, response copies and partial failures. Legacy regression
exporters stay under tests; this endpoint never calls them. Deploy game, lobby, client.
