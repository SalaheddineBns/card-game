# Glossary (English ↔ French legacy)

New code uses the **English** term. The French column is what you will find in `legacy/`
(class, field, column, JSON key or UI label). Check this file before you name anything; add a row
when you introduce a new domain term.

Legend for "Where": E = JPA entity / DB column, W = WebSocket JSON key or action, U = UI label (JSP), C = Java code.

## Core domain

| English (new code) | French / legacy name | Where | Meaning / notes |
|---|---|---|---|
| Player | `Joueur` (table `joueurs`) | E, C | A registered account that can play. |
| Username | `Pseudo` / `pseudo`, `username` | E, W, U | Unique player handle. Legacy has **no unique constraint**; the new schema adds one. |
| Password | `MotDePasse` / `motDePasse`, `password` | E, U | Legacy stores plain text. New: bcrypt hash in `password_hash`. |
| Password confirmation | `confirm_password`, "Repeat your password" | U | Register form only, never stored. |
| Age | `Âge` / `âge`, `age` | E, U | Integer, legacy requires `>= 1` (client-side only). |
| Gender | `Genre` / `genre` ("Homme" / "Femme") | E, U | Legacy free text. New: optional enum `MALE` (Homme), `FEMALE` (Femme), `OTHER` (Autre) (D11). |
| Games played | `NombrePartiesJouées` / `nombrePartiesJouées`, `nbrPartieJouées` | E, U | Counter, incremented at game end. |
| Victories (wins) | `NombreVictoires` / `nombreVictoires`, `nbrVictoire` | E, U | Counter, incremented for the winner at game end. |
| Average score | `ScoreMoyen` / `scoreMoyen`, `scoreMoy` | E, U | Running mean of the player's final scores. |
| Successful click ratio | `RatioClicRéussi` / `ratioClicRéussi`, `ratioClicReussi` | E, U | **Never computed in legacy** (always 0.0). Not ported (D12). |
| Fast click ratio | `RatioClicRapide` / `ratioClicRapide` | E, U | **Never computed in legacy** (always 0.0). Not ported (D12). |
| Game | `Partie` (table `parties`) | E, C | One match between 2+ players, made of several rounds. |
| Game played at | `DateHeure` / `dateHeure` | E | Timestamp the game was saved (legacy: at game end). |
| Total rounds / round count | `NombreTours` / `nombreTours`, `nbrTours`, `numberTours`, **`nbrPartie`** (invite form), "Number of Tours" | E, W, U | Number of rounds in a game. **The invite JSON key `nbrPartie` means rounds, not games.** |
| Round duration (timer) | `ValeurTimer` / `valeurTimer`, `timer`, `timerDuration` | E, W, U | Seconds per round. **The lobby label says "minutes per game" but game.jsp uses it as seconds per round.** |
| Combination count | `NombreCombinaisons` / `nombreCombinaisons`, `combinaison` | E, W, U | Collected in the invite form, **never used by any rule** and never persisted. **Dropped** (D9): do not use in new code. |
| Game mode | `ModeJeu` / `modeJeu` | E | Always the constant `"Normal"` in legacy. |
| Round | `Tour` (table `tours`), `tour`, `currentTour` | E, W, U | One timed period of a game. Translate as Round, **never "Turn"** (all players play simultaneously). |
| Center card | `CarteCentrale` / `carteCentrale`, `centreCard`, `centerCards`, `CenterCard` | E, W, C | The shared card every player compares against. |
| Card | `Carte` (table `cartes`), `carte` | E, W, C | See "two card models" below. |
| Player card (hand) | `UserCard`, `playerCards`, `cartes` | W, C | The single card each player holds at a given moment. |
| Participation | `Participation` (table `participations`) | E | Link Player ↔ Game with the player's final score. Keep the name `Participation`. |
| Score | `Score` / `score`, `scores`, `playersScore` | E, W | Points a player earned in one game. |
| Successful clicks | `NombreClicRéussi` | E | Per participation; **never written** in legacy. |
| Fast clicks | `NombreClicRapide` | E | Per participation; **never written** in legacy. |
| Invitation | `invitation`, `invitations` | W, C | Request from an inviter to other players to join a game. |
| Inviter | `sender`, `senderUsername` | W, C | Player who created the invitation (and is a player of the game). |
| Invitee | `invitee`, `inviteeUsername`, `invitees` | W, C | Player asked to join. |
| Accept / Decline | `response: "accept"` / `"reject"` | W | Invitee's answer. New code uses **Decline** (not Reject). |
| Accepted invitations | `acceptedInvitations` | C | Invitees that accepted, per inviter. |
| Winner | `winner`, "Gagnant" | W, U | Player with the highest final score. |
| Loser | `loser`, "Perdant" | W, U | Player with the lowest final score. |
| Final results | `resultat` | W | Map username → final score sent at game end. |
| Game details | `gameDetails`, `partieDetails`, `PartieDetails` | W, C | Game settings payloads (see Total rounds, Round duration). New: `GameSettings`. |
| Online / connected players | `connectedUsers`, "Connected Users" | U, C | Lobby presence list. |
| Lobby | `home.jsp` ("Game Setup") | U | Page with presence list + game setup/invite form. |
| Draw | (none in legacy; UI "Égalité") | – | Result with no single winner: all-equal scores (incl. all zero) or a tie for the top score (D3, D4). Nobody gets a victory. |
| Leaders | – | – | Players with the top score at game end (`GameResult.leaders`). Winner = the single leader. |
| Trailers | – | – | Players with the bottom score, empty when everybody is equal (`GameResult.trailers`). Loser = the single trailer. |
| Deal | (none; legacy regenerates `cartes` + `centerCards`) | – | One distribution of player cards + center card. |
| Deal version | – | – | Counter of deals in a game (`dealVersion`, starts at 1, +1 per new deal). A claim must name the current one (D8). |
| Claim status | – | – | `CORRECT` (+1, new deal), `WRONG` (nothing happens), `STALE` (claim named an old deal). |
| Invitation status | – | – | `PENDING`, `ACCEPTED` (game started), `DECLINED` (someone declined, cancelled), `EXPIRED` (60 s without all answers) (D7). |
| Player status (presence) | – | – | `ONLINE`, `IN_GAME` (D16). |
| Play again | "Rejouer" | U | Winner page button back to lobby. |
| Play | "Jouer" | U | Landing page call-to-action (opens login). |
| Sign in / Login | "Connexion", "Sign in" | U | |
| Sign up / Register | "Créer un compte", "Sign up" | U | |
| Profile update succeeded | "Modification réussie!" | U | |

