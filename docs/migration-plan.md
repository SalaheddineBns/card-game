# Migration plan: legacy servlet/JSP card game → Spring Boot + Angular

Owner: transformer (migration lead). Backend and frontend agents update the **Status** column when they
finish their part; only the transformer sets `verified`.

- Architecture decisions: `docs/adr/0001-target-architecture.md`
- Names: `docs/GLOSSARY.md` (read it before naming anything)
- Real-time messages: `docs/realtime-protocol.md`
- Legacy code (read-only): `legacy/` (paths below are relative to `legacy/src/main/`)
- Line numbers refer to the files as moved by `git mv` (unchanged content).

Statuses: `todo` → `specified` → `backend` → `frontend` → `verified`.

## The game in one paragraph

"Speedest Game": 2+ logged-in players play simultaneously. Every player holds one card (a letter
`A-F,H-L` and a color `red/blue/green`), and there is one shared **center card** whose letter is taken
from one player's card and whose color from (possibly another) player's card. Each player has three
buttons: **Same Color**, **Same Format** (same letter) and **The Two** (both). A correct press gives
+1 point (a wrong press costs nothing), and then **everybody gets new cards** and a new center card.
A game has N rounds of T seconds; when the last round's timer runs out, the highest score wins, the
lowest score loses, the game and participations are saved and player stats are updated.

## Feature table

| # | Feature | Legacy sources | Backend (REST / STOMP) | Frontend (route / components) | Parity tests | Status | Notes / decisions |
|---|---------|----------------|------------------------|-------------------------------|--------------|--------|-------------------|
| 1 | Register | `controller/RegisterServlet.java`, `webApp/index.jsp` (signup modal L49-116, `validateForm` L157-171) | `POST /api/auth/register` `{username, password, age, gender?}` → `201` `PlayerDto`; `409` ProblemDetail if username taken (case-insensitive); `400` validation | `/auth/register` · `RegisterPage` (password + confirm, terms checkbox: client-side only) | — (controller tests) | verified | Fix: bcrypt (B18), unique username (B19). **D18**: username 3-20 chars `[A-Za-z0-9_-]`, unique ignoring case; password ≥ 8 chars; legacy accounts not migrated. **D11**: `gender` optional enum `MALE`/`FEMALE`/`OTHER` (select). Age ≥ 1 as legacy. Confirm-password check is client-side only. On success navigate to `/auth/login` with a success toast. **D21**: French UI texts. **Review (transformer, 2026-11-15)**: implementation matches; backend ✔ (G1 fixed: HTTP-level validation tests). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 2 | Login | `controller/LoginServlet.java` L66-112, `index.jsp` login modal L118-142 | `POST /api/auth/login` `{username, password}` → `200` `PlayerDto` (session cookie); `401` ProblemDetail. `GET /api/auth/me` → `200` `PlayerDto` / `401`. CSRF token via cookie `XSRF-TOKEN` | `/auth/login` · `LoginPage`; `authGuard` on all other routes | — | verified | Failure message as legacy ("Invalid username or password", in French per D21). New code stores only the principal. Presence is driven by STOMP connect (row 7), not by login. **Review**: implementation matches; backend ✔ (G2 fixed). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 3 | Logout | `controller/LogoutServlet.java`, `home.jsp` L56-58 | `POST /api/auth/logout` → `204`, invalidates session, closes the user's STOMP sessions (→ `PlayerOffline` when the last one closes, D15) | navbar "Logout" button → `/` | — | verified | Logout closes the browser session's STOMP connections (`RealtimeSessionRegistry`). **Review**: backend ✔ (logout closes STOMP sessions, covered by `StompGameFlowIntegrationTest`). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 4 | Landing page | `webApp/index.jsp` (`main` L144-150), `cover.css`, `images/Bg.jpg` | — | `/` · `LandingPage` ("Speedest Game", "Jouer" → login) | — | verified | **D21**: French UI. **Review**: no backend part. **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 5 | Profile view | `controller/ProfileServlet.java#doGet` L53-70, `webApp/profile.jsp` (jQuery AJAX L9-35) | `GET /api/players/me` → `PlayerProfileDto {username, age, gender, gamesPlayed, victories, averageScore}` | `/profile` · `ProfilePage` | — | verified | Fix: no more `?username=` and never the password (B20). **D12**: click ratios not shown and not stored. **Review**: backend ✔ (no password, no ratios, `PlayerControllerTest`). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 6 | Profile edit | `ProfileServlet#doPost` L93-122, `profile.jsp` form L132-243 | `PUT /api/players/me` `{age, gender?}` → `200` `PlayerProfileDto` | same page, "Update"/"Cancel"; success toast "Modification réussie!" | — | verified | Fix: no stale overwrite of stats (B21). **D11** gender select. **D19**: "Changer Mot de Passe" button hidden (out of scope). Username read-only. **Review**: backend ✔ (G3 fixed: `@DynamicUpdate`, `profileEditDoesNotOverwriteStatsCommittedMeanwhile`). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 7 | Online presence (lobby) | `LoginServlet.sessions` L27/L97-98, `UserStatusWebSocket#broadcast` L435-449, `#onOpen/#onClose` L42-57, `home.jsp` sidebar L17-34 + WS handler L121-153 | `GET /api/players/online` → `[{username, status: ONLINE \| IN_GAME}]`; STOMP `/topic/presence` (`PlayerOnline`, `PlayerOffline`, `PlayerStatusChanged`) | `/lobby` · `LobbyPage`, `OnlinePlayersPanel` | `PresenceParityTest` | verified | Online ⇔ ≥1 authenticated STOMP session (fixes B17). Current user excluded from the list (as legacy). **D15**: several tabs allowed, offline when the last tab disconnects. **D16**: players in a running game are shown `IN_GAME`. **Review**: backend ✔ (`PresenceParityTest` 8/8, `PresenceControllerTest`, STOMP listeners). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 8 | Invitations: create (game setup) | `home.jsp` game form L65-86 + `sendInvitation` L178-201, `UserStatusWebSocket#handleInvite` L278-304 | `POST /api/invitations` `{invitees: [username], settings: {totalRounds, roundSeconds}}` → `201` `InvitationDto {id, inviter, invitees[], accepted[], settings, status, expiresAt}`; each invitee gets `InvitationReceived` on `/user/queue/invitations`. `400` invalid settings/invitees; `409` invitee offline, in a game (D16) or already in a pending invitation | `/lobby` · `GameSetupForm` (rounds, seconds per round, multi-select of online players) | `InvitationFlowParityTest` | verified | **D7**: only the selected players are invited (fixes B11), 1+ invitees, not self, no duplicates. **D9**: no "number of combinations". **D10**: rounds 1-20 (default 3), round 5-120 s (default 10). **D2**: label "seconds per round" (fixes B2). **Review**: backend ✔ (`InvitationFlowParityTest` 20/20, `InvitationControllerTest`); G4 open (user decision), G9 fixed. **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. G4 (inviter must be online) still awaits a user decision; it cannot happen through the UI, which always connects the lobby. |
| 9 | Invitations: respond, cancel, expire | `home.jsp` `confirm()` L154-163, `UserStatusWebSocket#handleResponse` L306-321 | `POST /api/invitations/{id}/accept` / `/decline` → `200` `InvitationDto`; `InvitationUpdated` to inviter + all invitees on `/user/queue/invitations` | `InvitationDialog` (PrimeNG dialog, Accept / Decline, countdown to `expiresAt`) | same | verified | **D7**: game starts when **all** invitees accepted; **any decline cancels** the invitation (`status: DECLINED`, `declinedBy`) and notifies everyone (fixes B12); a pending invitation **expires after 60 s** (`status: EXPIRED`, notify everyone). Accept/decline on a non-pending invitation → `409`. **Review**: backend ✔ (decline/expiry/idempotent accept covered by parity). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 10 | Game start + card deal | `UserStatusWebSocket#beginGame` L379-432, `#generateGameCards` L452-466, `home.jsp` redirect handler L164-174 | When the last invitee accepts: `GameService.startGame` first inserts the `games` row with status `IN_PROGRESS` (D22) to get the id, then state = `GameEngine.start(...)`; each player gets `GameStarted {gameId}` on `/user/queue/games`; `GET /api/games/{id}` → `GameSnapshotDto` | navigate to `/games/:id` · `GamePage`, `GameTable`, `PlayerCardList`, `CenterCard` | `CardGenerationParityTest`, `RoundTimerParityTest` | verified | Player order: inviter first, then invitees in invitation order (legacy order was arbitrary HashSet order). **D1**: keep the 11 symbols without `G`. ≥ 2 distinct players (D7). **D22**: game row created at start, live state in memory; `GET /api/games/current` for rejoin (D17). Contract: "invitation and game services"; tests: `GameSessionParityTest`. **Review**: backend ✔ (`GameSessionParityTest`, `CardGenerationParityTest`; row written at start, D22). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. Follow-up V1 (low): resync if the last round's end event is missed. |
| 11 | Rounds & timer | `game.jsp` `initializeGame` L104-118, `startTimer/resetTimer` L189-212, `finTour` handler L143-150; `UserStatusWebSocket` case `finTour` L77-81, `#handleFinTour` L264-276 | Server-driven: `TaskScheduler` per game fires at `roundEndsAt` → `GameEngine.roundTimerExpired` → `/topic/game/{id}` `RoundStarted {round, totalRounds, roundEndsAt, deal, scores}` or `GameFinished` | `RoundTimer` (countdown from `roundEndsAt`), notification "Time's up! Moving to the next round." (French per D21) | `RoundTimerParityTest` | verified | One server timer per game (fixes B9). Kept: N rounds of T s, new cards each round, scores carry over. **D2** seconds per round, **D10** limits. **D17**: a disconnected player's game continues, score kept, `GET /api/games/{id}` restores the view. **Review**: backend ✔ (`RoundTimerParityTest`, server timer per game, no timer left after the end). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. Follow-up V1 (low): resync if the last round's end event is missed. |
| 12 | Claims & scoring | `game.jsp` `handleSameColorButton` L255-273, `handleSameFormatButton` L274-293, `handleboth` L295-315; `UserStatusWebSocket#handleUpdateScore` L323-327, `#broadcastUpdatedScores` L330-342 | STOMP SEND `/app/game/{id}/claim` `{claim: SAME_COLOR\|SAME_SYMBOL\|BOTH, dealVersion}`; `GameEngine.claim` → `CORRECT`: `/topic/game/{id}` `ScoresUpdated {scores, deal, scoredBy}`; `WRONG`/`STALE`: `ClaimRejected {reason}` to the sender on `/user/queue/errors`, nothing broadcast | `ClaimButtons` ("The Same Color", "The Same Format", "The Two"; French labels per D21) | `ClaimScoringParityTest` | verified | Server-authoritative (fixes B10). **D8**: rule kept (+1 for any correct claim, BOTH = 1, no penalty), and only the **first correct claim per `dealVersion`** scores; a claim naming another version is `STALE`. **Review**: backend ✔ (`ClaimScoringParityTest`, STOMP claim mapping, subscribe guard, errors queue); G10 fixed (`ClaimRateLimiter`). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. Follow-up V1 (low): resync if the last round's end event is missed. |
| 13 | Game end: result, persistence, stats | `game.jsp` L200-203 (`finPartie`); `UserStatusWebSocket#handleResult` L136-251, `#updatePlayerStats` L253-262; `dto/PartieManager.java`; `dao/PartieDao`, `ParticipationDAO`, `JoueurDao` | On `FINISHED`: one transaction (`GameRecorder.finishGame`) sets the game row created at start to `FINISHED` + `finished_at` (D22), adds one `participation` per player and updates `player` stats (`PlayerStats.afterGame`); `/topic/game/{id}` `GameFinished {draw, winner?, loser?, leaders, topScore, trailers, bottomScore, scores}`; `GET /api/games/{id}/result` → `GameResultDto` (same fields) | — | `GameResultParityTest`, `PlayerStatsParityTest`, `GameSessionParityTest` | verified | **D3**: all-zero = draw, no victory (fixes B3). **D4**: tied leaders share a draw (no winner, no victory); tied trailers → no loser; never the same player as winner and loser (fixes B4). **D5**: only the final winner gets a victory (fixes B5). **D6**: one game row per game, stats and participation every game (fixes B6, B7, B8). **D14**: only the game's players receive the event. **D25**: games cut by a restart become `ABANDONED` (no participations, no stats). `game` stores `total_rounds`, `round_seconds`, `game_mode = 'Normal'`, `status`, `started_at`, `finished_at` (D22); no combinations (D9). **Review**: backend ✔ for D3-D6 (`GameResultParityTest`, `PlayerStatsParityTest`, `JpaGameRecorderTest`); G5 and G6 (D25) fixed. **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 14 | Result page | `webApp/WinnerPage.jsp` | `GET /api/games/{id}/result` | `/games/:id/result` · `GameResultPage` ("Gagnant", "Perdant", "Rejouer" → `/lobby`) | — | verified | **D20**: keep the winner/loser layout; the API also returns all scores. **D3/D4**: a draw shows "Égalité" with the tied leaders; no "Perdant" line when there is no single loser. Scores shown as integers (fixes B29). **D25**: `410` (abandoned game) → toast + `/lobby`. Legacy 2 s delay before showing: optional animation, not required. **Review**: backend ✔ for `GET /api/games/{id}/result` (403/404/409); 410 for abandoned games (D25). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 15 | Schema + seed | `model/*.java`, `META-INF/persistence.xml`, `dto/MockData.java` | Flyway `V1__init_schema.sql` (`players`, `games`, `participations`), `V2__game_status.sql` (D22), dev-only seed migration | — | — (`@DataJpaTest` only for custom queries) | verified | **D12**: no `cartes`/`tours` tables, no click ratio/click count columns. **D9**: no combinations column. **D11**: `gender` nullable, check in (`MALE`,`FEMALE`,`OTHER`). **D13**: dev-only seed (separate Flyway location enabled only in the dev profile): `alice`, `bob`, `carol`, bcrypt hash of a dev password documented in the seed file, zero stats. **D18**: unique index on `lower(username)`. **D22**: `V2__game_status.sql` (status, `finished_at`, `played_at` → `started_at`), see the services contract. **D23**: passwords 8-72 characters (validation only, no column change). **D25**: `V3__game_abandoned.sql` (status `ABANDONED`). **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |
| 16 | Unused legacy code | `controller/GameServlet.java` (`/game` endpoint), `UserStatusWebSocket#generateCards` L346-376, `dto/CardTables`, `webSocket/MainApp`, `model/Carte`, `model/Tour`, `enums/Forme`, `enums/Couleur`, `dao/CarteDao`, `dao/TourDao`, `test/UserStatusWebSocketTest` (all commented out) | not ported | — | — | verified | **D12**: not ported. **Verified (transformer, 2026-11-16)**: parity + unit/e2e green, UI checked against the legacy JSPs. |

