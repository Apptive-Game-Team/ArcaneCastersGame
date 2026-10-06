# Developer magic playground

Set PLAYGROUND_ENABLED=true (or playground.enabled=true) only on development game
and lobby servers. The default is disabled. The client entry point exists only in
the Unity Editor. No database migration is required.

Lobby administrators create a session through POST /api/dev/playgrounds. Lobby's
service token calls POST /api/server/playgrounds with ownerId. Game returns
sessionId, server, webSocketUrl, ownerId and expiresAt. The session owns empty
decks and two player bodies, uses the ordinary WordOnline simulation, disables
bots/results, and expires 300 seconds after initialization even with no client.

The owner uses POST /api/dev/playgrounds/{id}/cast (magicId, master, position),
/clear (master; None means both), /immunity (master, enabled), GET /snapshot and
DELETE /{id}. Commands complete on the loop thread and report success/message
and the two faction immunity states. Full command timeout is three seconds.

Clear removes faction-owned non-player objects, including buildings, projectiles
and pending creates. Neutral world objects and both players stay. Silent removal
skips death/destruction callbacks. Immunity blocks positive damage for present
and future faction objects, but permits healing, lifetime expiry and consumption.

Only the owner administrator may subscribe to /game/{id}/frameInfos/{ownerId}.
Existing frame/sync payloads are followed by playgroundEnded with reason
EXPIRED, CLOSED or FAILED. Normal match inputs and spectator subscriptions are
rejected for playground sessions. No ordinary record, status, rating, quest or
recovery path should run for this session type.

When modifying lifecycle code, cover start failure and watchdog teardown as well
as normal expiry: skipping only statistic builder creation still allows end
records and user status changes.

Keep the subscription guard's SessionService dependency lazy through ObjectProvider.
SessionService depends on STOMP infrastructure, which constructs this guard; eager
constructor injection causes a circular dependency and prevents real server startup.
Verify an enabled server's actual startup in addition to isolated session tests.
