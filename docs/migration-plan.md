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

| # | Feature | Legacy sources | Backend (REST / STOMP) | Frontend (route / components) | Parity tests | Status | Notes / open questions |
|---|---------|----------------|------------------------|-------------------------------|--------------|--------|------------------------|
| 1 | Register | `controller/RegisterServlet.java`, `webApp/index.jsp` (signup modal L49-116, `validateForm` L157-171) | `POST /api/auth/register` `{username, password, age, gender}` → `201` `PlayerDto`; `409` ProblemDetail if username taken; `400` validation | `/auth/register` · `RegisterPage` (password + confirm, terms checkbox are client-side only) | — (controller tests) | specified | Fix: bcrypt (B18), unique username (B19). Q11 gender, Q18 username/password rules. Legacy redirects to `index.jsp?successMessage=Compte+successful` → navigate to `/auth/login` with a success toast. Confirm-password check moves to the client + server-side `@AssertTrue`? → recommended client only. |
| 2 | Login | `controller/LoginServlet.java` L66-112, `index.jsp` login modal L118-142 | `POST /api/auth/login` `{username, password}` → `200` `PlayerDto` (session cookie); `401` ProblemDetail. `GET /api/auth/me` → `200` `PlayerDto` / `401`. CSRF token via cookie `XSRF-TOKEN` | `/auth/login` · `LoginPage`; `authGuard` on all other routes | — | specified | Legacy on failure: `index.jsp?error=Invalid username or password` → show the same message. Legacy stores 10 session attributes (L88-99); new code stores only the principal. Login also broadcast `login` presence (L100): new presence is driven by STOMP connect (row 6). |
| 3 | Logout | `controller/LogoutServlet.java`, `home.jsp` L56-58 | `POST /api/auth/logout` → `204`, invalidates session, closes the user's STOMP sessions (→ `PlayerOffline`) | navbar "Logout" button → `/` | — | specified | Legacy redirects to `index.jsp`. |
| 4 | Landing page | `webApp/index.jsp` (`main` L144-150), `cover.css`, `images/Bg.jpg` | — | `/` · `LandingPage` ("Speedest Game", "Jouer" → login) | — | specified | Pure UI. Q21 language of UI texts. |
| 5 | Profile view | `controller/ProfileServlet.java#doGet` L53-70, `webApp/profile.jsp` (jQuery AJAX L9-35) | `GET /api/players/me` → `PlayerProfileDto {username, age, gender, gamesPlayed, victories, averageScore}` | `/profile` · `ProfilePage` | — | specified | Fix (security): legacy returns any profile by `?username=` **including the password** (B20). Click ratios: Q12. |
| 6 | Profile edit | `ProfileServlet#doPost` L93-122, `profile.jsp` form L132-243 | `PUT /api/players/me` `{age, gender}` → `200` `PlayerProfileDto` | same page, "Update"/"Cancel"; success toast "Modification réussie!" | — | specified | Fix: legacy merges the stale session copy and overwrites stats (B21). "Changer Mot de Passe" button does nothing in legacy → out of scope (Q19). Username is read-only. |
| 7 | Online presence (lobby) | `LoginServlet.sessions` L27/L97-98, `UserStatusWebSocket#broadcast` L435-449, `#onOpen/#onClose` L42-57, `home.jsp` sidebar L17-34 + WS handler L121-153 | `GET /api/players/online` → `[{username, status}]`; STOMP `/topic/presence` (`PlayerOnline`, `PlayerOffline`, later `PlayerInGame`?) | `/lobby` · `LobbyPage`, `OnlinePlayersPanel` | — (`PresenceServiceTest`) | specified | Legacy list = users with a live HttpSession (never expires), events from login/logout servlets; WS close is not reported (B17). New: online ⇔ ≥1 authenticated STOMP session. Current user is excluded from the list (home.jsp L27). Q15 multi-tab, Q16 "in game" status. |
| 8 | Invitations: create (game setup) | `home.jsp` game form L65-86 + `sendInvitation` L178-201, `UserStatusWebSocket#handleInvite` L278-304 | `POST /api/invitations` `{invitees: [username], settings: {totalRounds, roundSeconds}}` → `201` `InvitationDto {id, inviter, invitees[], settings, status}`; each invitee gets `InvitationReceived` on `/user/queue/invitations` | `/lobby` · `GameSetupForm` (rounds, timer, pick online players) | `InvitationFlowParityTest` (to write with row 8 service contract) | specified | Legacy invites **every connected socket except the sender's**, ignoring `invitees` (B11). Q7 who is invited, Q9 `combinaison`, Q10 limits, Q2 timer unit. |
| 9 | Invitations: respond | `home.jsp` `confirm()` L154-163, `UserStatusWebSocket#handleResponse` L306-321 | `POST /api/invitations/{id}/accept` / `/decline` → `200` `InvitationDto`; inviter + invitees get `InvitationUpdated` on `/user/queue/invitations` | `InvitationDialog` (PrimeNG dialog, Accept / Decline) | same | specified | Legacy: game starts when accepted count == invited count; **decline is ignored and the game never starts** (B12). Q7. |
| 10 | Game start + card deal | `UserStatusWebSocket#beginGame` L379-432, `#generateGameCards` L452-466, `home.jsp` redirect handler L164-174 | When the last invitee accepts: `GameService` creates the game (id), state = `GameEngine.start(...)`; each player gets `GameStarted {gameId}` on `/user/queue/games`; `GET /api/games/{id}` → `GameSnapshotDto` | navigate to `/games/:id` · `GamePage`, `GameTable`, `PlayerCardList`, `CenterCard` | `CardGenerationParityTest`, `RoundTimerParityTest#startsAtRoundOne...` | specified | Legacy passes the start state through `localStorage` (`playersScore`, `playerCards`, `nbrTours`, `centreCard`, `timer`). Player order = invitees in invitation order, inviter last? → recommended inviter first (legacy order is HashSet order, i.e. arbitrary). Q1 missing `G`. |
| 11 | Rounds & timer | `game.jsp` `initializeGame` L104-118, `startTimer/resetTimer` L189-212, `finTour` handler L143-150; `UserStatusWebSocket` case `finTour` L77-81, `#handleFinTour` L264-276 | Server-driven: `TaskScheduler` per game fires at `roundEndsAt` → `GameEngine.roundTimerExpired` → `/topic/game/{id}` `RoundStarted {round, totalRounds, endsAt, deal, scores}` or `GameFinished` | `RoundTimer` (countdown computed from `endsAt`), notification "Time's up! Moving to the next round." | `RoundTimerParityTest` | specified | Legacy: one timer **per browser**, each browser sends `finTour`, so the server broadcasts N times and deals N times (B9). Behaviour kept: N rounds of T s, new cards each round, scores carry over. Q2 unit, Q10 limits, Q17 disconnects. |
| 12 | Claims & scoring | `game.jsp` `handleSameColorButton` L255-273, `handleSameFormatButton` L274-293, `handleboth` L295-315; `UserStatusWebSocket#handleUpdateScore` L323-327, `#broadcastUpdatedScores` L330-342 | STOMP SEND `/app/game/{id}/claim` `{claim: SAME_COLOR\|SAME_SYMBOL\|BOTH, dealVersion}`; server judges with `GameEngine.claim`; if correct → `/topic/game/{id}` `ScoresUpdated {scores, deal, dealVersion, scoredBy}`; wrong → `/user/queue/errors`? (Q8) | `ClaimButtons` ("The Same Color", "The Same Format", "The Two") | `ClaimScoringParityTest` | specified | Legacy is **client-authoritative**: the browser checks the claim and sends its whole local score map, the server rebroadcasts it (lost updates, cheating, B10). New: server-authoritative, same rule (+1, no penalty, new deal after each correct claim). Q8. |
| 13 | Game end: winner, persistence, stats | `game.jsp` L200-203 (`finPartie`); `UserStatusWebSocket#handleResult` L136-251, `#updatePlayerStats` L253-262; `dto/PartieManager.java`; `dao/PartieDao`, `ParticipationDAO`, `JoueurDao` | On `GameFinished`: one transaction saves `game` + one `participation` per player + updated `player` stats; `/topic/game/{id}` `GameFinished {winner, winnerScore, loser, loserScore, scores}`; `GET /api/games/{id}/result` → `GameResultDto` | — | `GameResultParityTest`, `PlayerStatsParityTest` | specified | Many legacy bugs here: B3, B4, B5, B6, B7, B8, B24. Q3, Q4, Q5, Q6. Participation score = rounded final score. `Partie.valeurTimer` = round seconds, `nombreTours` = total rounds, `modeJeu` = `"Normal"`, `nombreCombinaisons` never set. |
| 14 | Result (winner) page | `webApp/WinnerPage.jsp` | `GET /api/games/{id}/result` | `/games/:id/result` · `GameResultPage` ("Gagnant", "Perdant", "Rejouer" → `/lobby`) | — | specified | Legacy reads `localStorage` and waits 2 s before showing (L94). Shows scores as `3.0` (B29). Q3/Q4 decide what a draw looks like. Q20 full ranking. |
| 15 | Schema + seed | `model/*.java`, `META-INF/persistence.xml`, `dto/MockData.java` | Flyway `V1__init.sql` (`player`, `game`, `participation`), dev-only seed migration | — | — (`@DataJpaTest` only for custom queries) | specified | `MockData` holds **no players**, only fake invitations (`sender` → `user1`, `user2`). Q12 unused tables/columns, Q13 seed content. Credentials in `persistence.xml` are committed (B27). |
| 16 | Unused legacy code | `controller/GameServlet.java` (`/game` endpoint), `UserStatusWebSocket#generateCards` L346-376, `dto/CardTables`, `webSocket/MainApp`, `model/Carte`, `model/Tour`, `enums/Forme`, `enums/Couleur`, `dao/CarteDao`, `dao/TourDao`, `test/UserStatusWebSocketTest` (all commented out) | not ported | — | — | specified | Recommended: do not port (Q12). `TourDao` would NPE (no EMF). |

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

