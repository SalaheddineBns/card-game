# Speedest Game

A real-time multiplayer card game: invite other players from the lobby, then be the first to spot whether
the cards on the table share the same colour, the same shape, or both.

## Try it online

**▶ https://card-game-production-d9beee7e.up.railway.app**

Demo accounts (password **`demo1234`**):

| Username | Password |
|---|---|
| `alice` | `demo1234` |
| `bob` | `demo1234` |
| `carol` | `demo1234` |

To play a game alone, open two different browsers (or one normal and one private window), log in as
`alice` in one and `bob` in the other, then invite `bob` from alice's lobby.

> ⚠️ **The demo is reset every morning at 04:00 (Paris time).** All accounts, games and statistics are
> deleted, and the three demo accounts above are recreated. Accounts you create yourself only last until
> the next reset.

## Tech stack

| Part | Stack |
|---|---|
| Frontend | Angular 22, Tailwind CSS 4, PrimeNG 22, STOMP over WebSocket |
| Backend | Spring Boot 4, Java 25, Spring Security (session + CSRF), Spring WebSocket (STOMP) |
| Database | PostgreSQL 17, Flyway migrations |
| Tests | JUnit 5, Testcontainers, Vitest, Testing Library, Playwright |
| Deployment | Docker (one image), Railway, GitHub Actions CI |

## Run it locally

Requirements: Docker, Java 25 (`sdk env` in `backend/` with SDKMAN), Node 24.

```bash
cp .env.example .env                  # set POSTGRES_PASSWORD
docker compose up -d                  # PostgreSQL

cd backend && ./mvnw spring-boot:run  # API on http://localhost:8080 (local profile)
cd frontend && npm install && npx ng serve --port 4300   # app on http://localhost:4300
```

The local profile creates the same `alice` / `bob` / `carol` accounts, with the password `password123`.

## Tests

```bash
cd backend && ./mvnw verify           # unit, integration (Testcontainers) and parity tests
cd frontend && npm test               # unit tests
cd frontend && npx playwright test    # end-to-end (needs the backend running)
```

## Documentation

- [`docs/deploy.md`](docs/deploy.md): deployment on Railway, daily demo reset
- [`docs/migration-plan.md`](docs/migration-plan.md): migration from the original servlet/JSP app, all decisions
- [`docs/realtime-protocol.md`](docs/realtime-protocol.md): WebSocket (STOMP) messages
- [`docs/GLOSSARY.md`](docs/GLOSSARY.md): domain vocabulary
