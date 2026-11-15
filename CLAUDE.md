# Card Game

A multiplayer card game being migrated from a **Jakarta EE servlet/JSP app** to
**Spring Boot (backend) + Angular (frontend)**.

## Current state (legacy)

- Servlets in `controller/` (Login, Register, Profile, Game, Logout), JSP views, `HttpSession` auth.
- One raw WebSocket `@ServerEndpoint("/userStatus")` (`webSocket/UserStatusWebSocket.java`) holding
  **all game logic** (invitations, rounds, timer, card generation) in static mutable state.
- EclipseLink JPA + MySQL, DAOs that each build their own `EntityManagerFactory`.
- Domain is in French: `Joueur`, `Partie`, `Tour`, `Carte`, `Participation`, `Couleur`, `Forme`.

## Target architecture

```
legacy/        ← the current app, moved here with git mv. READ-ONLY until parity, then deleted
backend/       ← Spring Boot 4.x, Java 25, Maven, PostgreSQL, Flyway
frontend/      ← Angular (latest), Tailwind 4, PrimeNG (Aura preset)
docs/          ← migration-plan.md, GLOSSARY.md, realtime-protocol.md, adr/
compose.yaml   ← local PostgreSQL
```

| Concern       | Decision |
|---------------|----------|
| Strategy      | Parallel rewrite, not in-place. Legacy stays untouched as the behavioural reference |
| Backend       | `com.cardgame`, package-by-feature (`auth/`, `player/`, `game/`, `invitation/`) |
| Database      | PostgreSQL, Flyway owns the schema (`ddl-auto=validate`), fresh start, seed from legacy `MockData` |
| Auth          | Spring Security, session cookie (HttpOnly) + CSRF. Angular dev proxy, so everything is same-origin |
| Real-time     | Spring WebSocket + STOMP (`/topic/game/{id}`, `/user/queue/invitations`). Game state lives in a Spring service, never in static fields |
| API contract  | springdoc OpenAPI → generated Angular client. STOMP messages documented in `docs/realtime-protocol.md` |
| Naming        | English in new code. `docs/GLOSSARY.md` maps English ↔ French legacy names |
| Testing       | Backend: JUnit 5 + slices + Testcontainers. Frontend: Vitest + Testing Library + Playwright. Parity tests before each game rule is ported |

## Agents and how they work together

Subagents cannot call each other. **The main session coordinates them**:

1. **transformer**: inventories a legacy feature, updates `docs/migration-plan.md`, writes parity specs.
2. **backend**: implements the API, persistence, and STOMP side for that feature.
3. **frontend**: implements the Angular screens on top of the generated client and realtime service.
4. **transformer**: checks parity against legacy and ticks the feature off in the plan.

Hand-offs between agents happen through files in `docs/` (plan, protocol, glossary), never through
assumptions. Each agent preloads its best-practice skills from `.claude/skills/`.

## Rules

- Never edit anything under `legacy/`.
- Never put secrets in committed files. Use env vars (`SPRING_DATASOURCE_*`) and `application-local.yml` (gitignored).
- Never change a Flyway migration that has already been applied. Add a new one.
- Before writing code that uses a domain term, add or check its entry in `docs/GLOSSARY.md`.
- Use English for identifiers and commit messages.