Proposed new schema (backend agent owns the final Flyway SQL): `player(id, username unique, password_hash, age, gender, games_played, victories, average_score, created_at)`, `game(id, played_at, total_rounds, round_seconds, game_mode)`, `participation(id, game_id → game, player_id → player, score, unique(game_id, player_id))`. Optional columns depend on Q9 / Q12.

### DAOs

Every DAO (`CarteDao`, `JoueurDao`, `ParticipationDAO`, `PartieDao`, `TourDao`) implements `IDao` (`findById`, `findAll`, `save` = persist-or-merge, `update` = save, `delete`) and creates **its own `EntityManagerFactory`** in its constructor (`handleResult` builds three per game end). `JoueurDao.findByPseudo` returns `null` when absent (and would throw `NonUniqueResultException` with duplicate usernames). `TourDao` never initialises its EMF. Target: Spring Data `PlayerRepository` (`findByUsernameIgnoreCase`), `GameRepository`, `ParticipationRepository`.

### Suspected legacy bugs

Behaviour the user must decide on is marked with the question number. Others are fixed implicitly by the target architecture (security, infrastructure) and need no player-visible decision.

| # | Bug | Where | Decision |
|---|---|---|---|
| B1 | Symbol list skips `G` (`A-F, H-L`) | `UserStatusWebSocket` L27, L381 | Q1 |
| B2 | Timer label says "minutes per game", code uses seconds per round | `home.jsp` L72-73 vs `game.jsp` L189-207 | Q2 |
| B3 | `maxScore = Double.MIN_VALUE` (smallest positive double): all-zero scores → `winner = null`, nobody gets a victory, page shows `null(Score: 4.9E-324 )` | L152 | Q3 |
| B4 | Ties broken by map iteration order (arbitrary); when all scores are equal (> 0) the **same player is winner and loser** | L166-178 | Q4 |
| B5 | Victory credited to the **running** leader during the loop: every player who is max-so-far when visited gets a victory (e.g. scores `bob=3, alice=5` in that order → both credited) | L180-184 | Q5 |
| B6 | `processedUsers` is static and never cleared: stats and participations are saved only for each user's **first game since server start** | L40, L180-181 | Q6 |
| B7 | `PartieManager` singleton: every game after the first reuses (merges over) the same `Partie` row | `PartieManager` L9-17, L155-160 | Q6 |
| B8 | Every client sends `finPartie`, so results are computed and broadcast N times (masked by B6/B7) | `game.jsp` L200-203 | fixed by server-driven end |
| B9 | Every client sends `finTour`: N round broadcasts, N new deals and N score overwrites per round | L77-81 | fixed by server-driven timer |
| B10 | Client-authoritative scoring: the browser sends its full score map, last writer wins (lost points), any client can send any scores | `game.jsp` L255-315, L323-342 | fixed (server-authoritative), confirm in Q8 |
| B11 | `invite` ignores `invitees` and invites every other open socket (including players on a game page, who never answer) | L292-300 | Q7 |
| B12 | A decline is ignored; the game then never starts and the inviter is never told | L306-321 | Q7 |
| B13 | One global game: a new invite overwrites the running game's settings; `players` is overwritten by the next game | L32-38, L283-285, L385 | fixed (per-game state) |
| B14 | `players` aliases the `acceptedInvitations` set and the inviter is added to it | L385-386 | fixed |
| B15 | `scoreUpdate`, `finTour` and the end `redirect` go to **all** sockets: lobby users get redirected to the winner page of someone else's game | L246-250, L271-275, L337-341 | fixed (`/topic/game/{id}`) |
| B16 | Socket identity from `?username=` (impersonation); NPE if missing | L45-49 | fixed (principal) |
| B17 | Presence list = static map of HttpSessions, never cleaned on expiry; socket close not reported | `LoginServlet` L27; `home.jsp` L21-31; L54-57 | fixed (STOMP presence) |
| B18 | Plain-text passwords; login loads all players | `LoginServlet` L82-84 | fixed (bcrypt, query by username) |
| B19 | No unique username; mismatch forwards to missing `register.jsp`; bad age → 500 | `RegisterServlet` L29-35 | fixed, Q18 |
| B20 | Profile GET exposes any player's data **and password** | `ProfileServlet` L54-70 | fixed (`/me`, DTO) |
| B21 | Profile POST merges the stale login-time entity, reverting stats earned since login; error page missing | `ProfileServlet` L101-105, L119-120 | fixed |
| B22 | XSS / JSON injection: unescaped `error`, `successMessage`, usernames; JSON built with `String.format` / concatenation | `index.jsp` L6-8/L23-25, `home.jsp` L28, L297, L437 | fixed (Angular escaping, Jackson) |
| B23 | Dead/broken client code: `#scoreButton` click uses undefined `cartes`; `stateUpdate` uses undefined `centerCartes`; `querySelector('#'+username)` throws for usernames starting with a digit or containing special characters | `game.jsp` L164-174, L125-130, L258/276/297 | fixed, Q18 |
| B24 | `joueurDao.save(j)` before the null check (NPE for unknown user); NPE if stats are null | L214-218, L256 | fixed |
| B25 | `nombreCombinaisons`, click ratios and click counts never computed; `Tour`/`Carte` never written | various | Q9, Q12 |
| B26 | Hard-coded `ws://localhost:8080/LearnSocket_war_exploded` and context path | `home.jsp` L118, `game.jsp` L120, `profile.jsp` L14, `WinnerPage.jsp` L70 | fixed (same-origin, proxy) |
| B27 | DB credentials committed | `META-INF/persistence.xml` L14-16 | fixed (env vars); old password is compromised |
| B28 | New `EntityManagerFactory` per DAO instance, no transaction spanning a game save; `TourDao` EMF null | `dao/*` | fixed |
| B29 | Scores sent as strings of doubles (`"3.0"`) and shown as such | L237-238, `WinnerPage.jsp` L85-86 | fixed (integers) |
| B30 | Empty timer/rounds → `NumberFormatException`, invite silently dropped; `0` rounds behaves as 1 round; `0` s timer ends after ~1 s | L283-285, `game.jsp` L189-207 | Q10 |

