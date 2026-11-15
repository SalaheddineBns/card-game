---
name: testing-strategy
description: Testing rules for the card game across backend (JUnit 5, Mockito, Spring test slices, Testcontainers PostgreSQL), frontend (Vitest, Angular Testing Library, Playwright) and migration parity tests. Use when writing, reviewing or fixing tests anywhere in the project.
---

# Testing strategy (card game)

Test **behaviour through public interfaces**, not implementation details. A test should only break
when behaviour changes.

## Backend (`backend/`)

| Layer | Tool | Notes |
|-------|------|-------|
| Pure rules (`GameEngine`, scoring, card generation) | Plain JUnit 5 | No Spring context. Fast. Most tests live here. Inject `Random`/`Clock` so results are predictable |
| Services | JUnit 5 + Mockito | Mock only what crosses a boundary (repositories, messaging), not other plain classes |
| Controllers | `@WebMvcTest` + `MockMvc` / `MockMvcTester` | Check status, JSON shape, validation errors (ProblemDetail), security (`@WithMockUser`, CSRF with `.with(csrf())`) |
| Repositories | `@DataJpaTest` + Testcontainers | Custom queries only. Don't test Spring Data's derived methods |
| Full flows | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers | Register → login → create game → STOMP messages. Use a STOMP client (`WebSocketStompClient`) for real-time |

- **PostgreSQL through Testcontainers only. Never H2.** It has to be the same database that Flyway targets.
- Share one container through a `@TestConfiguration` with `@ServiceConnection`:
  ```java
  @TestConfiguration(proxyBeanMethods = false)
  class TestcontainersConfig {
      @Bean @ServiceConnection
      PostgreSQLContainer<?> postgres() { return new PostgreSQLContainer<>("postgres:17-alpine"); }
  }
  ```
- Test names describe behaviour: `rejectsInvitationWhenPlayerAlreadyInGame()`. Structure each test as
  given / when / then.
- Use AssertJ for assertions. Use Awaitility for async/STOMP assertions. Never `Thread.sleep`.
- Control time with an injected `Clock` and randomness with an injected `Random`/seed.
- `./mvnw verify` runs everything and must pass before you report.

## Frontend (`frontend/`)

| Layer | Tool | Notes |
|-------|------|-------|
| Components | Vitest + `@testing-library/angular` | Render, interact through roles and labels (`getByRole('button', { name: /accept/i })`), check what the user sees |
| Services with signals | Vitest | Use `TestBed.inject`. Mock HTTP with `provideHttpClientTesting()` |
| Realtime | Vitest | Mock `RealtimeService` with a `Subject`. Never connect to a real broker in unit tests |
| End-to-end | Playwright | A few critical flows: register → login, invite → accept → play a round → winner page. Run against `ng serve` + backend + Postgres (compose) |

- Query by role, label or text. Use `data-testid` only as a last resort. Never use CSS class selectors.
- Don't snapshot-test PrimeNG markup.
- `npm test` and `npm run build` must pass. Playwright runs with `npm run e2e`.

## Parity tests (migration)

The transformer writes these **before** a legacy rule is ported. They define "done" for the backend.

- Location: `backend/src/test/java/com/cardgame/parity/`, one class per rule area
  (`CardGenerationParityTest`, `CombinationParityTest`, `RoundTimerParityTest`, `InvitationFlowParityTest`).
- Each test cites its legacy source in a comment: `// legacy: UserStatusWebSocket#onMessage, lines 120-160`.
- Write them as **input → expected output tables** (`@ParameterizedTest` + `@CsvSource`/`@MethodSource`),
  with values found by reading the legacy code.
- Tests start as `@Disabled("pending port: <feature>")`. The backend agent removes `@Disabled` when it
  implements the rule.
- Mark suspected legacy bugs with `// LEGACY-BUG?` and an explanation. The user decides whether to keep
  or fix the behaviour, and the decision is recorded in `docs/migration-plan.md`.

## General

- Every bug fix starts with a failing test that reproduces it.
- No test depends on another test's order or leftover data. Each test sets up its own data.
- Never commit tests that are skipped without a reason. Every `@Disabled` gives one.
