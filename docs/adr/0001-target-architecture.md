# ADR 0001: Target architecture for the card game migration

- Status: Accepted
- Date: 2026-11-15
- Source: decisions recorded in `CLAUDE.md`

## Context

The game is a Jakarta EE app (servlets, JSPs, one raw WebSocket endpoint, EclipseLink + MySQL).
All game rules live in static mutable fields of `UserStatusWebSocket`, scoring and the round timer run
in the browser, authentication trusts a `?username=` query parameter on the socket, and passwords are
stored in plain text. One server can only run one game at a time, and state is lost on restart.
See the "Legacy inventory" in `docs/migration-plan.md`.

## Decision

1. **Parallel rewrite.** The legacy app moves to `legacy/` (via `git mv`) and stays untouched as the
   behavioural reference. It is deleted once every feature is `verified` and the user agrees.
2. **Backend**: Spring Boot 4.x, Java 25, Maven, base package `com.cardgame`, package-by-feature
   (`auth/`, `player/`, `invitation/`, `game/`). Pure game rules live in `com.cardgame.game.engine`
   with injected `RandomGenerator` and `Clock`, free of Spring and transport code.
3. **Database**: PostgreSQL. Flyway owns the schema, Hibernate runs with `ddl-auto=validate`.
   Fresh start: legacy MySQL data is not migrated. Dev seed data comes from a Flyway migration.
4. **Auth**: Spring Security with a server session (HttpOnly cookie) and CSRF protection. Passwords
   are hashed with bcrypt. The Angular dev server proxies `/api` and `/ws`, so everything is same-origin.
5. **Real-time**: Spring WebSocket + STOMP on `/ws`. Game-wide events on `/topic/game/{id}`,
   per-user events on `/user/queue/...` (e.g. `/user/queue/invitations`). The user is always the
   authenticated principal. Game state lives in a Spring service keyed by game id, never in static
   fields. The server is authoritative for scores and runs the round timer (`TaskScheduler`).
6. **API contract**: springdoc OpenAPI, from which the Angular client is generated. STOMP messages are
   documented by hand in `docs/realtime-protocol.md`.
7. **Frontend**: Angular (latest) with standalone components and signals, Tailwind 4, PrimeNG (Aura preset).
8. **Naming**: English in all new code. `docs/GLOSSARY.md` maps English to the French legacy names.
9. **Testing**: backend JUnit 5 + Spring slices + Testcontainers PostgreSQL (never H2); frontend Vitest +
   Testing Library + Playwright. Parity tests in `backend/src/test/java/com/cardgame/parity/` are written
   before a legacy rule is ported, and define "done" for it.
10. **Secrets**: never committed. `SPRING_DATASOURCE_*` env vars or a gitignored `application-local.yml`.

## Consequences

- Behaviour differences from legacy (security fixes, server-authoritative scoring, bug fixes) are
  intentional only when listed and approved in `docs/migration-plan.md`.
- Several games can run at once, and a refresh or reconnect can recover game state from the server.
- The legacy DB password is already in git history (`legacy/src/main/resources/META-INF/persistence.xml`);
  it must be considered compromised and never reused.
- Two codebases coexist until decommission; the `legacy/` tree must not be edited.