## Legacy inventory

### Servlets and endpoints

| Class | URL / method | Inputs | Session attributes | Output / navigation | Side effects | Errors |
|---|---|---|---|---|---|---|
| `RegisterServlet` | `POST /register` (form) | `username`, `genre`, `age` (int), `password`, `confirm_password` | none | `302 index.jsp?successMessage=Compte+successful` | INSERT `joueurs` with stats 0 / 0.0, **plain-text password** | Password mismatch → forward to `register.jsp` (**file does not exist**, 404). Non-numeric/empty `age` → `NumberFormatException` (500). Duplicate username accepted. Save failure → `ServletException`. |
| `LoginServlet` | `POST /login` (form) | `username`, `password` | Invalidates old session, then sets `currentJoueur` (entity), `pseudo`, `age`, `nbrPartieJouées`, `genre`, `nbrVictoire`, `ratioClicRapide`, `ratioClicReussi`, `scoreMoy`, `username`, `connectedUsers` (the static map) | success `302 home.jsp`; failure `302 index.jsp?error=Invalid username or password` | `LoginServlet.sessions.put(username, session)` (static); WS broadcast `{"action":"login","username","sessionId"}` to **all** sockets | Loads **all** players and compares plain text (`findAll`). |
| `LogoutServlet` | `POST /logout` | session | reads `username` | `302 index.jsp` | removes from static map; WS broadcast `{"action":"logout",...}`; `session.invalidate()` | — |
| `ProfileServlet#doGet` | `GET /profileServlet?username=` | `username` (query, **not** the session user) | none | `200` JSON = Gson of the whole `Joueur` entity (**includes `motDePasse`**); `404 {}` if unknown | — | Anyone can read any profile. |
| `ProfileServlet#doPost` | `POST /profileServlet` (form) | `genre`, `age`; `pseudo` field is posted but the servlet reads `username` (unused) | reads `currentJoueur`; sets `currentJoueur`, `pseudo`, `age`, `nbrPartieJouées`, `genre`, `updateStatus="Modification réussie!"` | `302 profile.jsp` | `merge` of the **login-time** `Joueur` copy → overwrites stats changed since login | Any exception → forward `errorPage.jsp` (**does not exist**). |
| `GameServlet` | WS `@ServerEndpoint("/game")` | — | — | — | prints "Im here" | Dead code. |
| `UserStatusWebSocket` | WS `@ServerEndpoint("/userStatus?username=")` | see message table | `session.getUserProperties().username` from the **query string** (spoofable; NPE if missing) | see message table | static state, DB writes at game end | see bugs |

### JSPs

| Page | Shows | Uses (HTTP) | Uses (WS) | Client state |
|---|---|---|---|---|
| `index.jsp` | Landing "Speedest Game", sign-in modal, sign-up modal, `error` / `successMessage` query params as alerts (unescaped, auto-hide 3 s) | `POST login`, `POST register` | — | — |
| `home.jsp` (lobby) | "Connected Users" sidebar (server-rendered from `connectedUsers`, excluding self), navbar (Home, Profile, Logout), "Game Setup" form: Number of Tours, Timer ("minutes per game"), Number of Combinations, invitees (free text, comma separated, required), Start Game / Reset; toast for `successMessage` | `POST logout`, link `profile.jsp` | opens `/userStatus?username=`; receives `login`, `logout`, `invitation` (→ `confirm()` → sends `response`), `redirect` (→ stores start state, navigates to `game.jsp`); sends `invite` | writes `localStorage.username`, `playersScore`, `playerCards`, `nbrTours`, `centreCard`, `timer` |
| `game.jsp` | "Game has started!", round number, own username, countdown, per-player rows `name : score : [card]` (card shows letter, background = color), center card button, three claim buttons, centered notification | — | opens a **new** `/userStatus?username=` socket; receives `scoreUpdate`, `finTour`, `redirect`, (`stateUpdate`, never sent); sends `updateScore`, `finTour`, `finPartie` | reads/writes `localStorage` (`playersScore`, `cartes`, `carterCard` [sic], `winner`, `loser`, `winnerScore`, `loserScore`) |
| `WinnerPage.jsp` | After 2 s: "Gagnant : name(Score: x )", "Perdant : name(Score: y )", "Rejouer" link to `home.jsp` | — | — | reads `localStorage` `winner`, `loser`, `winnerScore`, `loserScore` |
| `profile.jsp` | Avatar + pseudo, About text (static), Personal Details (Pseudo read-only, Age, genre free text, "Changer Mot de Passe" button with no handler), Game Stats (read-only: games played, victories, average score, click ratios), Cancel / Update, success alert from `updateStatus` | `GET profileServlet?username=<session username>` (jQuery), `POST profileServlet` | — | — |
| `style.css`, `cover.css`, `images/Bg.jpg` | Bootstrap cover template styles | | | |