### Open questions (need a user decision)

Recommended answers are what the specs above assume unless the user says otherwise.

| # | Question | Recommendation |
|---|---|---|
| Q1 | Symbols skip `G` (11 letters). Keep, or use `A-L` (12)? | Keep the 11 legacy letters (parity; harmless). |
| Q2 | Timer: seconds per round (what the game does) or minutes per game (what the label says)? | Seconds per round; fix the label. |
| Q3 | All scores 0: legacy shows no winner (`null`). What should happen? | Declare a **draw**: no winner, no loser, no victory credited; result page says "Draw". |
| Q4 | Tie for the highest (or lowest) score? | Draw among the tied leaders: nobody gets a victory, the result lists all tied players; same for the lowest score. Never the same player as winner and loser. |
| Q5 | Victory credited to every "running leader" (often 2+ victories per game). Keep? | Fix: only the final winner gets a victory. |
| Q6 | Stats/participations saved only for a user's first game since restart, and all games share one `Partie` row. Keep? | Fix: one game row and one participation per player per game, stats updated every game. |
| Q7 | Invitations: legacy invites everyone online and ignores declines. | Invite only the selected players (picked from the online list, 1+); start when all accept; any decline cancels the invitation and notifies everyone; pending invitations expire after 60 s. |
| Q8 | Claim rules: +1 for any correct claim, BOTH worth the same 1 point, no penalty, so spamming all three buttons is a winning strategy. Change? | Keep the legacy rule for parity now (pinned in `ClaimScoringParityTest`). Claims carry `dealVersion`, and a claim on an outdated deal is rejected (only the first correct claim on a deal scores). Revisit penalties/BOTH = 2 later as a new feature. |
| Q9 | "Number of Combinations" is collected but never used. | Drop it from the form, API and schema. |
| Q10 | Limits for rounds and timer (legacy: none, crashes on empty)? | Rounds 1-20 (default 3), round timer 5-120 s (default 10); `400` ProblemDetail outside the range. |
| Q11 | Gender: register offers Homme/Femme, profile is free text. | Optional enum `MALE`, `FEMALE`, `OTHER`, shown as a select in both places. |
| Q12 | Unused data: `cartes`/`tours` tables, click ratios and click counts (always 0, but shown on the profile). | Do not create them; remove the two ratio fields from the profile page. |
| Q13 | Seed data: `MockData` has no players. What should dev seed contain? | Dev-only Flyway seed (not in prod): players `alice`, `bob`, `carol`, password `password` (bcrypt), zero stats. |
| Q14 | Results/scores were broadcast to everyone online. | Confirm: only the game's players receive game events (`/topic/game/{id}`). |
| Q15 | Same user in two tabs? | Allowed; online while at least one tab is connected; game events go to all their tabs. |
| Q16 | Can a player in a running game be invited? | No: shown as "in game" in the lobby and rejected with `409`. |
| Q17 | A player disconnects mid-game? | Game continues; their score stays; on reconnect `GET /api/games/{id}` restores the view. |
| Q18 | Username/password rules (legacy: none). | Username 3-20 chars `[A-Za-z0-9_-]`, unique case-insensitively; password at least 8 chars. Legacy accounts are not migrated. |
| Q19 | "Changer Mot de Passe" button (no behaviour in legacy). | Out of scope for parity; hide it (add later as a feature). |
| Q20 | Result page shows only winner and loser. | Keep that layout; the API also returns all scores so a ranking can be added later. |
| Q21 | UI language: legacy mixes French and English labels. | Pick one (recommend French texts as in the key labels "Gagnant", "Perdant", "Rejouer", "Jouer"), via Angular i18n later. |

