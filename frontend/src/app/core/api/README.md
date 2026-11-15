# Generated API client

This folder is **generated** from the backend OpenAPI spec. Never edit files here by hand.

```bash
# backend running on http://localhost:8080 (springdoc at /v3/api-docs)
npm run api:generate
```

- Generator: `typescript-angular` via `@openapitools/openapi-generator-cli` (version pinned in
  `frontend/openapitools.json`, needs a Java runtime).
- Output: `src/app/core/api/` (excluded from ESLint and Prettier).
- After the first generation, wire it in `app.config.ts`: provide the generated `BASE_PATH` as `''`
  so calls stay relative (`/api/...`), go through the dev proxy and receive Angular's XSRF header
  (Angular never adds `X-XSRF-TOKEN` to absolute URLs).
- Components never call generated services directly: feature services wrap them.
