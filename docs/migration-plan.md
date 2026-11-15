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
| 1 | Register | `controller/RegisterServlet.java`, `webApp/index.jsp` (signup modal L49-116, `validateForm` L157-171) | `POST /api/auth/register` `{username, password, age, gender?}` → `201` `PlayerDto`; `409` ProblemDetail if username taken (case-insensitive); `400` validation | `/auth/register` · `RegisterPage` (password + confirm, terms checkbox: client-side only) | — (controller tests) | backend | Fix: bcrypt (B18), unique username (B19). **D18**: username 3-20 chars `[A-Za-z0-9_-]`, unique ignoring case; password ≥ 8 chars; legacy accounts not migrated. **D11**: `gender` optional enum `MALE`/`FEMALE`/`OTHER` (select). Age ≥ 1 as legacy. Confirm-password check is client-side only. On success navigate to `/auth/login` with a success toast. **D21**: French UI texts. |
| 2 | Login | `controller/LoginServlet.java` L66-112, `index.jsp` login modal L118-142 | `POST /api/auth/login` `{username, password}` → `200` `PlayerDto` (session cookie); `401` ProblemDetail. `GET /api/auth/me` → `200` `PlayerDto` / `401`. CSRF token via cookie `XSRF-TOKEN` | `/auth/login` · `LoginPage`; `authGuard` on all other routes | — | backend | Failure message as legacy ("Invalid username or password", in French per D21). New code stores only the principal. Presence is driven by STOMP connect (row 7), not by login. |
| 3 | Logout | `controller/LogoutServlet.java`, `home.jsp` L56-58 | `POST /api/auth/logout` → `204`, invalidates session, closes the user's STOMP sessions (→ `PlayerOffline` when the last one closes, D15) | navbar "Logout" button → `/` | — | backend | Closing the user's STOMP sessions is pending until STOMP exists (row 7). |
| 4 | Landing page | `webApp/index.jsp` (`main` L144-150), `cover.css`, `images/Bg.jpg` | — | `/` · `LandingPage` ("Speedest Game", "Jouer" → login) | — | specified | **D21**: French UI. |
| 5 | Profile view | `controller/ProfileServlet.java#doGet` L53-70, `webApp/profile.jsp` (jQuery AJAX L9-35) | `GET /api/players/me` → `PlayerProfileDto {username, age, gender, gamesPlayed, victories, averageScore}` | `/profile` · `ProfilePage` | — | specified | Fix: no more `?username=` and never the password (B20). **D12**: click ratios not shown and not stored. |
| 6 | Profile edit | `ProfileServlet#doPost` L93-122, `profile.jsp` form L132-243 | `PUT /api/players/me` `{age, gender?}` → `200` `PlayerProfileDto` | same page, "Update"/"Cancel"; success toast "Modification réussie!" | — | specified | Fix: no stale overwrite of stats (B21). **D11** gender select. **D19**: "Changer Mot de Passe" button hidden (out of scope). Username read-only. |
| 7 | Online presence (lobby) | `LoginServlet.sessions` L27/L97-98, `UserStatusWebSocket#broadcast` L435-449, `#onOpen/#onClose` L42-57, `home.jsp` sidebar L17-34 + WS handler L121-153 | `GET /api/players/online` → `[{username, status: ONLINE \| IN_GAME}]`; STOMP `/topic/presence` (`PlayerOnline`, `PlayerOffline`, `PlayerStatusChanged`) | `/lobby` · `LobbyPage`, `OnlinePlayersPanel` | — (`PresenceServiceTest`) | specified | Online ⇔ ≥1 authenticated STOMP session (fixes B17). Current user excluded from the list (as legacy). **D15**: several tabs allowed, offline when the last tab disconnects. **D16**: players in a running game are shown `IN_GAME`. |
| 8 | Invitations: create (game setup) | `home.jsp` game form L65-86 + `sendInvitation` L178-201, `UserStatusWebSocket#handleInvite` L278-304 | `POST /api/invitations` `{invitees: [username], settings: {totalRounds, roundSeconds}}` → `201` `InvitationDto {id, inviter, invitees[], accepted[], settings, status, expiresAt}`; each invitee gets `InvitationReceived` on `/user/queue/invitations`. `400` invalid settings/invitees; `409` invitee offline, in a game (D16) or already in a pending invitation | `/lobby` · `GameSetupForm` (rounds, seconds per round, multi-select of online players) | `InvitationFlowParityTest` (to write once the backend proposes the invitation service API) | specified | **D7**: only the selected players are invited (fixes B11), 1+ invitees, not self, no duplicates. **D9**: no "number of combinations". **D10**: rounds 1-20 (default 3), round 5-120 s (default 10). **D2**: label "seconds per round" (fixes B2). |
| 9 | Invitations: respond, cancel, expire | `home.jsp` `confirm()` L154-163, `UserStatusWebSocket#handleResponse` L306-321 | `POST /api/invitations/{id}/accept` / `/decline` → `200` `InvitationDto`; `InvitationUpdated` to inviter + all invitees on `/user/queue/invitations` | `InvitationDialog` (PrimeNG dialog, Accept / Decline, countdown to `expiresAt`) | same | specified | **D7**: game starts when **all** invitees accepted; **any decline cancels** the invitation (`status: DECLINED`, `declinedBy`) and notifies everyone (fixes B12); a pending invitation **expires after 60 s** (`status: EXPIRED`, notify everyone). Accept/decline on a non-pending invitation → `409`. |
| 10 | Game start + card deal | `UserStatusWebSocket#beginGame` L379-432, `#generateGameCards` L452-466, `home.jsp` redirect handler L164-174 | When the last invitee accepts: `GameService` creates the game (id), state = `GameEngine.start(...)`; each player gets `GameStarted {gameId}` on `/user/queue/games`; `GET /api/games/{id}` → `GameSnapshotDto` | navigate to `/games/:id` · `GamePage`, `GameTable`, `PlayerCardList`, `CenterCard` | `CardGenerationParityTest`, `RoundTimerParityTest` | specified | Player order: inviter first, then invitees in invitation order (legacy order was arbitrary HashSet order). **D1**: keep the 11 symbols without `G`. ≥ 2 distinct players (D7). |
| 11 | Rounds & timer | `game.jsp` `initializeGame` L104-118, `startTimer/resetTimer` L189-212, `finTour` handler L143-150; `UserStatusWebSocket` case `finTour` L77-81, `#handleFinTour` L264-276 | Server-driven: `TaskScheduler` per game fires at `roundEndsAt` → `GameEngine.roundTimerExpired` → `/topic/game/{id}` `RoundStarted {round, totalRounds, roundEndsAt, deal, scores}` or `GameFinished` | `RoundTimer` (countdown from `roundEndsAt`), notification "Time's up! Moving to the next round." (French per D21) | `RoundTimerParityTest` | specified | One server timer per game (fixes B9). Kept: N rounds of T s, new cards each round, scores carry over. **D2** seconds per round, **D10** limits. **D17**: a disconnected player's game continues, score kept, `GET /api/games/{id}` restores the view. |
| 12 | Claims & scoring | `game.jsp` `handleSameColorButton` L255-273, `handleSameFormatButton` L274-293, `handleboth` L295-315; `UserStatusWebSocket#handleUpdateScore` L323-327, `#broadcastUpdatedScores` L330-342 | STOMP SEND `/app/game/{id}/claim` `{claim: SAME_COLOR\|SAME_SYMBOL\|BOTH, dealVersion}`; `GameEngine.claim` → `CORRECT`: `/topic/game/{id}` `ScoresUpdated {scores, deal, scoredBy}`; `WRONG`/`STALE`: `ClaimRejected {reason}` to the sender on `/user/queue/errors`, nothing broadcast | `ClaimButtons` ("The Same Color", "The Same Format", "The Two"; French labels per D21) | `ClaimScoringParityTest` | specified | Server-authoritative (fixes B10). **D8**: rule kept (+1 for any correct claim, BOTH = 1, no penalty), and only the **first correct claim per `dealVersion`** scores; a claim naming another version is `STALE`. |
| 13 | Game end: result, persistence, stats | `game.jsp` L200-203 (`finPartie`); `UserStatusWebSocket#handleResult` L136-251, `#updatePlayerStats` L253-262; `dto/PartieManager.java`; `dao/PartieDao`, `ParticipationDAO`, `JoueurDao` | On `FINISHED`: one transaction saves a **new** `game` row + one `participation` per player + updated `player` stats (`PlayerStats.afterGame`); `/topic/game/{id}` `GameFinished {draw, winner?, loser?, leaders, topScore, trailers, bottomScore, scores}`; `GET /api/games/{id}/result` → `GameResultDto` (same fields) | — | `GameResultParityTest`, `PlayerStatsParityTest` | specified | **D3**: all-zero = draw, no victory (fixes B3). **D4**: tied leaders share a draw (no winner, no victory); tied trailers → no loser; never the same player as winner and loser (fixes B4). **D5**: only the final winner gets a victory (fixes B5). **D6**: one game row per game, stats and participation every game (fixes B6, B7, B8). **D14**: only the game's players receive the event. `game` stores `total_rounds`, `round_seconds`, `game_mode = 'Normal'`; no combinations (D9). |
| 14 | Result page | `webApp/WinnerPage.jsp` | `GET /api/games/{id}/result` | `/games/:id/result` · `GameResultPage` ("Gagnant", "Perdant", "Rejouer" → `/lobby`) | — | specified | **D20**: keep the winner/loser layout; the API also returns all scores. **D3/D4**: a draw shows "Égalité" with the tied leaders; no "Perdant" line when there is no single loser. Scores shown as integers (fixes B29). Legacy 2 s delay before showing: optional animation, not required. |
| 15 | Schema + seed | `model/*.java`, `META-INF/persistence.xml`, `dto/MockData.java` | Flyway `V1__init.sql` (`player`, `game`, `participation`), dev-only seed migration | — | — (`@DataJpaTest` only for custom queries) | backend | **D12**: no `cartes`/`tours` tables, no click ratio/click count columns. **D9**: no combinations column. **D11**: `gender` nullable, check in (`MALE`,`FEMALE`,`OTHER`). **D13**: dev-only seed (separate Flyway location enabled only in the dev profile): `alice`, `bob`, `carol`, bcrypt hash of a dev password documented in the seed file, zero stats. **D18**: unique index on `lower(username)`. |
| 16 | Unused legacy code | `controller/GameServlet.java` (`/game` endpoint), `UserStatusWebSocket#generateCards` L346-376, `dto/CardTables`, `webSocket/MainApp`, `model/Carte`, `model/Tour`, `enums/Forme`, `enums/Couleur`, `dao/CarteDao`, `dao/TourDao`, `test/UserStatusWebSocketTest` (all commented out) | not ported | — | — | specified | **D12**: not ported. |

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

