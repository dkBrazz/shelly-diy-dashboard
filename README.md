# Shelly DIY Dashboard

A self-hosted dashboard that receives three-phase power measurements from Shelly Cloud, stores fixed-point samples in TimescaleDB, and serves an authenticated React dashboard from Spring Boot.

## Prerequisites

- Java 24 (the Gradle toolchain resolver can provision it)
- Node.js 22 or newer with npm
- PostgreSQL 18 with TimescaleDB 2.25 or newer
- Google OAuth client credentials
- A Shelly Cloud authorization code

## Configure

Copy `.env.example` to `.env`, replace every `replace-me` value, and export those variables before starting Spring Boot. `.env` is ignored by Git; Spring reads the exported environment, not the file directly.

`SHELLY_REGION` selects the Shelly Cloud endpoint used to exchange the authorization code for an access token. `SHELLY_WEBSOCKET_REGIONS` is a comma-separated list of regional endpoints monitored simultaneously for real-time events; it defaults to `shelly-241-eu,shelly-242-eu`.

The application creates the current schema from `src/main/resources/schema.sql`. Add each permitted Google account to the allowlist after the database is available:

```sql
INSERT INTO users (email)
VALUES ('you@example.com')
ON CONFLICT (email) DO NOTHING;
```

Do not use `schema.old.sql`; it describes the legacy floating-point layout.

### Local TimescaleDB

Start the PostgreSQL 18 / TimescaleDB 2.25.2 container:

```bash
docker compose up -d timescaledb
```

The Compose service reads `DB_NAME`, `DB_USER`, `DB_PASSWORD`, and `DB_PORT` from `.env` and otherwise uses the application's local defaults. Database files are retained in the `timescaledb-data` Docker volume. Check readiness with:

```bash
docker compose ps
```

Stop the database with `docker compose down`. To also delete all local database data, use `docker compose down --volumes`.

## Develop

Start the backend:

```bash
./gradlew bootRun -PskipFrontend
```

In another terminal, install and run the frontend:

```bash
cd frontend
npm ci
npm run dev
```

Vite serves the UI on port 5173 and proxies API and OAuth routes to Spring Boot on port 8080.

## Validate

The canonical full check installs dependencies from the lockfile, lints and type-checks the frontend, builds its production assets, and runs backend tests:

```bash
./gradlew check --no-daemon
```

For faster scoped feedback:

```bash
./gradlew test -PskipFrontend --no-daemon
cd frontend && npm run check
```

Generated frontend assets are written to `src/main/resources/static` and are intentionally not committed.

## Architecture notes

- Shelly Cloud events arrive over independently reconnecting WebSockets for every configured region and are persisted asynchronously.
- Measurements are stored as integer values multiplied by 1,000. API responses convert them back to display units.
- History endpoints aggregate samples with TimescaleDB `time_bucket`.
- Google OAuth authenticates users, and the `users` table acts as an explicit allowlist.

Agent-specific contribution guidance is in `AGENTS.md`.
