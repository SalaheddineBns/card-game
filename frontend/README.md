# Card game frontend

Angular 22 (standalone, signals, OnPush) · Tailwind CSS 4 · PrimeNG 22 (Aura preset via `@primeuix/themes`)
· `@stomp/rx-stomp`. UI texts are French (D21 in `docs/migration-plan.md`).

| Command                           | What it does                                                                                               |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `npm start`                       | `ng serve` on http://localhost:4200, proxies `/api` and `/ws` to http://localhost:8080 (`proxy.conf.json`) |
| `npm test`                        | Vitest unit/component tests, single run (`npm run test:watch` to watch)                                    |
| `npm run lint`                    | ESLint (`angular-eslint`, Prettier-compatible)                                                             |
| `npm run format` / `format:check` | Prettier                                                                                                   |
| `npm run build`                   | Production build into `dist/`                                                                              |
| `npm run api:generate`            | Regenerates `src/app/core/api/` from the backend's `/v3/api-docs` (backend must be running, needs Java)    |

Layout: `src/app/core` (api, auth, http, layout, notification, realtime, theme), `src/app/shared/ui`,
`src/app/features/*` (each lazy-loaded through its own `*.routes.ts`).