### WebSocket messages (`/userStatus`)

All messages are JSON objects with an `action` field. "All" = every open socket on the server (lobby and game pages, all games).

| `action` | Direction | Shape | Server effect / client effect | Legacy source |
|---|---|---|---|---|
| `login` | S → all | `{"username","sessionId","action":"login"}` (hand-built string) | lobby adds `<li id="user-<name>">` | `broadcast` L435-449; `LoginServlet` L100; `home.jsp` L126-146 |
| `logout` | S → all | `{"username","sessionId","action":"logout"}` | lobby removes the `<li>` | `broadcast`; `LogoutServlet` L28; `home.jsp` L147-153 |
| `invite` | C → S | `{"action":"invite","invitees":"a,b","gameDetails":{"nbrPartie":"3","timer":"10","combinaison":"2"}}` (all strings) | Sets static `nbrTour`, `timer`, `combinaison` (`parseInt`, empty → exception, invite dropped); sends `invitation` to every **other** socket; `invitations[sender]` = their usernames; `acceptedInvitations[sender]` = {} | `handleInvite` L278-304; `home.jsp` L178-201 |
| `invitation` | S → every other socket | `{"action":"invitation","message":"Invitation from X: accept or reject?","sender":"X"}` (String.format) | lobby shows `confirm()`; game page ignores it | L297-298; `home.jsp` L154-163 |
| `response` | C → S | `{"action":"response","sender":"X","response":"accept"\|"reject"}` | accept: add responder to `acceptedInvitations[X]`; when its size == `invitations[X]` size → `beginGame`. reject: nothing | `handleResponse` L306-321 |
| `redirect` (start) | S → players | `{"action":"redirect","url":"game.jsp","nbrTours":3,"playerCards":[{"username","carte":"A","color":"red"}],"centreCard":{"carte":"L","color":"red"},"timer":10,"scores":{"alice":0,"bob":0}}` | lobby stores into `localStorage` and navigates | `beginGame` L379-432 |
| `updateScore` | C → S | `{"action":"updateScore","scoresUpdate":{"alice":1,"bob":0}[,"playerCards":[...]]}` | server broadcasts the **client's** map + a new deal | `handleUpdateScore` L323-327; `game.jsp` L262/281/303 |
| `scoreUpdate` | S → all | `{"action":"scoreUpdate","scores":{...},"cartes":[UserCard...],"centerCards":{"carte","color"}}` | game page replaces scores, cards, center card | `broadcastUpdatedScores` L330-342; `game.jsp` L131-142 |
| `finTour` | C → S | `{"action":"finTour","scoresUpdate":{...},"playerCards":[...],"currentTour":1}` | `handleUpdateScore` (broadcast + new deal) then `handleFinTour` | L77-81; `game.jsp` L198 |
| `finTour` | S → all | `{"action":"finTour","tour":2}` | game page: notification, round = `tour`, restart timer | `handleFinTour` L264-276; `game.jsp` L143-150 |
| `finPartie` | C → S | `{"action":"finPartie","resultat":{"alice":3,"bob":1},"partieDetails":{"numberTours":3,"timerDuration":10}}` | `handleResult`: winner/loser, save game, participations, stats, broadcast | `handleResult` L136-251; `game.jsp` L200-203 |
| `redirect` (end) | S → all | `{"action":"redirect","url":"WinnerPage.jsp","winner":"alice","loser":"bob","winnerScore":"3.0","loserScore":"1.0"}` (scores are strings) | every page that handles `redirect` navigates (lobby users too) | L231-250; `game.jsp` L151-160 |
| `stateUpdate` | — | — | handled by `game.jsp` L125-130 but never sent; references undefined `centerCartes` | dead |

### Static state in `UserStatusWebSocket` (all server-wide, one game at a time)

| Field | Line | Meaning | New home |
|---|---|---|---|
| `sessions` | 24 | all open sockets | Spring STOMP session registry / `PresenceService` |
| `invitations` | 25 | inviter → invited usernames | `InvitationService` (per invitation id) |
| `acceptedInvitations` | 26 | inviter → accepted usernames | `InvitationService` |
| `letters`, `colors` | 27-28 | card symbols (no `G`), colors | `CardDealer.SYMBOLS`, `CardDealer.COLORS` |
| `random` | 29 | unseeded `Random` | injected `RandomGenerator` |
| `players` | 32 | players of "the" game (aliases an `acceptedInvitations` set) | `GameState.players` per game |
| `nbrTour`, `timer`, `combinaison` | 34-36 | settings of "the" game (overwritten by any new invite) | `GameSettings` per invitation/game |
| `nbrUsers` | 38 | player count, unused | — |
| `processedUsers` | 40 | users whose result was saved, **never cleared** | not needed (one save per game) |
| `PartieManager.uniqueInstance` | `dto/PartieManager` L6 | singleton `Partie` reused for every game | new `Game` row per game |

### Entities / schema (EclipseLink, MySQL `game` DB, tables created outside the code)

| Entity → table | Fields (column) | Relations | Used? |
|---|---|---|---|
| `Joueur` → `joueurs` | `joueurId` (`JoueurID`, identity), `pseudo` (`Pseudo`), `motDePasse` (`MotDePasse`, plain text), `âge` (`Âge`), `genre` (`Genre`), `nombrePartiesJouées`, `nombreVictoires` (Integer), `scoreMoyen`, `ratioClicRéussi`, `ratioClicRapide` (Double) | — | yes |
| `Partie` → `parties` | `partieId` (`PartieID`), `dateHeure` (Timestamp, not null), `nombreTours` (int), `valeurTimer` (int), `nombreCombinaisons` (int, never set), `modeJeu` (not null, always `"Normal"`) | `tours` one-to-many (mappedBy `partie`, cascade all) | yes (singleton bug) |
| `Participation` → `participations` | `participationId`, `score` (Integer), `nombreClicRéussi`, `nombreClicRapide` (never set) | `joueur` **@OneToOne** (`JoueurID`), `partie` **@OneToOne** (`PartieID`); semantically both are many-to-one | yes |
| `Tour` → `tours` | `tourId`, `timestamp` | `partie` many-to-one (not null), `carteCentrale` one-to-one → `Carte` | **never written** |
| `Carte` → `cartes` | `carteId`, `forme` (enum `Forme`, string), `couleur` (enum `Couleur`, string) | — | **never written** |

Proposed new schema (backend agent owns the final Flyway SQL): `player(id, username unique, password_hash, age, gender, games_played, victories, average_score, created_at)`, `game(id, started_at, status IN_PROGRESS|FINISHED, finished_at, total_rounds, round_seconds, game_mode)` (D22, `V2`), `participation(id, game_id → game, player_id → player, score, unique(game_id, player_id))`. No combinations, click ratio or click count columns (D9, D12); `gender` nullable enum (D11); unique `lower(username)` (D18).

### DAOs

Every DAO (`CarteDao`, `JoueurDao`, `ParticipationDAO`, `PartieDao`, `TourDao`) implements `IDao` (`findById`, `findAll`, `save` = persist-or-merge, `update` = save, `delete`) and creates **its own `EntityManagerFactory`** in its constructor (`handleResult` builds three per game end). `JoueurDao.findByPseudo` returns `null` when absent (and would throw `NonUniqueResultException` with duplicate usernames). `TourDao` never initialises its EMF. Target: Spring Data `PlayerRepository` (`findByUsernameIgnoreCase`), `GameRepository`, `ParticipationRepository`.

### Suspected legacy bugs

Decision column: **fix** = new code behaves differently (approved), **keep** = legacy behaviour preserved.
Decisions D1-D21 are listed under "Decisions" below.

