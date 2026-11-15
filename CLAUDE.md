# Card Game

A multiplayer card game built with **Spring Boot (backend) + Angular (frontend)**.
It was migrated from a Jakarta EE servlet/JSP app. The migration is complete: all 16 features in
`docs/migration-plan.md` are `verified`, and the old app was deleted.

## The original app (history only)

The servlet/JSP app was removed from the tree. Its last version is in commit `1a8d27b`, under
`legacy/`. Read it with `git show 1a8d27b:legacy/<path>`, or list it with `git ls-tree -r 1a8d27b legacy/`.
The parity tests and the plan cite line numbers from that commit.

## Architecture

```
backend/       ← Spring Boot 4.x, Java 25, Maven, PostgreSQL, Flyway
frontend/      ← Angular 22, Tailwind 4, PrimeNG 22 (Aura preset)
docs/          ← migration-plan.md, GLOSSARY.md, realtime-protocol.md, adr/
compose.yaml   ← local PostgreSQL (password from .env)
```

| Concern       | Decision |
|---------------|----------|
| Backend       | `com.cardgame`, package-by-feature (`auth/`, `player/`, `presence/`, `invitation/`, `game/`, `realtime/`) |
| Database      | PostgreSQL. Flyway owns the schema (`ddl-auto=validate`). Dev seed (alice/bob/carol) only in the `local` profile |
| Auth          | Spring Security, session cookie (HttpOnly) + CSRF (raw `XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header, also on STOMP CONNECT) |
| Real-time     | Spring WebSocket + STOMP at `/ws`. Live game state lives in Spring services, never in static fields |
| API contract  | springdoc OpenAPI → generated Angular client in `frontend/src/app/core/api` (committed, never hand-edited). STOMP messages are in `docs/realtime-protocol.md` |
| Naming        | English in code, French in the UI. `docs/GLOSSARY.md` maps them to the original French names |
| Testing       | Backend: JUnit 5 + slices + Testcontainers, plus parity tests in `com.cardgame.parity`. Frontend: Vitest + Testing Library + Playwright |
| Decisions     | D1–D26 in `docs/migration-plan.md`. ADRs in `docs/adr/` |

## Running locally

```bash
cp .env.example .env              # set POSTGRES_PASSWORD
docker compose up -d
cd backend && sdk env && ./mvnw spring-boot:run      # :8080, local profile, reads ../.env
cd frontend && npm install && npx ng serve --port 4300   # proxy → BACKEND_URL (default :8080)
```

## Agents

- **backend** and **frontend** build features in their own folders.
- **transformer** keeps `docs/` coherent: plan, decisions, glossary and protocol. It writes parity
  or contract tests before behaviour changes and verifies features end to end.

Subagents can't call each other, so the main session coordinates them. Hand-offs go through files in `docs/`.

## Rules

- Never put secrets in committed files. Use `.env` (gitignored) or env vars.
- Never change a Flyway migration that has already been applied. Add a new one.
- Don't change parity test expectations without a recorded user decision in `docs/migration-plan.md`.
- After changing the API, run `./mvnw verify` (it regenerates `backend/target/openapi.json`), then
  `npm run api:generate` in `frontend/`.
- Use English for identifiers and commit messages, French for UI text.