## Contract for backend: game engine

Pure Java, package `com.cardgame.game.engine`, **no Spring, no I/O, no static mutable state**. All
types below are `public`. The parity tests in `backend/src/test/java/com/cardgame/parity/` compile
against exactly these signatures (verified against a throwaway legacy-faithful reference implementation:
66/66 cases pass when `@Disabled` is removed).

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
    public static final List<Character> SYMBOLS;  // A B C D E F H I J K L  (legacy order, no G - Q1)
    public static final List<CardColor> COLORS;   // RED, BLUE, GREEN       (legacy order)
    public CardDealer(java.util.random.RandomGenerator random);
    public Deal deal(List<String> players);       // IllegalArgumentException if empty
}

public enum Claim { SAME_COLOR, SAME_SYMBOL, BOTH }
public final class ClaimRules {
    public static boolean isCorrect(Claim claim, Card playerCard, Card centerCard);
    public static int pointsFor(Claim claim, Card playerCard, Card centerCard);   // 1 or 0
}

public record GameSettings(int totalRounds, java.time.Duration roundDuration, int combinations) {}
public enum GamePhase { IN_PROGRESS, FINISHED }
public record GameState(List<String> players, GameSettings settings, GamePhase phase, int currentRound,
                        SequencedMap<String, Integer> scores, Deal deal, java.time.Instant roundStartedAt) {
    public java.time.Instant roundEndsAt();       // roundStartedAt + settings.roundDuration
}
public record ClaimOutcome(boolean correct, GameState state) {}
public record PlayerOutcome(String player, int score, boolean victory) {}
public record GameResult(String winner, int winnerScore, String loser, int loserScore,
                         List<PlayerOutcome> outcomes) {}         // outcomes in player order