| # | Bug | Where | Decision |
|---|---|---|---|
| B1 | Symbol list skips `G` (`A-F, H-L`) | `UserStatusWebSocket` L27, L381 | **keep** (D1) |
| B2 | Timer label says "minutes per game", code uses seconds per round | `home.jsp` L72-73 vs `game.jsp` L189-207 | **fix** label, keep seconds per round (D2) |
| B3 | `maxScore = Double.MIN_VALUE` (smallest positive double): all-zero scores → `winner = null`, nobody gets a victory, page shows `null(Score: 4.9E-324 )` | L152 | **fix**: draw (D3) |
| B4 | Ties broken by map iteration order (arbitrary); when all scores are equal (> 0) the **same player is winner and loser** | L166-178 | **fix**: shared draw, never winner = loser (D4) |
| B5 | Victory credited to the **running** leader during the loop: every player who is max-so-far when visited gets a victory | L180-184 | **fix**: final winner only (D5) |
| B6 | `processedUsers` is static and never cleared: stats and participations saved only for each user's first game since server start | L40, L180-181 | **fix** (D6) |
| B7 | `PartieManager` singleton: every game after the first reuses the same `Partie` row | `PartieManager` L9-17, L155-160 | **fix** (D6) |
| B8 | Every client sends `finPartie`, so results are computed and broadcast N times | `game.jsp` L200-203 | **fix** (server-driven end) |
| B9 | Every client sends `finTour`: N round broadcasts, N new deals and N score overwrites per round | L77-81 | **fix** (server-driven timer) |
| B10 | Client-authoritative scoring: last writer wins, any client can send any scores | `game.jsp` L255-315, L323-342 | **fix** (server-authoritative, D8) |
| B11 | `invite` ignores `invitees` and invites every other open socket | L292-300 | **fix** (D7) |
| B12 | A decline is ignored; the game never starts and the inviter is never told | L306-321 | **fix**: decline cancels + notifies, 60 s expiry (D7) |
| B13 | One global game: a new invite overwrites the running game's settings; `players` overwritten by the next game | L32-38, L283-285, L385 | **fix** (per-game state) |
| B14 | `players` aliases the `acceptedInvitations` set and the inviter is added to it | L385-386 | **fix** |
| B15 | `scoreUpdate`, `finTour` and the end `redirect` go to **all** sockets | L246-250, L271-275, L337-341 | **fix** (D14) |
| B16 | Socket identity from `?username=` (impersonation); NPE if missing | L45-49 | **fix** (principal) |
| B17 | Presence = static map of HttpSessions, never cleaned; socket close not reported | `LoginServlet` L27; `home.jsp` L21-31; L54-57 | **fix** (STOMP presence, D15) |
| B18 | Plain-text passwords; login loads all players | `LoginServlet` L82-84 | **fix** (bcrypt) |
| B19 | No unique username; mismatch forwards to missing `register.jsp`; bad age → 500 | `RegisterServlet` L29-35 | **fix** (D18) |
| B20 | Profile GET exposes any player's data **and password** | `ProfileServlet` L54-70 | **fix** |
| B21 | Profile POST merges the stale login-time entity, reverting stats; error page missing | `ProfileServlet` L101-105, L119-120 | **fix** |
| B22 | XSS / JSON injection (unescaped params and usernames, hand-built JSON) | `index.jsp` L6-8/L23-25, `home.jsp` L28, L297, L437 | **fix** |
| B23 | Dead/broken client code (`cartes`, `centerCartes` undefined; `querySelector('#'+username)`) | `game.jsp` L164-174, L125-130, L258/276/297 | **fix** (D18 also restricts usernames) |
| B24 | `joueurDao.save(j)` before the null check; NPE if stats are null | L214-218, L256 | **fix** |
| B25 | `nombreCombinaisons`, click ratios and click counts never computed; `Tour`/`Carte` never written | various | **fix**: dropped (D9, D12) |
| B26 | Hard-coded `ws://localhost:8080/LearnSocket_war_exploded` and context path | `home.jsp` L118, `game.jsp` L120, `profile.jsp` L14, `WinnerPage.jsp` L70 | **fix** (same-origin) |
| B27 | DB credentials committed | `META-INF/persistence.xml` L14-16 | **fix** (env vars); old password compromised |
| B28 | New `EntityManagerFactory` per DAO instance, no transaction for a game save; `TourDao` EMF null | `dao/*` | **fix** |
| B29 | Scores sent as strings of doubles (`"3.0"`) | L237-238, `WinnerPage.jsp` L85-86 | **fix** (integers) |
| B30 | Empty timer/rounds → exception, invite dropped; `0` rounds = 1 round; `0` s ≈ 1 s | L283-285, `game.jsp` L189-207 | **fix**: validation (D10) |

### Decisions (user answered "yes to all" recommendations, 2026-11-15)

| # | Question | Decision |
|---|---|---|
| D1 | Symbols skip `G`? | Keep the 11 legacy letters `A-F, H-L`. |
| D2 | Timer unit? | Seconds per round; the label is fixed. |
| D3 | All scores 0? | Draw: no winner, no loser, no victory; result page says "Égalité". |
| D4 | Ties? | Tied leaders share a draw (no winner, no victory); tied lowest scores → no loser; never the same player as winner and loser. |
| D5 | Victories to running leaders? | Only the final winner gets a victory. |
| D6 | Stats only for first game / shared game row? | One game row and one participation per player per game; stats updated after every game. |
| D7 | Invitations? | Invite only the selected online players (1+, not self); start when all accept; any decline cancels and notifies everyone; pending invitations expire after 60 s. |
| D8 | Claim rules? | +1 for any correct claim, BOTH = 1, no penalty; only the first correct claim per `dealVersion` scores. |
| D9 | Number of combinations? | Dropped (form, API, schema). |
| D10 | Limits? | Rounds 1-20 (default 3); round 5-120 s (default 10); `400` outside. |
| D11 | Gender? | Optional enum `MALE`, `FEMALE`, `OTHER`, select in register and profile. |
| D12 | Unused data? | Don't port `cartes`/`tours`, click ratios, click counts; remove ratios from the profile page. |
| D13 | Seed? | Dev-only seed: `alice`, `bob`, `carol` with a bcrypt-hashed dev password, zero stats. |
| D14 | Event audience? | Only the game's players receive game events (`/topic/game/{id}`). |
| D15 | Multiple tabs? | Allowed; online while ≥1 tab is connected; game events reach all tabs. |
| D16 | Invite a player in a running game? | No: shown `IN_GAME` and rejected with `409`. |
| D17 | Disconnect mid-game? | Game continues, score kept, `GET /api/games/{id}` restores the view. |
| D18 | Username/password rules? | Username 3-20 chars `[A-Za-z0-9_-]`, unique ignoring case; password ≥ 8; legacy accounts not migrated. |
| D19 | "Changer Mot de Passe"? | Out of scope; hidden. |
| D20 | Result page content? | Keep winner/loser layout; API returns all scores too. |
| D21 | UI language? | French UI texts. |
| D22 | When is the game row written? | A Flyway `V2` adds `games.status` (`IN_PROGRESS` → `FINISHED`) and `finished_at`, and renames `played_at` to `started_at`. The row is created when the game **starts**, so its id exists for `/topic/game/{id}` and rejoining. Live state stays in memory. |
| D23 | Maximum password length? | 72 characters (bcrypt limit), `400` above. |
| D25 | Games left `IN_PROGRESS` by a server restart? | `V3` adds status `ABANDONED`. At startup those games become `ABANDONED` with `finished_at` set; no participations, no stats. Snapshot and result answer **410 Gone** (see "Contract note: V3 / abandoned games"). |
| D24 | `Location` header on `201`? | Not for now: `POST /api/auth/register` and `POST /api/invitations` return `201` with a body and no `Location`. |

No open questions remain (the restart point became D25).

## Contract for backend: game engine

Pure Java, package `com.cardgame.game.engine`, **no Spring, no I/O, no static mutable state**. All
types below are `public`. The parity tests in `backend/src/test/java/com/cardgame/parity/` compile
against exactly these signatures and encode decisions D1-D10 (checked against a throwaway reference
implementation of the decided behaviour: 89/89 cases pass with `@Disabled` removed).

```java
package com.cardgame.game.engine;

public enum CardColor { RED, BLUE, GREEN;
    public String wireValue();                    // "red" | "blue" | "green"
}
public record Card(char symbol, CardColor color) {}
public record PlayerCard(String player, Card card) {}
public record Deal(List<PlayerCard> playerCards, Card centerCard) {
    public Card cardOf(String player);           // IllegalArgumentException if unknown
}

public final class CardDealer {
    public static final List<Character> SYMBOLS;  // A B C D E F H I J K L  (legacy order, no G - D1)
    public static final List<CardColor> COLORS;   // RED, BLUE, GREEN       (legacy order)
    public CardDealer(java.util.random.RandomGenerator random);
    public Deal deal(List<String> players);       // IllegalArgumentException if empty
}

public enum Claim { SAME_COLOR, SAME_SYMBOL, BOTH }
public final class ClaimRules {
    public static boolean isCorrect(Claim claim, Card playerCard, Card centerCard);
    public static int pointsFor(Claim claim, Card playerCard, Card centerCard);   // 1 or 0
}

public record GameSettings(int totalRounds, java.time.Duration roundDuration) {
    // compact constructor: IllegalArgumentException unless 1 <= totalRounds <= 20
    //                      and 5 s <= roundDuration <= 120 s (D10). No "combinations" (D9).
    public static GameSettings defaults();        // 3 rounds, 10 s
}
public enum GamePhase { IN_PROGRESS, FINISHED }
public record GameState(List<String> players, GameSettings settings, GamePhase phase, int currentRound,
                        SequencedMap<String, Integer> scores, Deal deal, int dealVersion,
                        java.time.Instant roundStartedAt) {
    public java.time.Instant roundEndsAt();       // roundStartedAt + settings.roundDuration
}
public enum ClaimStatus { CORRECT, WRONG, STALE }
public record ClaimOutcome(ClaimStatus status, GameState state) {
    public boolean correct();                     // status == CORRECT
}
public record PlayerOutcome(String player, int score, boolean victory) {}
public record GameResult(String winner,           // null = no single winner (draw)
                         String loser,            // null = no single loser
                         List<String> leaders, int topScore,
                         List<String> trailers, int bottomScore,
                         List<PlayerOutcome> outcomes) {     // outcomes in player order
    public boolean isDraw();                      // winner == null
}

public final class GameEngine {
    public GameEngine(java.util.random.RandomGenerator random, java.time.Clock clock);
    public GameState start(List<String> players, GameSettings settings);
    public ClaimOutcome claim(GameState state, String player, Claim claim, int dealVersion);
    public GameState roundTimerExpired(GameState state);
    public GameResult result(GameState state);                      // IllegalStateException unless FINISHED
    public static GameResult determineResult(SequencedMap<String, Integer> scores);
}

public record PlayerStats(int gamesPlayed, int victories, double averageScore) {
    public static PlayerStats initial();                           // 0, 0, 0.0
    public PlayerStats afterGame(int score, boolean victory);
}
```

