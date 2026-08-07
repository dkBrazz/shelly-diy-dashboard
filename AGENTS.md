# Repository guide for coding agents

## Scope and stack

These instructions apply to the entire repository. The application is a Kotlin 2.2 / Spring Boot 4 backend on Java 24 and a React 19 / TypeScript frontend built with Vite 7. PostgreSQL with TimescaleDB stores measurements.

## Repository map

- `src/main/kotlin/org/dkbrazz/controller`: authenticated REST endpoints.
- `src/main/kotlin/org/dkbrazz/service`: Shelly ingestion, authorization, and query logic.
- `src/main/kotlin/org/dkbrazz/websocket`: long-lived Shelly Cloud connection.
- `src/main/kotlin/org/dkbrazz/model` and `repository`: persistence entities, DTOs, scaling, and Timescale queries.
- `src/main/resources/schema.sql`: active startup schema; treat schema changes as production data changes.
- `src/main/resources/schema.old.sql`: legacy reference only; never use it as the active schema.
- `frontend/src`: React dashboard. Vite writes production assets to `src/main/resources/static`, which is generated and ignored.

## Standard commands

Run commands from the repository root unless noted otherwise.

- Full clean validation: `./gradlew check --no-daemon`
- Backend-only tests: `./gradlew test -PskipFrontend --no-daemon`
- Frontend clean install: `cd frontend && npm ci`
- Frontend validation: `cd frontend && npm run check`
- Backend development server: `./gradlew bootRun -PskipFrontend`
- Frontend development server: `cd frontend && npm run dev`
- Production image: `docker build -t shelly-diy-dashboard .`

The Gradle Java toolchain resolver can provision Java 24. Node 22 or newer is expected. Always use the Gradle wrapper and `npm ci`; update and commit `frontend/package-lock.json` whenever dependencies change.

## Development workflow

1. Inspect the relevant controller/service/repository path and its frontend consumer before editing shared API shapes.
2. Make the smallest coherent change. Keep Kotlin DTOs and `frontend/src/types.ts` aligned.
3. Add or update focused tests. Backend tests live under the matching package in `src/test/kotlin`.
4. Run a scoped check while iterating, then `./gradlew check --no-daemon` before handoff.
5. Report checks that could not run and any remaining warning; do not claim unrun validation passed.

## Domain invariants

- Measurement columns are fixed-point integers scaled by 1,000. Use `scaleMeasure`, `requireScaledMeasure`, and `unscaleMeasure`; do not hand-roll conversions.
- Persist measurement timestamps as UTC-aware values. The UI intentionally renders them in `Europe/Kyiv` through `frontend/src/config.ts`.
- History aggregation uses the TimescaleDB `time_bucket` function. Repository query changes need a Timescale-backed integration check or an explicit note that only unit tests ran.
- `/api/**` is authenticated. Google OAuth users must also exist in the `users` table; preserving that allowlist is a security requirement.
- Shelly Cloud work happens through a scheduled WebSocket client and asynchronous event processing. Preserve reconnect state safety and avoid blocking the scheduler thread.
- `schema.sql` runs on every startup. Prefer additive, idempotent SQL and explain migration implications for existing installations.

## Safety and repository hygiene

- Never commit credentials, tokens, database dumps, `.env` files, or generated frontend assets.
- Use `.env.example` only as a key inventory; real values belong in the environment or a secret manager.
- Do not weaken authentication, CSRF choices, or the user allowlist as a testing shortcut.
- Preserve unrelated working-tree changes and avoid destructive Git commands.
