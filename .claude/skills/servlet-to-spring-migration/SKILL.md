---
name: servlet-to-spring-migration
description: Playbook for migrating the legacy Jakarta EE servlet/JSP/WebSocket card game to Spring Boot + Angular. Covers legacy→target mapping tables, the per-feature migration workflow, the migration-plan format, and the known legacy pitfalls. Use when analysing legacy code, planning a feature's migration, or verifying parity.
---

# Servlet → Spring Boot + Angular migration

> **Status: complete.** All features are `verified` and `legacy/` was deleted. The original code is
> in commit `62e42ab` (`git show 62e42ab:legacy/<path>`). Use this playbook to understand past
> decisions, or to port any behaviour of the original app that turns out to be missing.

## Legacy → target mapping

| Legacy | Target |
|--------|--------|
| `HttpServlet` (`doGet`/`doPost`) in `controller/` | `@RestController` in `com.cardgame.<feature>` returning DTO records as JSON |
| JSP page (`*.jsp`) + inline JS + `style.css`/`cover.css` | Angular route + standalone component in `frontend/src/app/features/<feature>/`, PrimeNG + Tailwind |
| `request.getParameter(...)` | `@RequestBody` record with `@Valid`, or `@PathVariable`/`@RequestParam` |
| `request.setAttribute` + forward to JSP | JSON response the Angular component renders |
| `response.sendRedirect(...)` | Angular `Router.navigate(...)` after a successful API call |
| `HttpSession.setAttribute("user", ...)` | Spring Security `SecurityContext` in the session, read with `@AuthenticationPrincipal` |
| `LoginServlet` / `LogoutServlet` / `RegisterServlet` | `AuthController` (`POST /api/auth/login`, `/logout`, `/register`, `GET /api/auth/me`) |
| `@ServerEndpoint("/userStatus")` + `?username=` param | STOMP endpoint `/ws`, user from the authenticated session |
| `session.getBasicRemote().sendText(json)` loops over `sessions` | `SimpMessagingTemplate.convertAndSend("/topic/...")` or `convertAndSendToUser(...)` |
| `static Set<Session>`, `static Map invitations`, static counters | Spring `@Service` with per-game state (`ConcurrentHashMap<Long, GameState>`) or DB rows |
| Hand-rolled timer and counters in static fields | `TaskScheduler` + `ScheduledFuture` per game, cancelled on finish |
| `DAO` + `Persistence.createEntityManagerFactory(...)` per DAO | Spring Data `JpaRepository` interfaces |
| `persistence.xml` + EclipseLink + MySQL | Spring Boot autoconfig, Hibernate, PostgreSQL, Flyway |
| Hardcoded DB credentials | Env vars / `application-local.yml` (gitignored) |
| Gson | Jackson (Spring Boot default) |
| Lombok on models | Records for DTOs, plain Java entities |
| `MockData` | Dev-only Flyway seed migration |
| French names (`Joueur`, `Partie`, `Tour`, `Carte`, `Couleur`, `Forme`) | English names, mapped in `docs/GLOSSARY.md` |

## Per-feature workflow

Suggested feature order: **auth → player profile → lobby/online presence → invitations → game start
→ rounds/cards/timer → scoring/winner**.

For each feature:

1. **Inventory** (transformer): list every legacy file involved. For each servlet, JSP and WebSocket
   message type, write down the inputs, outputs, side effects (DB writes, broadcasts), and error cases.
2. **Specify** (transformer): add a row to `docs/migration-plan.md`. Describe the target REST endpoints
   and STOMP destinations, plus the Angular routes. Write parity tests (`@Disabled`) for every rule.
   Add new terms to the glossary.
3. **Backend** (backend agent): Flyway migration → entities → repositories → services/rules → controllers
   and STOMP handlers → turn the parity tests on → update `docs/realtime-protocol.md` → `./mvnw verify`.
4. **Frontend** (frontend agent): regenerate the API client → feature routes and components → realtime
   wiring → tests → build.
5. **Verify** (transformer): parity tests are green, the behaviour matches legacy (or the difference was
   approved), and the Playwright flow passes if there is one. Set the status to `verified`.

## `docs/migration-plan.md` format

```markdown
| # | Feature | Legacy sources | Backend (REST / STOMP) | Frontend (route / components) | Parity tests | Status | Notes / open questions |
|---|---------|----------------|------------------------|-------------------------------|--------------|--------|------------------------|
| 1 | Register | RegisterServlet, index.jsp | POST /api/auth/register | /auth/register · RegisterPage | — | todo | Legacy stores plain-text password? → bcrypt |
```

Statuses: `todo` → `specified` → `backend` → `frontend` → `verified`.

## Known legacy pitfalls to look for

- **Static mutable state in `UserStatusWebSocket`**: shared across all games and users, not thread-safe,
  lost on restart. Make it per-game service state.
- **Trusting `?username=` on the WebSocket**: anyone can pretend to be anyone. Use the authenticated principal.
- **A new `EntityManagerFactory` per DAO instance**: leaks resources and has no transaction boundaries.
- **Plain-text or weakly hashed passwords**: use bcrypt. Existing users aren't migrated.
- **Game logic mixed with transport**: pull rules out into pure classes (`GameEngine`) before porting the messaging.
- **Unseeded `Random`**: inject it, so tests are predictable.
- **Mixed-up JSON message shapes**: catalogue every `type` the legacy WebSocket sends or receives
  before designing the STOMP protocol.

## Rules

- Legacy is the **behaviour reference**, not a design reference. Keep what it does. Re-design how it does it.
- The original code is read from git history (`62e42ab`), never restored into the tree.
- Write every behaviour difference from legacy (bug fixes, security fixes) in the plan's notes column,
  and get the user's agreement for anything a player would notice.