Behaviour the tests pin:

1. **Randomness**: only `RandomGenerator.nextInt(int bound)` is called (tests use a scripted generator
   that fails on anything else and checks each bound). `GameEngine` builds its own `CardDealer` from the
   injected generator.
2. **Deal draw order**: for each player in list order, `nextInt(11)` → `SYMBOLS[i]`, then `nextInt(3)` →
   `COLORS[i]`; then `nextInt(n)` picks the player whose **symbol** is the center symbol, then `nextInt(n)`
   picks the player whose **color** is the center color (n = player count). Duplicates between players are
   allowed. The center card may match nobody's card exactly.
3. **start**: `IllegalArgumentException` for fewer than 2 players or duplicate names (D7). Phase
   `IN_PROGRESS`, `currentRound = 1`, scores 0 for every player **in the given order** (insertion-ordered
   map; tests compare order), one deal, `dealVersion = 1`, `roundStartedAt = clock.instant()`.
   `GameState` holds immutable copies so `equals` works and states are safe to share across threads.
4. **claim** (D8): `IllegalStateException` if not `IN_PROGRESS`; `IllegalArgumentException` if the player
   is not in the game. Then, in this order: `dealVersion != state.dealVersion()` → `STALE`, same state;
   wrong claim → `WRONG`, same state (no random draw); correct → `CORRECT`, claimer +1 (also for `BOTH`),
   new deal, `dealVersion + 1`, round and `roundStartedAt` unchanged.
5. **roundTimerExpired**: `IllegalStateException` if not `IN_PROGRESS`. If `currentRound < totalRounds` →
   `currentRound + 1`, new deal, `dealVersion + 1`, `roundStartedAt = clock.instant()`, scores unchanged.
   Otherwise phase `FINISHED`, same scores, deal and `dealVersion`, **no** random draw.
6. **determineResult** (D3, D4, D5): `topScore` = max, `leaders` = players with max (player order);
   `bottomScore` = min, `trailers` = players with min, **empty when max == min**. `winner` = the single
   leader or `null`; `loser` = the single trailer or `null`. `victory` is true only for `winner`.
   All-zero (or all-equal) scores → draw with no loser and no victory.
7. **PlayerStats.afterGame**: `gamesPlayed + 1`, `victories + (victory ? 1 : 0)`,
   `averageScore = (averageScore * gamesPlayed + score) / (gamesPlayed + 1)`; applied after **every**
   game (D6).

Not in the engine (service level, see rows 8-13): invitations (D7: 60 s expiry, decline cancels),
scheduling (`TaskScheduler` calls `roundTimerExpired` at `roundEndsAt()`), serialising access per game
(claims for one game must be applied one at a time so "first correct claim per `dealVersion`" holds),
persistence (D6), messaging (D14), presence (D15, D16).

## Contract for backend: invitation and game services

Covers plan rows 5-14. The parity tests `PresenceParityTest`, `InvitationFlowParityTest` and `GameSessionParityTest` in
`backend/src/test/java/com/cardgame/parity/` compile against the signatures below and run **without a
Spring context**. They use fakes from the same package (`RecordingPublisher`, `FakeTaskScheduler`,
`FakeGameRecorder`, `FakeGameLauncher`, `MutableClock`). Both classes are `@Disabled("pending port: ...")`;
remove the annotation when the service is implemented.

### Design rules

- The services are plain classes with constructor injection (annotate them `@Service` / `@Component`).
  No static mutable state. Every timestamp comes from the injected `Clock` and every delay from the injected
  Spring `TaskScheduler` (only `schedule(Runnable, Instant)` and `ScheduledFuture#cancel` are used).
- Messaging goes through the **port** `RealtimePublisher`. Its implementation (`StompRealtimePublisher`,
  backed by `SimpMessagingTemplate`) is the only class that knows about STOMP.
- Persistence for games goes through the **port** `GameRecorder`. Its implementation (`JpaGameRecorder`)
  owns the transactions. Presence and invitations live in memory only. Live game state lives in memory too
  (D22), and only the `games` row (with its status) and, at the end, participations and stats are stored.
- Usernames are matched **ignoring case** (D18), and events and DTOs always carry the canonical spelling
  (the spelling the player registered with, as known by `PresenceService`).
- All exceptions extend the existing `com.cardgame.common.DomainException` (French `title`/`detail`, D21),
  so the existing `GlobalExceptionHandler` turns them into ProblemDetail responses.
- Every operation on one game (claims, timer ticks, snapshot reads) is serialised per game (for example
  `synchronized` on the session object), so that "first correct claim per `dealVersion`" (D8) holds.

### Ports and shared types

```java
package com.cardgame.realtime;

/** Every server → client STOMP payload. The JSON must contain "type" = simple class name, and "sentAt". */
public interface RealtimeEvent {
    java.time.Instant sentAt();
}

public interface RealtimePublisher {
    /** convertAndSendToUser(username, destination, event); destination like "/queue/invitations". */
    void toUser(String username, String destination, RealtimeEvent event);
    /** convertAndSend(destination, event); destination like "/topic/presence" or "/topic/game/7". */
    void toTopic(String destination, RealtimeEvent event);
}

public final class Destinations {
    public static final String PRESENCE_TOPIC    = "/topic/presence";
    public static final String INVITATIONS_QUEUE = "/queue/invitations";
    public static final String GAMES_QUEUE       = "/queue/games";
    public static final String ERRORS_QUEUE      = "/queue/errors";
    public static String gameTopic(long gameId) { return "/topic/game/" + gameId; }
}
```

### Presence (row 7, D15, D16)

```java
package com.cardgame.presence;

public enum PlayerStatus { ONLINE, IN_GAME }

public class PresenceService {
    public PresenceService(java.time.Clock clock, RealtimePublisher publisher);

    /** STOMP session connected. First session of this user → PlayerOnline on PRESENCE_TOPIC. */
    public void connected(String username, String sessionId);
    /** STOMP session closed. Last session of this user → PlayerOffline on PRESENCE_TOPIC. Unknown ids are ignored. */
    public void disconnected(String username, String sessionId);

    /** Case-insensitive lookup of an ONLINE player (empty when offline). */
    public Optional<OnlinePlayerDto> find(String username);
    /** Online players except `excludedUsername` (ignoring case), sorted by username ignoring case. */
    public List<OnlinePlayerDto> onlinePlayers(String excludedUsername);

    /**
     * Marks players as in a game (or not). The flag is kept while a player is offline (D17), so a player
     * who reconnects during a game comes back as IN_GAME. For each player who is online and whose status
     * changes → PlayerStatusChanged on PRESENCE_TOPIC.
     */
    public void setInGame(Collection<String> usernames, boolean inGame);
}
```

```java
package com.cardgame.presence.dto;
public record OnlinePlayerDto(String username, PlayerStatus status) {}
public record PlayerOnline(String username, PlayerStatus status, Instant sentAt) implements RealtimeEvent {}
public record PlayerOffline(String username, Instant sentAt) implements RealtimeEvent {}
public record PlayerStatusChanged(String username, PlayerStatus status, Instant sentAt) implements RealtimeEvent {}
```

The wiring is backend-only and is not covered by these tests: `@EventListener` for `SessionConnectedEvent` /
`SessionDisconnectEvent` calls `connected` / `disconnected` with `principal.getName()` and the STOMP session id.

### Invitations (rows 8-9, D7, D10, D16)

```java
package com.cardgame.invitation;

public enum InvitationStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }

public class InvitationService {
    public static final java.time.Duration EXPIRY = java.time.Duration.ofSeconds(60);

    public InvitationService(java.time.Clock clock, org.springframework.scheduling.TaskScheduler scheduler,
                             RealtimePublisher publisher, PresenceService presence, GameLauncher gameLauncher);

    public InvitationDto create(String inviter, CreateInvitationRequest request);
    public InvitationDto accept(long invitationId, String username);
    public InvitationDto decline(long invitationId, String username);
    /** PENDING invitations where `username` is the inviter or an invitee (lobby reload). */
    public List<InvitationDto> pendingFor(String username);
}

public class InvalidInvitationException extends DomainException {}        // 400
public class PlayerUnavailableException extends ConflictException {}      // 409
public class InvitationNotFoundException extends NotFoundException {}     // 404
public class InvitationClosedException extends ConflictException {}       // 409
```

