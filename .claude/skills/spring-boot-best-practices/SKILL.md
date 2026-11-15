---
name: spring-boot-best-practices
description: Rules for writing Spring Boot 4 / Java 25 code in the card game backend. Covers package-by-feature layout, records + MapStruct, transactions, validation, ProblemDetail errors, Spring Security session + CSRF, and STOMP WebSocket messaging. Use when writing or reviewing anything under backend/.
---

# Spring Boot best practices (card game)

## Layout: package by feature

```
com.cardgame
  CardGameApplication.java
  config/        SecurityConfig, WebSocketConfig, OpenApiConfig, JacksonConfig
  common/        GlobalExceptionHandler, base exceptions, shared value types
  auth/          AuthController, AuthService, dto/
  player/        Player (entity), PlayerRepository, PlayerService, PlayerController, dto/, PlayerMapper
  game/          Game, Round, Card, Participation, GameService, GameEngine (pure rules), ...
  invitation/    InvitationService, InvitationMessagingController, dto/
```

- Features only depend on another feature's **service or DTOs**, never on its repository or entity internals.
- Pure game rules (card generation, combinations, scoring, timer rules) go in plain Java classes with
  **no Spring dependency** (`GameEngine`, `ScoreCalculator`), so they can be unit-tested without a context.

## Code style

- **Constructor injection only**, `final` fields. No `@Autowired` on fields.
- **DTOs are Java records** with Bean Validation annotations: `record RegisterRequest(@NotBlank @Size(max=50) String username, @NotBlank @Size(min=8) String password) {}`.
- **Map with MapStruct** (`@Mapper(componentModel = "spring")`). Never return entities from controllers.
- **Entities**: no Lombok. Write getters/setters by hand, use a protected no-arg constructor, base
  `equals`/`hashCode` on the id (null-safe), and leave relations out of `toString`.
- Default every association to `FetchType.LAZY`. Use `@EntityGraph` or fetch-join queries to avoid N+1.
- Use `Long` ids with `GenerationType.IDENTITY` (matches Postgres `bigint generated always as identity`).
- Store enums with `@Enumerated(EnumType.STRING)`, never ORDINAL.
- Use `Instant`/`OffsetDateTime` for timestamps (`timestamptz`), never `java.util.Date`.
- `Optional` is for return types only, never fields or parameters.

## Transactions

- `@Transactional` goes on **service** methods, never controllers or repositories.
- Mark read paths `@Transactional(readOnly = true)`.
- Never call a remote or messaging side effect inside a transaction that might roll back. Publish with
  `@TransactionalEventListener(phase = AFTER_COMMIT)` instead.

## Errors

- One `@RestControllerAdvice` that returns RFC 7807 `ProblemDetail`.
- Domain exceptions (`PlayerNotFoundException`, `GameFullException`) map to 404 / 409 / 422.
  Validation errors map to 400 with field errors in `properties.errors`.
- Never leak stack traces or SQL messages to clients.
- Set `spring.mvc.problemdetails.enabled=true`.

## REST

- Prefix every endpoint with `/api`. Plural nouns: `/api/players/{id}`, `/api/games/{id}/rounds`.
- Status codes: 201 + `Location` on create, 204 on delete, 200 otherwise.
- Paginate collections with `Pageable` and return a page DTO, never an unbounded list.
- Document with springdoc. Add `@Operation`/`@Schema` only where the defaults aren't clear enough.

## Security (session + CSRF)

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .build();
}
```

- Hash passwords with `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt). Legacy
  passwords are not migrated.
- Log in through a JSON endpoint that authenticates with `AuthenticationManager` and saves the context
  with `HttpSessionSecurityContextRepository`.
- Session cookie: `HttpOnly`, `SameSite=Lax`, `Secure` outside local.
- Get the current user from `@AuthenticationPrincipal`. Never trust a username sent in the request.
  (Legacy took `?username=` on the WebSocket. Don't repeat that.)

## STOMP WebSocket

- Endpoint `/ws`, application prefix `/app`, broker prefixes `/topic` and `/queue`, user prefix `/user`.
- Secure the WebSocket with `@EnableWebSocketSecurity` and an `AuthorizationManager<Message<?>>`. The
  STOMP CONNECT frame must carry the CSRF header.
- Broadcast to a game: `/topic/game/{gameId}`. Private messages (invitations, your hand):
  `messagingTemplate.convertAndSendToUser(username, "/queue/...", payload)`.
- `@MessageMapping` handlers are thin: validate, delegate to the service, return nothing (the service publishes).
- Game state:
  - Store it in a `@Service` with a `ConcurrentHashMap<Long, GameState>` keyed by game id, or in the
    database when it has to survive a restart.
  - **Never** in static fields.
  - Make each game's state changes atomic: synchronise on that game's state object, or use `compute`.
- Timers go through a `TaskScheduler`, never `Thread.sleep` or hand-made threads.
  Cancel the `ScheduledFuture` when the game ends.
- Record every destination and payload in `docs/realtime-protocol.md`.

## Configuration

- `application.yml` holds safe defaults. Secrets come from env vars. The `local` profile goes in
  `application-local.yml` (gitignored).
- Use `@ConfigurationProperties` records for app settings (e.g. `cardgame.round.duration`), not scattered `@Value`.
- Turn on virtual threads with `spring.threads.virtual.enabled=true`.
- Turn on Actuator `health` and `info` only.
