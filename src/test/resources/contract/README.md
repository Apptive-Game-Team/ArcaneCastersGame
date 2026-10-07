# Game session contract fixtures

These files are the wire format of the calls this server and the lobby make to each other,
shared with `Apptive-Game-Team/WordOnlineMatching`. The same files exist there under
`src/test/resources/contract/` with identical content.

Session creation, `POST /api/server/game-sessions` (lobby calls this server):

- `create-session-request.json` - what the lobby sends. Verified here by deserializing it
  into this server's `CreateSessionRequest`; verified there by serializing the lobby's own
  `CreateSessionRequest`.
- `session-ready-response.json` - what this server answers. Verified here by serializing
  `SessionReadyResponse`; verified there by deserializing into the lobby's own
  `SessionReadyResponse`. Its `instanceId` is the boot generation of the game server process
  that owns the session; the fixture value is a placeholder, and at runtime it is a fresh
  UUID per process. The lobby stores it on the ticket and compares it with the id on the
  `servers` row, because a restarted server answers on the same domain and port and passes
  health checks while holding none of the previous sessions. Its `mapType` is the map of the
  session, one of `GRASSLAND`, `RIVER`, `FORTRESS`, `GATE` or `FOREST`, decided by the game
  server before the response is built; the fixture value is a placeholder. It is `null` only
  when no session was created.

Session end, `POST /api/internal/game-sessions/{sessionId}/ended` (this server calls the
lobby, authenticated with a pre-issued service token read from `LOBBY_SERVICE_TOKEN_PATH`):

- `session-ended-notification.json` - what this server sends when a session finishes.
  Verified here by serializing `SessionEndedNotificationDto`; verified there by
  deserializing into the lobby's own request DTO. The session id travels in the path, so
  the body carries only the `instanceId` of the process that ran the session: the lobby
  closes the match ticket only if that id still matches the one it recorded, which stops a
  restarted process from closing a ticket it never owned.

Changing a field name or the nesting on one side alone makes the other side's test fail,
which is the point: without these, both repositories' suites stay green through a broken
contract. A mismatch does not surface as an exception at runtime either - the lobby reads
it as a refusal and fails over, so every candidate is rejected and matching returns 503
with nothing in the logs pointing at the cause.

When the contract changes, update both copies in the same pull request pair and say so in
each body.