```java
package com.cardgame.invitation.dto;
public record CreateInvitationRequest(
        @NotEmpty List<@NotBlank String> invitees,
        @Valid GameSettingsDto settings) {}            // null → GameSettings.defaults() (D10)
public record InvitationDto(long id, String inviter, List<String> invitees, List<String> accepted,
                            GameSettingsDto settings, InvitationStatus status, String declinedBy,
                            Instant expiresAt, Long gameId) {}   // gameId set once ACCEPTED
public record InvitationReceived(long invitationId, String inviter, List<String> invitees,
                                 GameSettingsDto settings, Instant expiresAt, Instant sentAt) implements RealtimeEvent {}
public record InvitationUpdated(long invitationId, InvitationStatus status, List<String> accepted,
                                String declinedBy, Instant sentAt) implements RealtimeEvent {}
```

Rules (pinned by `InvitationFlowParityTest`; presence rules above by `PresenceParityTest`):

1. **create**:
   - Invitee names are resolved through `PresenceService.find` (case-insensitive) to canonical names, in request order.
   - `InvalidInvitationException` (400) when the list is empty, names repeat (ignoring case), or it contains the inviter (ignoring case).
   - `PlayerUnavailableException` (409) when an invitee is offline or `IN_GAME`, an invitee is in another PENDING invitation (as inviter or invitee), or the inviter is `IN_GAME` or already in a PENDING invitation.
   - Settings are validated by `GameSettings` (D10 → 400 through bean validation on `GameSettingsDto`).
   - On success: status PENDING, `expiresAt = now + 60 s`, ids start at 1 and increase. `InvitationReceived` goes to **each invitee only** on `INVITATIONS_QUEUE`. The inviter gets the 201 response and no event. An expiry task is scheduled at `expiresAt`.
   - A rejected request publishes nothing and schedules nothing.
2. **accept**:
   - `InvitationNotFoundException` (404) when the id is unknown or the user is not an invitee (the inviter included).
   - `InvitationClosedException` (409) when the status is not PENDING.
   - Accepting twice is idempotent: same DTO, no event.
   - Otherwise the user is added to `accepted` (invitation order), and `InvitationUpdated` goes to the inviter and **every** invitee:
     - still missing answers → status PENDING;
     - all accepted → status ACCEPTED, the expiry task is cancelled, then `gameLauncher.startGame(players, settings)` is called with players = **inviter first, then invitees in invitation order**, and its id is stored in `gameId`.
3. **decline**: same 404/409 checks. Status DECLINED, `declinedBy` = the user, `InvitationUpdated` goes to everyone, the expiry task is cancelled, and the game never starts. The participants are free for new invitations at once.
4. **expiry**: when the task runs and the invitation is still PENDING → status EXPIRED and `InvitationUpdated` goes to everyone. In any other status it does nothing. After that, accept/decline → 409 and the participants are free again.
5. Closed invitations (ACCEPTED/DECLINED/EXPIRED) may be dropped from memory. `pendingFor` lists only PENDING ones.

### Games (rows 10-14, D3-D6, D8, D14, D17, D22)

```java
package com.cardgame.game;

/** Implemented by GameService; InvitationService depends only on this. */
public interface GameLauncher {
    long startGame(List<String> players, GameSettings settings);
}

/** Persistence port. JpaGameRecorder implements it with Spring Data. */
public interface GameRecorder {
    /** INSERT games row with status IN_PROGRESS, started_at, settings; returns its id (D22). */
    long createGame(GameSettings settings, java.time.Instant startedAt);
    /**
     * One transaction: status FINISHED + finished_at, one participation per record, and for each player
     * stats = PlayerStats.afterGame(score, victory) (D6). Records are in player order.
     */
    void finishGame(long gameId, java.time.Instant finishedAt, List<PlayerGameRecord> records);
    /** Only FINISHED games; records in player order (order by participation id). */
    Optional<FinishedGame> findFinished(long gameId);
}
public record PlayerGameRecord(String username, int score, boolean victory) {}
public record FinishedGame(long gameId, java.time.Instant finishedAt, List<PlayerGameRecord> records) {}

/** In-memory live games (D22). Thread-safe. */
public class GameSessionRegistry {
    public GameSessionRegistry();
    public boolean isLive(long gameId);
    public Optional<Long> activeGameOf(String username);   // ignoring case
    // registering/removing sessions: internal API, backend's choice
}

public class GameService implements GameLauncher {
    public GameService(GameEngine engine, GameSessionRegistry sessions, GameRecorder recorder,
                       PresenceService presence, RealtimePublisher publisher,
                       org.springframework.scheduling.TaskScheduler scheduler, java.time.Clock clock);

    @Override public long startGame(List<String> players, GameSettings settings);
    public void claim(long gameId, String username, Claim claim, int dealVersion);
    public GameSnapshotDto snapshot(long gameId, String username);
    public Optional<GameSnapshotDto> currentGameOf(String username);
    public GameResultDto result(long gameId, String username);
    /** Used by the STOMP SUBSCRIBE guard for /topic/game/{id}: live game and username is a player. */
    public boolean isPlayer(long gameId, String username);
}

public class GameNotFoundException extends NotFoundException {}      // 404
public class NotAGamePlayerException extends DomainException {}       // 403
public class GameFinishedException extends ConflictException {}       // 409
public class GameInProgressException extends ConflictException {}     // 409
```

The engine is the `@Bean GameEngine(RandomGenerator, Clock)`, with the same `Clock` bean the service gets.

```java
package com.cardgame.game.dto;
public record GameSettingsDto(@Min(1) @Max(20) int totalRounds, @Min(5) @Max(120) int roundSeconds) {
    public GameSettings toSettings();
    public static GameSettingsDto from(GameSettings settings);
}
public record PlayerScoreDto(String player, int score) {}
public record CardDto(String symbol, String color) {}                       // "A", "red"
public record PlayerCardDto(String player, String symbol, String color) {}
public record DealDto(int dealVersion, List<PlayerCardDto> playerCards, CardDto centerCard) {}
public record GameSnapshotDto(long gameId, List<String> players, GameSettingsDto settings, GamePhase phase,
                              int round, Instant roundEndsAt, List<PlayerScoreDto> scores, DealDto deal) {}
public record GameResultDto(long gameId, Instant finishedAt, boolean draw, String winner, String loser,
                            List<String> leaders, int topScore, List<String> trailers, int bottomScore,
                            List<PlayerScoreDto> scores) {}
// events
public record GameStarted(long gameId, Instant sentAt) implements RealtimeEvent {}
public record ScoresUpdated(long gameId, String scoredBy, List<PlayerScoreDto> scores, DealDto deal,
                            Instant sentAt) implements RealtimeEvent {}
public record RoundStarted(long gameId, int round, int totalRounds, Instant roundEndsAt,
                           List<PlayerScoreDto> scores, DealDto deal, Instant sentAt) implements RealtimeEvent {}
public record GameFinished(long gameId, boolean draw, String winner, String loser, List<String> leaders,
                           int topScore, List<String> trailers, int bottomScore, List<PlayerScoreDto> scores,
                           Instant sentAt) implements RealtimeEvent {}
public record ClaimRejected(long gameId, ClaimStatus reason, int currentDealVersion,
                            Instant sentAt) implements RealtimeEvent {}
```

Rules (pinned by `GameSessionParityTest`):

1. **startGame**:
   - `recorder.createGame(settings, now)` comes **first**, and its id is the game id (D22).
   - Then `engine.start(players, settings)`, the session is registered, `presence.setInGame(players, true)` is called, and the round timer is scheduled at `roundEndsAt()`.
   - `GameStarted` goes to **each player** on `GAMES_QUEUE`, and to nobody else.
2. **claim**:
   - Errors: `GameNotFoundException` when the game is not live (unknown or finished), `NotAGamePlayerException` when the user is not a player.
   - `CORRECT` → `ScoresUpdated(scoredBy = canonical name)` on `gameTopic(id)` only.
   - `WRONG` / `STALE` → `ClaimRejected(reason, currentDealVersion)` to the claimer on `ERRORS_QUEUE` only. Nothing on the topic, and the score is unchanged.
   - Claims do not move the timer.
3. **round timer**: when it fires, `engine.roundTimerExpired`.
   - `IN_PROGRESS` → `RoundStarted` on the game topic and the next tick is scheduled at the new `roundEndsAt()`.
   - `FINISHED`:
     - `engine.result` → records, with `victory` only for the single winner (D3-D5);
     - `recorder.finishGame(id, now, records)`, exactly once per game (D6);
     - `GameFinished` on the game topic;
     - `presence.setInGame(players, false)`;
     - the session is removed;
     - no task stays scheduled.
4. **snapshot**: the live view for a player: phase, round, `roundEndsAt`, scores in player order, the full deal with every player's card (as legacy).
   - Errors: `NotAGamePlayerException` for others. For a game that is no longer live, `GameFinishedException` when `findFinished` has it, otherwise `GameNotFoundException`.
   - `currentGameOf(username)` returns the snapshot of the user's live game, or empty. A player who disconnects and reconnects keeps their score and gets the snapshot (D17).
5. **result**:
   - From `recorder.findFinished`, recomputed with `GameEngine.determineResult` over the records in order (so `draw`/`winner`/`loser`/leaders/trailers match the `GameFinished` event).
   - Errors: `GameInProgressException` when the game is still live, `NotAGamePlayerException` when the user is not in the records, `GameNotFoundException` otherwise.
6. A player who disconnects mid-game is still a player. The timer keeps running and their score is recorded at the end (D17).

### REST endpoints (all require a session, CSRF on unsafe methods; errors are ProblemDetail)

