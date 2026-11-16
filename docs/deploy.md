# Deploying to Railway

One Docker image serves both the Angular app and the Spring Boot API from the same origin, so the session
cookie, CSRF and the WebSocket all work without CORS. It runs as **one always-on instance**, because live games,
invitations and presence are held in memory. It uses a Railway-hosted PostgreSQL database and the generated
`*.up.railway.app` domain.

```
GitHub (main) ──push──▶ GitHub Actions CI (backend, frontend, docker build)
                              │ all green
                              ▼
                     Railway "Wait for CI" ──▶ builds ./Dockerfile ──▶ 1 replica ──▶ https://<app>.up.railway.app
                                                                        │
                                                                        └── Railway PostgreSQL (private network)
```

| File | Role |
|---|---|
| `Dockerfile` | node 24 builds the Angular app → JDK 25 packages the jar with the app in `static/` → runs on JRE 25 as a non-root user, `SPRING_PROFILES_ACTIVE=prod`, listens on `$PORT` |
| `backend/src/main/resources/application-prod.yml` | Proxy-aware HTTPS (`forward-headers-strategy=framework`), Secure cookies, Swagger UI and `/v3/api-docs` **disabled**, Flyway on and no dev seed, Hikari pool of 5, graceful shutdown, daily demo reset on (D29) |
| `.github/workflows/ci.yml` | `./mvnw verify`, frontend lint, test and build, plus a Docker build without push. Railway waits for it |
| `compose.prod-local.yaml` | Runs the production image locally with its own PostgreSQL |

