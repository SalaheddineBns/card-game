# Generated API client

This folder is **generated** from the backend OpenAPI spec. Never edit files here by hand.
The generated code **is committed**, so the frontend builds without Java or a running backend.
Regenerate and commit whenever the backend API changes.

```bash
# from the spec file written by the backend build (cd backend && ./mvnw verify)
npm run api:generate          # reads ../backend/target/openapi.json

# or from a running backend (springdoc at http://localhost:8080/v3/api-docs)
npm run api:generate:live
```

- Generator: `typescript-angular` via `@openapitools/openapi-generator-cli` (version pinned in
  `frontend/openapitools.json`, needs a Java runtime).
- `.openapi-generator-ignore` keeps this README and stops `git_push.sh` / `.gitignore` being generated.
  The generator never deletes files: if an endpoint or model is removed, delete its file by hand.
- Output is excluded from ESLint and Prettier.
- `app.config.ts` calls `provideApi('')` (BASE_PATH `''`), so calls stay relative (`/api/...`), go
  through the dev proxy and receive Angular's `X-XSRF-TOKEN` header (Angular never adds it to
  absolute URLs).
- Components never call generated services directly: stores/feature services wrap them
  (e.g. `core/auth/auth-store.ts` wraps `AuthService`).