Proposed new schema (backend agent owns the final Flyway SQL): `player(id, username unique, password_hash, age, gender, games_played, victories, average_score, created_at)`, `game(id, played_at, total_rounds, round_seconds, game_mode)`, `participation(id, game_id → game, player_id → player, score, unique(game_id, player_id))`. No combinations, click ratio or click count columns (D9, D12); `gender` nullable enum (D11); unique `lower(username)` (D18).

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

No open questions remain.

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

## Hand-off log

| Date | From → To | What |
|---|---|---|
| 2026-11-15 | transformer → backend | Legacy moved to `legacy/`. Inventory + plan written. Parity tests for rows 10-13 in `backend/src/test/java/com/cardgame/parity/` (all `@Disabled`). Waiting on user decisions Q1-Q21 for behaviour changes; the backend can scaffold and implement rows 1-3, 15 and the game engine contract now. |
| 2026-11-15 | user → transformer → backend | All recommendations accepted (D1-D21). Plan rows, bug decisions, engine contract and parity tests updated: draw-aware `GameResult`, `dealVersion` + `ClaimStatus`, `GameSettings` validation/defaults without combinations, ≥ 2 players. Only class-level `pending port` disables remain. Realtime proposal updated (invitation decline/expiry, `ClaimRejected`, draw shape). |
| 2026-11-15 | backend → frontend, transformer | Rows 1-3 and 15 at `backend`: Spring Boot 4.1.2 scaffold, Flyway `V1__init_schema.sql` (tables `players`, `games`, `participations`), dev seed `db/seed/R__dev_seed_players.sql` (only with the `local` profile; alice/bob/carol, password `password123`), `/api/auth/register\|login\|logout\|me` (operationIds `register`, `login`, `logout`, `me`; response `PlayerDto {id, username, age, gender}`). Session cookie `JSESSIONID` plus CSRF cookie `XSRF-TOKEN`, sent back in the `X-XSRF-TOKEN` header. ProblemDetail `title`/`detail` in French, field errors in `errors[] {field, message}`. Game engine `com.cardgame.game.engine` implemented: all 5 parity classes enabled and passing (89 cases). Open point for rows 10/13: `games` holds only finished games (row 13), but row 10 needs a game id at start. Either keep ids in memory until the end or add a `status` column in V2. |