| Method + path | Body → response | Errors |
|---|---|---|
| `GET /api/players/me` | → `200 PlayerProfileDto {username, age, gender, gamesPlayed, victories, averageScore}` | 401 |
| `PUT /api/players/me` | `UpdateProfileRequest {age (@NotNull @Min(1)), gender?}` → `200 PlayerProfileDto` | 400, 401 |
| `GET /api/players/online` | → `200 [OnlinePlayerDto]` (without the caller) | 401 |
| `POST /api/invitations` | `CreateInvitationRequest` → `201 InvitationDto` (no `Location`, like register, D24) | 400 invalid, 409 unavailable |
| `POST /api/invitations/{id}/accept` | → `200 InvitationDto` | 404, 409 closed |
| `POST /api/invitations/{id}/decline` | → `200 InvitationDto` | 404, 409 closed |
| `GET /api/invitations/pending` | → `200 [InvitationDto]` | 401 |
| `GET /api/games/current` | → `200 GameSnapshotDto` or `204` | 401 |
| `GET /api/games/{id}` | → `200 GameSnapshotDto` | 403 not a player, 404, 409 finished |
| `GET /api/games/{id}/result` | → `200 GameResultDto` | 403 not a player, 404, 409 in progress |

`PlayerService` gains `PlayerProfileDto getProfile(String username)` and
`PlayerProfileDto updateProfile(String username, UpdateProfileRequest request)` (plus the stats update used by
`JpaGameRecorder`). Controller tests are the backend's (`@WebMvcTest`).

### STOMP

| Kind | Destination | Handler |
|---|---|---|
| `@MessageMapping("/game/{gameId}/claim")` | client SEND `/app/game/{gameId}/claim`, payload `ClaimMessage(Claim claim, int dealVersion)` | `GameService.claim(gameId, principal.getName(), claim, dealVersion)` |
| `@MessageExceptionHandler` + `@SendToUser("/queue/errors")` | — | a `DomainException` thrown by a handler → `Error {status, title, detail, sentAt}` |
| SUBSCRIBE guard (`ChannelInterceptor`) | `/topic/game/{id}` | allowed only if `GameService.isPlayer(id, user)`; `/user/queue/*` and `/topic/presence` open to any authenticated user |
| connect / disconnect listeners | — | `PresenceService.connected` / `disconnected` |

### Flyway `V2` (D22)

Do not edit `V1__init_schema.sql`; add `V2__game_status.sql`:

```sql
alter table games rename column played_at to started_at;
alter index ix_games_played_at rename to ix_games_started_at;
alter table games add column status varchar(12) not null default 'IN_PROGRESS';
alter table games add column finished_at timestamptz;
update games set status = 'FINISHED', finished_at = started_at;   -- rows written before V2 were finished games
alter table games add constraint ck_games_status check (status in ('IN_PROGRESS', 'FINISHED'));
alter table games add constraint ck_games_finished_at check ((status = 'FINISHED') = (finished_at is not null));
```

`Game` entity: `startedAt`, `status` (enum `GameStatus { IN_PROGRESS, FINISHED }`), `finishedAt`, and `finish(Instant)`.
Backend integration test to add: `JpaGameRecorderTest` (`@DataJpaTest` + Testcontainers): create → IN_PROGRESS,
finish twice for the same players over two games → `games_played` 2, victories only for winners,
participations per game, `findFinished` returns records in player order (D6).

### Contract note: V3 / abandoned games (D25)

Live state is lost on restart, so games that are still `IN_PROGRESS` in the database at startup can never finish.

1. **`V3__game_abandoned.sql`** (do not edit V1/V2):
   ```sql
   alter table games drop constraint ck_games_status;
   alter table games add constraint ck_games_status check (status in ('IN_PROGRESS', 'FINISHED', 'ABANDONED'));
   alter table games drop constraint ck_games_finished_at;
   alter table games add constraint ck_games_finished_at
       check ((status in ('FINISHED', 'ABANDONED')) = (finished_at is not null));
   ```
   `GameStatus` gains `ABANDONED`, and `Game` gains `abandon(Instant)`.
2. **`GameRecorder`** gains:
   - `int abandonInProgressStartedBefore(Instant cutoff)`: a single bulk update `IN_PROGRESS` → `ABANDONED` with `finished_at = cutoff`, only for rows with `started_at < cutoff`. It returns the count and writes **no participations and no stats**;
   - `Optional<GameStatus> findStatus(long gameId)`.

   Add both methods to the test fake `parity/FakeGameRecorder` as plain storage (support code; the parity test classes themselves stay unchanged).
3. **`com.cardgame.game.AbandonedGameCleanup`** (`@Component`):
   - It records `cutoff = clock.instant()` in its constructor, i.e. before the web server accepts traffic, so a game started after boot is never touched.
   - On `@EventListener(ApplicationReadyEvent.class)` it calls `recorder.abandonInProgressStartedBefore(cutoff)` and logs the count at INFO.
4. **New exception `GameAbandonedException`** (extends `DomainException`), status **410 Gone**, title "Partie interrompue", detail "Cette partie a été interrompue par un redémarrage du serveur." For a game id that is not live, `GameService` checks `recorder.findStatus(id)`:
   - `snapshot`: FINISHED → 409 `GameFinishedException` (unchanged); ABANDONED → 410; IN_PROGRESS or absent → 404.
   - `result`: FINISHED → as today; ABANDONED → 410; otherwise 404.
   - The 410 is returned to any logged-in user, because abandoned games have no participations to check against; it exposes no data. `isPlayer` stays false, so subscriptions are refused.
5. **Tests (backend):**
   - `AbandonedGameCleanupTest` (`@DataJpaTest` + Testcontainers): an IN_PROGRESS row started before the cutoff becomes ABANDONED with `finished_at` set; a row started after it and FINISHED rows are untouched; player stats and participations are unchanged.
   - `GameService` unit tests with the fake recorder: snapshot and result → 410 for ABANDONED.
   - `@WebMvcTest`: the ProblemDetail has status 410.
6. **Frontend:** a 410 on `GET /api/games/{id}` or `/result` shows the detail as a toast and navigates to `/lobby`. `GET /api/games/current` never returns an abandoned game, because those are not live.

## Backend review (rows 1-14, 2026-11-15)

Method: read `backend/src/main` against the legacy inventory and D1-D25; ran the parity suite on the real code
(`./mvnw -o test -Dtest='com.cardgame.parity.*Test'`: 129 tests, 0 failures, 0 skipped). The parity classes are
unchanged except for the removed `@Disabled`. Rows stay at `backend` until the frontend is done and the flows are checked
end to end.

| # | Gap | Suggested fix | Owner |
|---|---|---|---|
| G1 (fixed) | Register validation is not tested at HTTP level (bad username pattern/length, password 7 and 73 chars (D23), age 0, duplicate → 409). | `AuthControllerTest` cases asserting status + `errors[].field`. | backend |
| G2 (fixed) | No test for a wrong password (401 French ProblemDetail) or for logging in as `ALICE` and getting `alice` from `/api/auth/me` (D18). | Add to `AuthControllerTest` / `AuthFlowIntegrationTest`. | backend |
| G3 (fixed) | `Player` is updated with all columns (no `@DynamicUpdate`/`@Version`): a `PUT /api/players/me` that overlaps with `finishGame` can write back the old stats (B21 again, as a race). | `@DynamicUpdate` on `Player` (no schema change) + a test that interleaves both updates. | backend |
| G4 (open, R1) | The inviter does not need a STOMP connection: a REST-only client can invite, and the game can start with the inviter offline. | Recommend `409` "Vous devez être connecté au lobby" when `presence.find(inviter)` is empty. Small rule change: needs a user OK. | user → backend |
| G5 (fixed) | If `finishGame` throws, the error is logged, `GameFinished` is still sent, but the result answers 404 and stats are lost; the row stays `IN_PROGRESS` until the next restart. | After D25: retry once, then mark the game `ABANDONED` right away so the result answers 410 consistently. | backend |
| G6 (fixed) | D25 not implemented yet. | Follow "Contract note: V3 / abandoned games". | backend |
| G7 (fixed) | Client ordering: events published between `GET /api/games/{id}` and the `SUBSCRIBE` are lost. | Subscribe to `/topic/game/{id}` first, then load the snapshot, and ignore `ScoresUpdated`/`RoundStarted` with `deal.dealVersion` ≤ the snapshot's. (Written in `realtime-protocol.md`.) | frontend |
| G8 (fixed) | Player-visible items to check on the frontend: D2 label "secondes par manche", D3/D4 draw display ("Égalité", no "Perdant" line), D25 410 handling, French texts (D21), "Time's up" notification at round change. | Frontend checklist; the transformer checks them at verification. | frontend |
| G9 (fixed) | `InvitationService.accept` calls `startGame` (a DB insert) while holding the service-wide lock: every invitation operation waits for it. | Low priority: release the lock before `startGame` (state is already ACCEPTED). | backend |
| G10 (fixed) | Claims are not throttled: a client can flood WRONG/STALE claims (each one answers a `ClaimRejected`). | Low priority: per-player limit (e.g. 10 claims/s, extra ones dropped silently). | backend |
| G11 (fixed) | When the HTTP session expires (2 h), open STOMP connections stay authenticated. | Low priority: on `SessionDestroyedEvent`, `RealtimeSessionRegistry.closeAll(sessionId)`. | backend |

## Final verification (2026-11-16)