## Two card models (important)

The legacy code contains **two unrelated card models**. Do not mix them up.

| English | French | Values | Used by the game? |
|---|---|---|---|
| Card symbol | runtime `carte` (a `char`), UI "Format" ("The Same Format" button) | `A B C D E F H I J K L` (11 letters, **no G**) | **Yes**: this is what players compare. |
| Card color (runtime) | runtime `color` | `red`, `blue`, `green` | **Yes**. New enum `CardColor { RED, BLUE, GREEN }`, wire value lower-case. |
| Shape (playing-card rank) | `Forme` enum: `AS, ROI, DAME, VALET, DIX, NEUF, HUIT, SEPT, SIX, CINQ, QUATRE, TROIS, DEUX` | Ace, King, Queen, Jack, Ten, Nine, Eight, Seven, Six, Five, Four, Three, Two | **No**: only on the unused `Carte` entity. |
| Color (playing-card suit) | `Couleur` enum: `COEUR, PIQUE, CARREAU, TREFLE` | Hearts, Spades, Diamonds, Clubs | **No**: only on the unused `Carte` entity. |

## Claims (player actions in a round)

| English (new) | Legacy button / function | Rule |
|---|---|---|
| `Claim.SAME_COLOR` | "The Same Color" / `handleSameColorButton` | Correct if my card color == center color. |
| `Claim.SAME_SYMBOL` | "The Same Format" / `handleSameFormatButton` | Correct if my card symbol == center symbol. |
| `Claim.BOTH` | "The Two" / `handleboth` | Correct if both symbol and color match. |

## Legacy WebSocket actions

| Legacy `action` | English name (new protocol) | Direction |
|---|---|---|
| `login` | `PlayerOnline` | server → all |
| `logout` | `PlayerOffline` | server → all |
| `invite` | create invitation | client → server |
| `invitation` | `InvitationReceived` | server → invitee |
| `response` | accept / decline invitation | client → server |
| `redirect` (to `game.jsp`) | `GameStarted` | server → players |
| `updateScore` | submit claim (new: server-validated) | client → server |
| `scoreUpdate` | `ScoresUpdated` (+ new deal) | server → all |
| `finTour` (client) | round timer expired (new: server-driven) | client → server |
| `finTour` (server) | `RoundStarted` | server → all |
| `finPartie` | game finished (new: server-driven) | client → server |
| `redirect` (to `WinnerPage.jsp`) | `GameFinished` | server → all |
| `stateUpdate` | none (dead code, never sent) | – |
