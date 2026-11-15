# Real-time protocol (STOMP)

> **Status: PROPOSAL** (written by the transformer from the legacy `/userStatus` messages; updated with
> user decisions D1-D21 from `docs/migration-plan.md`).
> The backend agent owns this file once it implements a destination: replace "proposed" with
> "implemented" per row and fix any shape that changed. The frontend agent codes against this file.
> Legacy message catalogue: `docs/migration-plan.md` → "Legacy inventory" → "WebSocket messages".

## Connection

| Item | Value |
|---|---|
| Endpoint | `/ws` (native WebSocket, same origin through the Angular dev proxy) |
| Auth | The HTTP session cookie from `POST /api/auth/login`. No `?username=`; the principal is the logged-in player. Unauthenticated CONNECT is refused. |
| CSRF | Send the CSRF token in the STOMP `CONNECT` headers (header name: `X-XSRF-TOKEN`) - backend to confirm |
| Application prefix | `/app` (client SEND) |
| Broker prefixes | `/topic` (broadcast), `/queue` (per user, via `/user/queue/...`) |
| Payload | JSON, UTF-8. Every event has a `type` field (discriminator) and a `sentAt` ISO-8601 instant. |
| Heartbeat | 10 s / 10 s (proposed) |

Rule: commands that need validation and a clear error (create/accept/decline invitation) go through REST
(ProblemDetail errors). High-frequency in-game actions (claims) go through STOMP SEND.

## Destinations (proposed)

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
| `GET /api/games/{id}` | full `GameSnapshot` when the game page opens or reconnects (then follow `/topic/game/{id}`) |
| `GET /api/games/{id}/result` | result page |

## Event shapes (proposed)

```jsonc
// /topic/presence
{ "type": "PlayerOnline",  "username": "alice", "sentAt": "..." }
{ "type": "PlayerOffline", "username": "alice", "sentAt": "..." }

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
- `GameSnapshot` (REST) = `{ gameId, players, settings, phase, round, totalRounds, roundEndsAt, scores, deal }`.

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
| 2026-11-15 | transformer | Applied D7 (decline/expiry statuses), D8 (`ClaimRejected`, `dealVersion`), D3-D5 (draw-aware `GameFinished`), D9/D10 (settings), D16 (`IN_GAME`). |
