---
name: angular-best-practices
description: Rules for writing modern Angular code in the card game frontend. Covers standalone components, signals, OnPush, the new control flow, lazy feature routes, inject(), the generated OpenAPI client, the STOMP realtime service, forms, and interceptors. Use when writing or reviewing anything under frontend/.
---

# Angular best practices (card game)

## Project setup

- Scaffold: `npx @angular/cli@latest new frontend --style=css --ssr=false --routing`
  (Tailwind 4 is set up through PostCSS. See the `primeng-tailwind` skill).
- Strict TypeScript and strict templates stay on. Never use `any`. Use `unknown` and narrow it.
- `proxy.conf.json` forwards `/api` and `/ws` (with `ws: true`) to `http://localhost:8080`, so the
  session cookie and CSRF work same-origin.
- Lint with `@angular-eslint`. Format with Prettier.

## Components

- Standalone only (the default). No NgModules.
- `changeDetection: ChangeDetectionStrategy.OnPush` on every component.
- Use `input()`, `input.required()`, `output()` and `model()`, not the `@Input`/`@Output` decorators.
- Use `inject()` instead of constructor parameters.
- Templates use `@if`, `@for (x of xs; track x.id)` and `@switch`. Never `*ngIf`/`*ngFor`.
  Use `@defer` for heavy, off-screen parts.
- **Smart vs presentational components**:
  - Route-level "page" components inject services and hold state.
  - Presentational components in `shared/` or the feature's `ui/` folder only use inputs and outputs.
- Keep templates free of logic. Derive values with `computed()`, not method calls in the template.
- Name files after the Angular style guide for the installed version (e.g. `game-table.ts` + `game-table.html`).
  Keep it consistent with what the CLI generates.

## State: signals first

- Local and feature state lives in `signal()` / `computed()` inside a feature-scoped service
  (`@Injectable()` provided in the route's `providers`) or a root service for global state (current user).
- Expose state read-only: `private readonly _game = signal<GameState | null>(null); readonly game = this._game.asReadonly();`
- Use RxJS for **streams** (WebSocket messages, debounced input). Convert at the edge with
  `toSignal()` / `toObservable()`. Use `rxResource`/`httpResource`/`resource` for async loading where
  it fits.
- Never subscribe by hand without cleanup. Use `takeUntilDestroyed()`, or better, `toSignal`.
- No NgRx unless an ADR justifies it.

## Routing

```ts
// app.routes.ts
export const routes: Routes = [
  { path: 'auth', loadChildren: () => import('./features/auth/auth.routes') },
  { path: '', canMatch: [authGuard], children: [
      { path: '', loadChildren: () => import('./features/home/home.routes') },
      { path: 'game/:id', loadChildren: () => import('./features/game/game.routes') },
      { path: 'profile', loadChildren: () => import('./features/profile/profile.routes') },
  ]},
  { path: '**', redirectTo: '' },
];
```

- Use functional guards and resolvers only. Turn on `withComponentInputBinding()` so route params
  arrive as `input()`.

## HTTP and the API client

- Generate the client from the backend:
  `npx @openapitools/openapi-generator-cli generate -i http://localhost:8080/v3/api-docs -g typescript-angular -o src/app/core/api`
  and add it as an npm script `api:generate`. **Never edit `core/api/` by hand.**
- `provideHttpClient(withFetch(), withInterceptors([csrfInterceptor, errorInterceptor]))`.
  Angular's built-in XSRF support reads the `XSRF-TOKEN` cookie. Make sure it's on
  (`withXsrfConfiguration` if the names differ).
- `errorInterceptor`: on 401, clear the user and redirect to `/auth/login`. Turn `ProblemDetail`
  responses into PrimeNG toasts.
- Wrap generated services in feature services when you need to add state. Components call the feature
  service, not the generated one directly.

## Real-time (STOMP)

- One `RealtimeService` in `core/realtime/` (provided in root) owns the single `RxStomp` instance.
  - It connects after login and disconnects on logout.
  - It sends the CSRF token in the CONNECT headers.
  - It exposes typed methods: `watchGame(id): Observable<GameEvent>`, `invitations(): Observable<Invitation>`,
    `send(dest, body)`.
- Message types are hand-written interfaces in `core/realtime/protocol.ts` that mirror
  `docs/realtime-protocol.md` exactly. Use discriminated unions on `type`.
- Reconnect is handled by rx-stomp (`reconnectDelay`). Show the connection state in the UI.

## Forms

- Typed reactive forms (`FormBuilder.nonNullable`). Show validation messages with PrimeNG `p-message`
  or `<small>` under each field.
- Disable submit while a request is pending. Show server `ProblemDetail` field errors on the matching controls.

## Performance and accessibility

- Use `track` with a stable id in every `@for`. Use `NgOptimizedImage` for images.
- Every interactive element is reachable by keyboard and has an accessible name. Card visuals must not
  rely on colour alone (show the shape or letter too).