Checks run by the transformer:

- Backend `./mvnw verify` (JDK 25): **222 tests, 0 failures, 0 skipped**, of which the parity suite has 129 tests in 8 classes. The parity classes are unchanged since they were written, apart from the removed `@Disabled`; `FakeGameRecorder` only gained plain storage for D25/G5.
- Frontend `npm test`: **48 tests in 11 files, all green**.
- Playwright against a fresh backend (`SERVER_PORT=18080 ./mvnw spring-boot:test-run`, `BACKEND_URL=http://localhost:18080 npx playwright test`): **2/2** (register → login → lobby → logout; alice invites bob → 1 × 5 s game → result). The test backend was stopped afterwards.
- Code review: backend gap fixes G1-G3, G5, G6 (D25: `V3__game_abandoned.sql`, `AbandonedGameCleanup` with the constructor cutoff, 410 `GameAbandonedException`) and G9-G11 are present and tested. The frontend matches the G8 checklist:
  - French labels;
  - "Secondes par manche";
  - claim buttons "Même couleur" / "Même forme" / "Les deux";
  - "Temps écoulé !" at each round change;
  - result "Gagnant" / "Perdant" / "Égalité" (no "Perdant" line without a single loser) / "Rejouer";
  - 410 handled on the snapshot and the result (toast + `/lobby`), 409 on the snapshot → result;
  - G7: topic subscribed first, events buffered until the snapshot and filtered by `dealVersion`, resync after a reconnect;
  - profile without click ratios or a password button;
  - lobby "Joueurs connectés" with the "En partie" status and "Vous avez une partie en cours" (D17).

Result: **16/16 rows `verified`**.

### Remaining issues (non-blocking)

| # | Issue | Owner |
|---|---|---|
| R1 | G4: refusing invitations from an inviter without a lobby connection (`409`). Only reachable by a client that bypasses the Angular lobby. | user decision → backend |
| R2 (V1) | The STOMP `SUBSCRIBE` is fire-and-forget, so an event published in the few ms between the subscription and the snapshot `GET` can be missed. Score and round events heal at the next event, but a missed `GameFinished` on the last round would leave the page on a timer at 0. Fix: when the last round's `roundEndsAt` + 2 s passes without an event, reload the snapshot (409 → result). | frontend (low) |

## Decommission readiness

`legacy/` is no longer needed as a behaviour reference: every row is verified and the legacy rules are pinned by the
parity tests. Nothing in the build reads `legacy/` (only docs, agent files and test comments mention it).
Before deleting it (not done; needs the user's agreement):

1. **User agreement** to delete `legacy/` (responsibility 7). Required.
2. Record the last commit that contains it, so the line references in this plan and in the parity test comments stay
   resolvable: tree `legacy/` = `79c57de` at commit `1a8d27b` (`git show 1a8d27b:legacy/src/main/java/webSocket/UserStatusWebSocket.java`).
3. In the same change, update the files that describe `legacy/` as present: `CLAUDE.md` ("Current state (legacy)", layout,
   "Never edit anything under `legacy/`"), `.claude/agents/*.md` and `.claude/skills/servlet-to-spring-migration/SKILL.md`.
   The parity test comments can keep their `legacy/...` citations (they refer to the commit above).
4. Delete the untracked root `target/` (legacy build output) at the same time.
5. Deleting the folder does not remove the committed MySQL password from git history (B27). It is already treated as
   compromised (ADR 0001); rewriting history is a separate user decision.
6. R1 and R2 do not block decommissioning.

## Hand-off log

| Date | From → To | What |
|---|---|---|
| 2026-11-15 | transformer → backend | Legacy moved to `legacy/`. Inventory + plan written. Parity tests for rows 10-13 in `backend/src/test/java/com/cardgame/parity/` (all `@Disabled`). Waiting on user decisions Q1-Q21 for behaviour changes; the backend can scaffold and implement rows 1-3, 15 and the game engine contract now. |
| 2026-11-15 | user → transformer → backend | All recommendations accepted (D1-D21). Plan rows, bug decisions, engine contract and parity tests updated: draw-aware `GameResult`, `dealVersion` + `ClaimStatus`, `GameSettings` validation/defaults without combinations, ≥ 2 players. Only class-level `pending port` disables remain. Realtime proposal updated (invitation decline/expiry, `ClaimRejected`, draw shape). |
| 2026-11-15 | backend → frontend, transformer | Rows 1-3 and 15 at `backend`: Spring Boot 4.1.2 scaffold, Flyway `V1__init_schema.sql` (tables `players`, `games`, `participations`), dev seed `db/seed/R__dev_seed_players.sql` (only with the `local` profile; alice/bob/carol, password `password123`), `/api/auth/register\|login\|logout\|me` (operationIds `register`, `login`, `logout`, `me`; response `PlayerDto {id, username, age, gender}`). Session cookie `JSESSIONID` plus CSRF cookie `XSRF-TOKEN`, sent back in the `X-XSRF-TOKEN` header. ProblemDetail `title`/`detail` in French, field errors in `errors[] {field, message}`. Game engine `com.cardgame.game.engine` implemented: all 5 parity classes enabled and passing (89 cases). Open point for rows 10/13: `games` holds only finished games (row 13), but row 10 needs a game id at start. Either keep ids in memory until the end or add a `status` column in V2. |
| 2026-11-15 | user → transformer → backend | D22-D24 recorded. New section "Contract for backend: invitation and game services" (rows 5-14: ports `RealtimePublisher` / `GameRecorder` / `GameLauncher`, `PresenceService`, `InvitationService`, `GameService`, `GameSessionRegistry`, REST, STOMP, `V2__game_status.sql`). New `@Disabled` tests `PresenceParityTest`, `InvitationFlowParityTest`, `GameSessionParityTest` + fakes (checked: 129/129 parity cases pass against a throwaway reference of the contract on top of the real engine). `realtime-protocol.md` aligned. |
| 2026-11-15 | backend → frontend, transformer | Rows 5-14 at `backend` (services contract implemented as specified; all 8 parity classes enabled, 129 cases green). `V2__game_status.sql` (D22). REST operationIds: `getMyProfile`, `updateMyProfile`, `getOnlinePlayers`, `createInvitation`, `acceptInvitation`, `declineInvitation`, `getPendingInvitations`, `getCurrentGame`, `getGame`, `getGameResult` (plus `register`, `login`, `logout`, `me`). Every JSON endpoint declares `application/json`; errors use the `Problem` schema (`errors: FieldViolation[]`). STOMP `/ws` implemented per `docs/realtime-protocol.md` (now IMPLEMENTED): raw `X-XSRF-TOKEN` on CONNECT, SUBSCRIBE guard on `/topic/game/{id}`, logout closes sockets. Deviations: the `/topic/game/{id}` guard is an `AuthorizationManager` rule in the WebSocket security config (it runs as a channel interceptor) rather than a hand-written `ChannelInterceptor`. A refused frame gives a STOMP `ERROR` frame and the socket stays open; the client should reconnect. `PresenceService.isInGame` was added (the inviter check needs it while the inviter is offline). Local run: `.env` is imported by Spring, and `spring-boot:run` uses the `local` profile. |
| 2026-11-15 | transformer → backend, frontend | D25 recorded with a contract note (V3, `AbandonedGameCleanup`, 410). Backend review of rows 1-14: parity 129/129 on real code; gaps G1-G11 listed under "Backend review". |
| 2026-11-16 | backend → transformer, frontend | Review gaps fixed: G6/D25 (`V3__game_abandoned.sql`, `GameStatus.ABANDONED`, `Game.abandon`, `AbandonedGameCleanup` at `ApplicationReadyEvent`, `GameAbandonedException` → 410 "Partie interrompue" on `getGame`/`getGameResult`), G3 (`@DynamicUpdate` on `Player` + interleaved-transaction test), G1/G2 (auth validation, D23 lengths, French 401, upper-case login → stored spelling), G5 (finishGame retried once, then the game is ABANDONED → 410), G9 (game start outside the invitation lock; participants stay busy while it starts), G10 (`cardgame.claims.max-per-second`, default 10, extra claims dropped silently), G11 (`HttpSessionDestroyedEvent` closes that session's STOMP connections). G4 not done (waiting for the user). Deviation: `GameRecorder` also gained `abandonGame(gameId, at)` (needed by G5), added to `FakeGameRecorder` as plain storage like the two D25 methods. Parity test classes unchanged. 219 tests green. |
| 2026-11-16 | backend → frontend | Fixes requested by the frontend: (1) no `produces` condition on the controllers any more (springdoc still documents `application/json` via `default-produces-media-type`), so 204s such as `logout` and `getCurrentGame` answer 204 to `Accept: application/problem+json` instead of 406; (2) every property of the `*Dto` schemas is `required` (nullable ones are `type: [T, "null"]`), and `CardDto`/`PlayerCardDto` `symbol` and `color` are enums; (3) `GameSnapshotDto` gains `roundStartedAt` and `serverTime`. The contract's 8-argument constructor is kept, and `equals` ignores `serverTime`, so `GameSessionParityTest` is unchanged. 222 tests green; `backend/target/openapi.json` regenerated. |
| 2026-11-16 | transformer → main session | Final verification: backend 222 tests (parity 129), frontend 48, Playwright 2/2; 16/16 rows `verified`. Remaining: R1 (G4, user decision), R2 (frontend, low). Decommission readiness written; `legacy/` not deleted. |