public final class GameEngine {
    public GameEngine(java.util.random.RandomGenerator random, java.time.Clock clock);
    public GameState start(List<String> players, GameSettings settings);
    public ClaimOutcome claim(GameState state, String player, Claim claim);
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
3. **start**: phase `IN_PROGRESS`, `currentRound = 1`, scores 0 for every player **in the given player
   order** (the map is insertion-ordered and tests compare order), one deal, `roundStartedAt = clock.instant()`.
   `GameState` must hold immutable copies so `equals` works and states are safe to share across threads.
4. **claim**: `IllegalStateException` if not `IN_PROGRESS`, `IllegalArgumentException` if the player is not
   in the game. Correct → claimer's score +1 (never more, also for `BOTH`), a **new deal** for everyone,
   round and `roundStartedAt` unchanged. Wrong → `correct = false` and the **same** state (no random draw).
5. **roundTimerExpired**: `IllegalStateException` if not `IN_PROGRESS`. If `currentRound < totalRounds` →
   `currentRound + 1`, new deal, `roundStartedAt = clock.instant()`, scores unchanged. Otherwise phase
   `FINISHED`, same scores, same deal, **no** random draw.
6. **determineResult** (legacy semantics, pending Q3-Q5): iterate in map order; winner = first entry with
   score strictly greater than the running max, which starts at `Double.MIN_VALUE` (so a 0 score never
   wins); loser = first entry strictly below the running min (starts at `Double.MAX_VALUE`). Tests whose
   outcome depends on Q3/Q4/Q5 are separately `@Disabled("pending user decision: Qn")`; implement the
   recommended answer once the user decides, and the transformer will update those tests first.
7. **PlayerStats.afterGame**: `gamesPlayed + 1`, `victories + (victory ? 1 : 0)`,
   `averageScore = (averageScore * gamesPlayed + score) / (gamesPlayed + 1)`.

Not in the engine (service level, see rows 8-13): invitations, scheduling (`TaskScheduler` calls
`roundTimerExpired` at `roundEndsAt()`), locking/serialising access per game, persistence, messaging,
`dealVersion` (Q8: a counter the service increments whenever `state.deal()` changes).

## Hand-off log

| Date | From → To | What |
|---|---|---|
| 2026-11-15 | transformer → backend | Legacy moved to `legacy/`. Inventory + plan written. Parity tests for rows 10-13 in `backend/src/test/java/com/cardgame/parity/` (all `@Disabled`). Waiting on user decisions Q1-Q21 for behaviour changes; the backend can scaffold and implement rows 1-3, 15 and the game engine contract now. |
