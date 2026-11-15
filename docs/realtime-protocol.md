# Real-time protocol (STOMP)

> **Status: IMPLEMENTED** (backend, 2026-11-15): every destination and event below exists and is covered by
> `StompGameFlowIntegrationTest` and the service parity tests. Originally written by the transformer from the
> legacy `/userStatus` messages, with user decisions D1-D24 from `docs/migration-plan.md`.
> The backend agent owns this file once it implements a destination: replace "proposed" with
> "implemented" per row and fix any shape that changed. The frontend agent codes against this file.
> Java types and service rules: `docs/migration-plan.md` → "Contract for backend: invitation and game services"
> (event records implement `com.cardgame.realtime.RealtimeEvent`; destinations are in `com.cardgame.realtime.Destinations`).
> Legacy message catalogue: `docs/migration-plan.md` → "Legacy inventory" → "WebSocket messages".

## Connection

| Item | Value |
|---|---|
| Endpoint | `/ws` (native WebSocket, same origin through the Angular dev proxy) |
| Auth | The HTTP session cookie (`JSESSIONID`) from `POST /api/auth/login`, sent with the WebSocket handshake. No `?username=`; the principal is the logged-in player. A handshake without a valid session is refused with HTTP 401 (no socket). |
| CSRF | **Implemented:** send the raw value of the `XSRF-TOKEN` cookie in the STOMP `CONNECT` frame header `X-XSRF-TOKEN` (same name and value as the HTTP header). The raw (unmasked) value is accepted. The cookie is rotated at login, so read it right before connecting (connect **after** login). A missing/wrong token → STOMP `ERROR` frame and no `CONNECTED`. |
| Authorization | SUBSCRIBE allowed to `/user/queue/**` and `/topic/presence` for any logged-in player; `/topic/game/{id}` only for a player of that live game (D14); SEND only to `/app/**`. Anything else → STOMP `ERROR` frame (the subscription is not created). Treat an `ERROR` frame as fatal: disconnect and reconnect. |
| Logout | `POST /api/auth/logout` closes every STOMP connection opened from that browser session (→ `PlayerOffline` when it was the player's last one, D15). |
| Online | A player is online while ≥ 1 STOMP connection is open (D15): connect the socket in the lobby **and** on the game page. |
| Application prefix | `/app` (client SEND) |
| Broker prefixes | `/topic` (broadcast), `/queue` (per user, via `/user/queue/...`) |
| Payload | JSON, UTF-8. Every event has a `type` field (discriminator) and a `sentAt` ISO-8601 instant. |
| Heartbeat | Server 10 s / 10 s (`heart-beat:10000,10000`). Clients should request e.g. `10000,10000` too. |
| Error delivery | `Error` and `ClaimRejected` on `/user/queue/errors`: `Error` goes only to the tab that sent the failing frame; `ClaimRejected` goes to all of the claimer's tabs. |

Rule: commands that need validation and a clear error (create/accept/decline invitation) go through REST
(ProblemDetail errors). High-frequency in-game actions (claims) go through STOMP SEND.

## Destinations (implemented)

### Subscriptions (server → client)

| Destination | Who subscribes | Event `type`s | Replaces legacy |
|---|---|---|---|
| `/topic/presence` | every logged-in client (lobby) | `PlayerOnline`, `PlayerOffline`, `PlayerStatusChanged` (`ONLINE` / `IN_GAME`, D16) | `login`, `logout` |
| `/user/queue/invitations` | every logged-in client | `InvitationReceived`, `InvitationUpdated` | `invitation` |
| `/user/queue/games` | every logged-in client | `GameStarted` | `redirect` → `game.jsp` |
| `/topic/game/{gameId}` | the game's players only (subscription refused for others) | `ScoresUpdated`, `RoundStarted`, `GameFinished` | `scoreUpdate`, `finTour` (S→C), `redirect` → `WinnerPage.jsp` |
| `/user/queue/errors` | every logged-in client | `ClaimRejected`, `Error` (ProblemDetail-like) | – (legacy fails silently) |

### Client sends

| Destination | Payload | Effect | Replaces legacy |
|---|---|---|---|
| `/app/game/{gameId}/claim` | `{"claim":"SAME_COLOR" \| "SAME_SYMBOL" \| "BOTH", "dealVersion": 7}` | Server judges with `GameEngine.claim` (D8). `CORRECT` → `ScoresUpdated` on the game topic. `WRONG` or `STALE` → nothing broadcast; `ClaimRejected {reason}` to the sender only. No penalty. | `updateScore` |

Legacy `invite`, `response`, `finTour` (C→S) and `finPartie` have **no STOMP equivalent**:
invitations are REST (`POST /api/invitations`, `POST /api/invitations/{id}/accept|decline`) and the
server drives round changes and the end of the game with its own timer.

### Related REST (snapshot / recovery)

| Endpoint | Purpose |
|---|---|
| `GET /api/players/online` | presence list when the lobby opens (then follow `/topic/presence`) |
| `GET /api/invitations/pending` | pending invitations of the caller (as inviter or invitee) when the lobby opens |
| `POST /api/invitations`, `POST /api/invitations/{id}/accept`, `/decline` | invitation commands (`201`/`200` `InvitationDto`; 400/404/409 ProblemDetail) |
| `GET /api/games/current` | snapshot of the caller's live game, or `204`: the lobby redirects to `/games/:id` after a reload (D17) |
| `GET /api/games/{id}` | full `GameSnapshot` when the game page opens or reconnects (then follow `/topic/game/{id}`); 403 not a player, 404, 409 finished, 410 abandoned (D25) |
| `GET /api/games/{id}/result` | result page; 403 not a player, 404, 409 still in progress, 410 abandoned by a server restart (D25) |

## Event shapes (implemented)

```jsonc
// /topic/presence
{ "type": "PlayerOnline",  "username": "alice", "status": "ONLINE" | "IN_GAME", "sentAt": "..." }   // IN_GAME after a reconnect during a game (D17)
{ "type": "PlayerOffline", "username": "alice", "sentAt": "..." }                                  // last tab closed (D15)
{ "type": "PlayerStatusChanged", "username": "alice", "status": "ONLINE" | "IN_GAME", "sentAt": "..." }
// D16: status is "ONLINE" | "IN_GAME"; sent when a player enters or leaves a running game
{ "type": "PlayerStatusChanged", "username": "alice", "status": "IN_GAME", "sentAt": "2026-11-15T20:15:30Z" }

// /user/queue/invitations
// settings: totalRounds 1-20 (default 3), roundSeconds 5-120 (default 10) - D10; no combinations - D9
{ "type": "InvitationReceived", "invitationId": 12, "inviter": "alice",
  "invitees": ["bob", "carol"], "settings": { "totalRounds": 3, "roundSeconds": 10 },
  "expiresAt": "...",                     // createdAt + 60 s (D7)
  "sentAt": "..." }
// Sent to the inviter AND every invitee on each change (D7):
//   PENDING  -> someone accepted, still waiting for others
//   ACCEPTED -> all invitees accepted; GameStarted follows on /user/queue/games
//   DECLINED -> an invitee declined; the invitation is cancelled for everyone (declinedBy is set)
//   EXPIRED  -> 60 s passed without all acceptances; cancelled for everyone
{ "type": "InvitationUpdated", "invitationId": 12,
  "status": "PENDING" | "ACCEPTED" | "DECLINED" | "EXPIRED",
  "accepted": ["bob"], "declinedBy": null, "sentAt": "..." }

// /user/queue/games
{ "type": "GameStarted", "gameId": 34, "sentAt": "..." }

// /topic/game/{gameId}
{ "type": "ScoresUpdated", "gameId": 34, "scoredBy": "alice",
  "scores": [ { "player": "alice", "score": 1 }, { "player": "bob", "score": 0 } ],
  "deal": { "dealVersion": 8,
            "playerCards": [ { "player": "alice", "symbol": "B", "color": "blue" } ],
            "centerCard": { "symbol": "B", "color": "blue" } },
  "sentAt": "..." }
{ "type": "RoundStarted", "gameId": 34, "round": 2, "totalRounds": 3,
  "roundEndsAt": "...", "scores": [ ... ], "deal": { ... }, "sentAt": "..." }
// D3/D4/D5: winner/loser are null when there is no single one; draw == (winner == null)
{ "type": "GameFinished", "gameId": 34,
  "draw": false,
  "winner": "alice", "loser": "bob",
  "leaders": ["alice"], "topScore": 3,
  "trailers": ["bob"], "bottomScore": 1,        // trailers is empty when every score is equal
  "scores": [ ... ], "sentAt": "..." }
// example draw (all zero):
// { "type": "GameFinished", "draw": true, "winner": null, "loser": null,
//   "leaders": ["alice", "bob"], "topScore": 0, "trailers": [], "bottomScore": 0, ... }

// /user/queue/errors
{ "type": "ClaimRejected", "gameId": 34, "reason": "WRONG" | "STALE", "currentDealVersion": 8, "sentAt": "..." }
{ "type": "Error", "status": 409, "title": "...", "detail": "...", "sentAt": "..." }
```

Notes:

- Scores are a **list in player order**, not a JSON object, so order is stable on the client.
- Colors on the wire stay lower-case (`"red"`, `"blue"`, `"green"`) like legacy (`CardColor.wireValue()`).
- `dealVersion` starts at 1 and increments every time the deal changes (correct claim or new round).
  Only the first correct claim on a given version scores (D8).
- Game events go only to the game's players (D14); a user may have several tabs, all receive them (D15).
- A reconnecting player gets the current state from `GET /api/games/{id}` (D17).
- `GameSnapshot` (REST) = `{ gameId, players, settings: {totalRounds, roundSeconds}, phase, round, roundEndsAt, scores, deal }`.
- `GameResult` (REST) = `{ gameId, finishedAt, draw, winner, loser, leaders, topScore, trailers, bottomScore, scores }` (same rule as `GameFinished`).
- The game id is the `games` row id, created when the game **starts** (D22).
- Order on the user queue at game start: `InvitationUpdated(ACCEPTED)` (invitations queue), then `GameStarted` (games queue).
- `Error` on `/user/queue/errors` comes from `@MessageExceptionHandler` when a STOMP handler throws a domain error (e.g. 403 not a player, 404 game not live).

## Client rules (joining a game)

1. On `GameStarted` (or when `GET /api/games/current` returns a game), **subscribe to `/topic/game/{id}` first**,
   then call `GET /api/games/{id}`.
2. Keep the snapshot's `deal.dealVersion`. Ignore any `ScoresUpdated` / `RoundStarted` whose `deal.dealVersion` is
   lower than or equal to it; `GameFinished` always applies.
3. On `409` from the snapshot, the game is finished: go to `/games/{id}/result`. On `410` (D25), show the detail and go
   to `/lobby`.

## Legacy → new mapping

| Legacy `action` | Direction | New |
|---|---|---|
| `login` / `logout` | S → all | `/topic/presence` `PlayerOnline` / `PlayerOffline` (from STOMP connect/disconnect, not from the login servlet) |
| `invite` | C → S | `POST /api/invitations` |
| `invitation` | S → others | `/user/queue/invitations` `InvitationReceived` (invitees only) |
| `response` | C → S | `POST /api/invitations/{id}/accept` / `decline` (decline cancels for everyone, D7) |
| `redirect` (game.jsp) | S → players | `/user/queue/games` `GameStarted` + `GET /api/games/{id}` |
| `updateScore` | C → S | `/app/game/{id}/claim` (the client sends a claim, never scores) |
| `scoreUpdate` | S → all | `/topic/game/{id}` `ScoresUpdated` |
| `finTour` | C → S | removed (server timer) |
| `finTour` | S → all | `/topic/game/{id}` `RoundStarted` |
| `finPartie` | C → S | removed (server timer) |
| `redirect` (WinnerPage.jsp) | S → all | `/topic/game/{id}` `GameFinished` + `GET /api/games/{id}/result` |
| `stateUpdate` | – | dropped (dead code) |

## Changelog

| Date | Author | Change |
|---|---|---|
| 2026-11-15 | transformer | Initial proposal derived from legacy `/userStatus`. |
| 2026-11-15 | transformer | D25: 410 for abandoned games on snapshot/result. No STOMP change (abandoned games had no live session, so no event is sent). |
| 2026-11-15 | transformer | Aligned with the services contract: `PlayerOnline.status`, `PlayerStatusChanged`, REST for pending invitations and current game, error codes, D22 game id. |
| 2026-11-15 | transformer | Applied D7 (decline/expiry statuses), D8 (`ClaimRejected`, `dealVersion`), D3-D5 (draw-aware `GameFinished`), D9/D10 (settings), D16 (`IN_GAME`). |
| 2026-11-15 | backend | Implemented all destinations and events (status IMPLEMENTED). Added: handshake 401, ERROR-frame behaviour for CSRF/forbidden SUBSCRIBE, logout closes sockets, `Error` only to the sending tab, heartbeats 10 s. No shape changes. |
| 2026-11-15 | backend | Confirmed the CSRF `CONNECT` header (`X-XSRF-TOKEN`, raw cookie value). Added the `PlayerStatusChanged` example. No STOMP destination implemented yet (auth only). |