> **No `railway.toml`.** Railway has deprecated *Config as Code*. New services can't use it, and existing files stop
> being read on 2026-12-01 ([docs](https://docs.railway.com/reference/config-as-code)). Its replacement,
> *Infrastructure as Code* (`.railway/railway.ts`, applied with the Railway CLI), doesn't yet document every setting
> used here, such as the restart policy. The few settings below are therefore made once in the dashboard. Railway
> finds the root `Dockerfile` on its own: "Railway always builds with a Dockerfile if one exists".

## One-time setup in the Railway dashboard

Before you start, commit and push `.github/workflows/ci.yml` to `main` on GitHub, and make sure the first CI run is
green (Actions tab).

1. **Create the project.** Go to <https://railway.com/new>, choose **Deploy from GitHub repo**, and pick this
   repository. Authorise the Railway GitHub app for the repo if you're asked. Railway creates a service for the app
   and starts a first build. That build may fail because there's no database yet, which is expected.
2. **Add PostgreSQL.** On the project canvas, click **+ Create**, then **Database**, then **PostgreSQL**. The new
   service is normally named **`Postgres`**. If it shows another name, use that name instead of `Postgres` in
   step 3.
3. **Set the app's variables.** Open the app service, then the **Variables** tab, and choose **Raw Editor**. Paste:

   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
   SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
   ```

   - Railway's PostgreSQL guide documents the variable names `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`,
     `PGDATABASE` and `DATABASE_URL`, and the variables guide documents the `${{SERVICE.VAR}}` syntax. The service
     name `Postgres` is Railway's usual default, but I couldn't confirm it in the docs, so check the canvas.
   - Don't use `DATABASE_URL` directly. It is a `postgresql://user:pass@host/db` URL, and JDBC needs the
     `jdbc:postgresql://` form above.
   - Don't set `PORT` (Railway injects it) or `SPRING_PROFILES_ACTIVE` (the image already sets `prod`).
4. **Service settings.** Open the app service, then **Settings**:

   | Section | Setting | Value | Why |
   |---|---|---|---|
   | Source | Branch | `main` | |
   | Source | **Wait for CI** | **On** | Deploy only after every GitHub Actions workflow on the commit has succeeded. A failed workflow skips the deploy, and the previous version keeps running |
   | Deploy | Healthcheck Path | `/actuator/health` | Returns 200 only when the app **and** the database are up |
   | Deploy | Healthcheck Timeout | `300` seconds (the default) | Enough for the first boot, which runs Flyway on an empty database. The app normally starts in about 5 s |
   | Deploy | Restart Policy | **On Failure**, max retries `5` | |
   | Deploy | Replicas | **1** | Live game state is in memory. **Never scale above 1** |
   | Deploy | Serverless / App Sleeping | **Off** | A sleeping app drops WebSockets and running games |
   | Resources | Memory | 512 MB to 1 GB is plenty | The JVM uses 75 % of it (`-XX:MaxRAMPercentage=75`) |

5. **Public URL.** Go to **Settings**, then **Networking**, then **Generate Domain**. If Railway asks for a port,
   use the port printed in the deploy logs (`Tomcat started on port …`). That's the `PORT` Railway injected, so
   accept the detected value. You get `https://<something>.up.railway.app`.
6. **First deploy.** On the **Deployments** tab, click **Redeploy** on the latest deployment, or push any commit to
   `main` (it then waits for CI). In the **Deploy Logs** you should see:
   - `The following 1 profile is active: "prod"`
   - `Successfully applied 3 migrations to schema "public", now at version v3` (first boot only)
   - `Marked 0 game(s) left in progress before … as ABANDONED`
   - `Started CardGameApplication in … seconds`

   The deployment turns **Active** once the health check passes.
7. **Check it:**
   - `https://<domain>/actuator/health` returns `{"groups":["liveness","readiness"],"status":"UP"}`.
   - `https://<domain>/` shows the landing page, and `https://<domain>/lobby` redirects to the login.
   - `https://<domain>/api/auth/me` returns 401 with a French ProblemDetail.
8. **Log in.** On the first start the app creates the demo players `alice`, `bob` and `carol`, with the password
   `demo1234` (or `CARDGAME_DEMO_RESET_DEMO_PASSWORD`). See "Daily demo reset" below. You can also register your own
   account with **Créer un compte**. Use a second browser or a private window with a second account to try a game.

## Day to day

- **Deploy:** merge or push to `main`. CI runs, and Railway builds and deploys the image once CI is green.
- **Logs:** open the app service, then **Deployments**, choose a deployment, and select **View Logs** (Build and
  Deploy logs). The **Observability** tab has metrics. Errors are logged at `ERROR`, and each server start logs
  how many interrupted games it marked abandoned.
- **Health:** `GET /actuator/health`. Railway only uses the health check while a deploy goes live, not afterwards,
  so a later database outage doesn't restart the app; the restart policy covers crashes.
- **Roll back:** on the **Deployments** tab, open a previous successful deployment's **⋮** menu and choose
  **Rollback** (or **Redeploy**). Database migrations are forward-only, so rolling back across a migration needs care.
  V1 to V3 only add things, so the previous image still works against a newer schema as long as no columns were
  dropped. Never edit a migration that has already been applied; add a new one.
- **Every deploy or restart interrupts running games.** This is by design (D25): live state lives in memory. Games
  that were running are marked `ABANDONED` at the next start, their players get "Partie interrompue" (410), and
  pending invitations are lost. Players reconnect automatically. Deploy when nobody is playing.
- **Database access:** open the Postgres service, then **Data** (table browser), or **Connect** for a `psql`
  command.

## Daily demo reset (D29)

The public demo is reset every day at **04:00 Europe/Paris**. The reset:

1. closes every WebSocket and invalidates every HTTP session, so everybody is logged out;
2. drops the running games (their timers are cancelled), the pending invitations and the online list;
3. in one transaction, deletes **all** participations, games and players (ids restart at 1), then recreates the
   demo players `alice`, `bob` and `carol`, with zero stats and the demo password;
4. logs one line: `Demo reset: closed … STOMP connection(s) and … HTTP session(s); … recreated demo players [...]`.

Any account people registered during the day is deleted. On a fresh deploy with an empty database, the demo players
are also created at startup (`Demo seed: created demo players …`), so they exist before the first 04:00 run.

The reset runs on its own thread and doesn't touch the game timers. It is only active with the `prod` profile;
development and tests have it off. Change it with service variables in Railway (then redeploy):

| Variable | Default (prod) | Effect |
|---|---|---|
| `CARDGAME_DEMO_RESET_ENABLED` | `true` | `false` turns off both the daily reset and the startup seed (for a real, non-demo deployment) |
| `CARDGAME_DEMO_RESET_CRON` | `0 0 4 * * *` | Spring cron with seconds first, e.g. `0 30 3 * * *` for 03:30, or `0 0 4 * * MON` for Mondays only |
| `CARDGAME_DEMO_RESET_ZONE` | `Europe/Paris` | Time zone of the cron |
| `CARDGAME_DEMO_RESET_DEMO_PASSWORD` | `demo1234` | Password of the demo players (8-72 characters). Applies to players created from then on, so at the next reset or seed |

An invalid cron, zone or password stops the app at startup (the health check fails) instead of failing at 04:00.

## Running the production image locally

```bash
cp .env.example .env            # once; set POSTGRES_PASSWORD
docker compose -f compose.prod-local.yaml up --build -d
open http://localhost:8081      # health: http://localhost:8081/actuator/health
docker compose -f compose.prod-local.yaml down      # add -v to delete its database
```

- It uses its own compose project (`card-game-prod-local`), with its own PostgreSQL and volume. The development
  database from `compose.yaml` isn't touched, and no database port is published.
- It runs the `prod` profile, with one local-only override, `SERVER_SERVLET_SESSION_COOKIE_SECURE=false`, because
  plain `http://localhost` can't keep Secure cookies. On Railway everything is HTTPS and cookies stay Secure.
- To build the image only: `docker build -t card-game .`

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| Health check never passes, logs show `Connection refused` / `UnknownHostException` | The datasource variables are wrong, or the Postgres service has a different name than in `${{Postgres.…}}` (step 3) |
| Login works but you're immediately logged out | The site was opened over `http://`. Use the `https://` Railway domain (cookies are Secure) |
| The WebSocket gets `403` on `/ws` | The proxy didn't forward the original host. As a fallback, set `CARDGAME_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS=https://<your-domain>.up.railway.app` (comma-separated list) |
| Deploys stay in **WAITING** | CI is still running or failed (Actions tab), or the Railway GitHub app has a pending permission update. Approve it in GitHub, then reconnect the repo in Settings, Source |
| `OutOfMemoryError` / restarts | Raise the service memory. The JVM uses 75 % of the container limit |

## Sources

- [Config as Code (deprecated)](https://docs.railway.com/reference/config-as-code)
- [Infrastructure as Code](https://docs.railway.com/infrastructure-as-code)
- [PostgreSQL on Railway](https://docs.railway.com/guides/postgresql)
- [Variables and reference variables](https://docs.railway.com/guides/variables)
- [Health checks](https://docs.railway.com/reference/healthchecks)
- [GitHub autodeploys and "Wait for CI"](https://docs.railway.com/guides/github-autodeploys)
