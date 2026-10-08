# 12 — Deployment and Local Environment

## 1. Container architecture

```mermaid
flowchart LR
  B[Browser] -- ":3000" --> F["frontend<br/>nginx 1.29<br/>static SPA + /api proxy"]
  F -- "/api → backend:8080" --> BE["backend<br/>eclipse-temurin 21 JRE<br/>Spring Boot jar"]
  BE -- "JDBC 5432" --> P[("postgres 17-alpine<br/>volume pgdata")]
  BE -. "HTTPS (optional)" .-> AI[Claude API]
  L["localstack:4<br/>profile: localstack"]:::opt
  classDef opt stroke-dasharray: 5 5
```

| Service | Image / build | Port | Notes |
|---------|---------------|------|-------|
| `postgres` | `postgres:17-alpine` | 5432 | health check `pg_isready`; data in volume `pgdata` |
| `backend` | `backend/Dockerfile` (multi-stage: JDK build → JRE runtime, non-root user) | 8080 | starts after the DB is healthy; Flyway migrates on start |
| `frontend` | `frontend/Dockerfile` (Node build → nginx) | 3000 | serves the SPA, proxies `/api` (same origin → no CORS needed) |
| `localstack` | `localstack/localstack:4` | 4566 | only with `--profile localstack`; not used by the core platform (ADR-7) |

### Decisions
- **Multi-stage Dockerfiles:** the runtime images contain no build tools or source code → smaller and safer.
- **nginx reverse proxy:** browser and API share one origin, so the JWT never has to cross origins, and the backend
  port need not be public in production.
- **Secrets only from `.env`:** `docker-compose.yml` uses `${VAR:?message}` for mandatory secrets
  (`POSTGRES_PASSWORD`, `JWT_SECRET`) — the stack refuses to start without them instead of using a default.

## 2. Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `POSTGRES_DB`, `POSTGRES_USER` | `cybersim` | database name / user |
| `POSTGRES_PASSWORD` | — (required) | database password |
| `JWT_SECRET` | — (required in Docker; random in local dev) | HS256 signing key, ≥ 32 characters |
| `JWT_EXPIRATION_MINUTES` | 60 | token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` (Compose) / `http://localhost:5173` (app) | for running the SPA dev server against the backend |
| `APP_DEMO_DATA_ENABLED` | `true` (Compose) / `false` (app) | create the demo admin account |
| `DEMO_ADMIN_EMAIL` / `DEMO_ADMIN_PASSWORD` | e-mail preset, password empty | the single admin account; created only if the password is set (there is no student account and no registration) |
| `AI_PROVIDER` | `mock` | `mock` or `claude` |
| `ANTHROPIC_API_KEY` | empty | Claude API key (never committed, never logged) |
| `AI_MODEL` | `claude-opus-5-5` | e.g. `claude-sonnet-5-5`, `claude-haiku-4-5` for lower cost |
| `AI_EFFORT` | `low` | `low`/`medium`/`high`/`xhigh`/`max` |
| `AI_TIMEOUT_SECONDS` | 30 | per-attempt timeout |
| `AI_MAX_TOKENS` | 2048 | maximum output tokens per AI response |
| `AI_SERVER_SIDE_FALLBACK` | `true` | ask the API to retry refused requests on a fallback model |
| `APP_SCENARIO_SEED_ENABLED` | `true` | import and publish `resources/scenarios/*.json` on start-up |
| `LEARNER_API_KEY` | empty | shared service key the learner module sends as `X-API-Key` on `/api/learner/**`; empty disables the integration (503) |
| `POSTGRES_PORT`, `BACKEND_PORT`, `FRONTEND_PORT` | 5432 / 8080 / 3000 | published host ports |

## 3. Commands

| Purpose | Command |
|---------|---------|
| First start | `cp .env.example .env` → edit secrets → `docker compose up --build` |
| Production-like start (detached) | `docker compose up --build -d` |
| Development: DB only | `docker compose up -d postgres`, then run backend (`./mvnw spring-boot:run`) and frontend (`npm run dev`) locally |
| Reset database | `docker compose down -v && docker compose up -d` (Flyway recreates the schema, the seeder re-imports and publishes the scenarios) |
| Tests | `cd backend && ./mvnw test` (Docker must be running) |
| With LocalStack | `docker compose --profile localstack up -d` |
| Logs | `docker compose logs -f backend` |
| Stop | `docker compose down` |

> **Schema policy during pre-release:** the project keeps a single migration, `V1__initial_schema.sql`, and it
> was **rewritten** for the admin-only scope. Flyway validates checksums, so any volume created before the
> rewrite fails on start-up (checksum mismatch and an applied-but-missing `V2`). Recreate it:
> `docker compose down -v && docker compose up --build`. Once the platform has real data, schema changes
> switch to additive `V2, V3, …` migrations.

## 4. Verification status

- 2026-10-06/07 — the original student platform was verified end-to-end through `docker compose`
  (historical; that runtime has since moved to the learner module).
- 2026-10-09 — `docker compose down -v && docker compose up --build -d` with the admin-only build:
  all three containers start, the rewritten `V1` migrates, the three seed scenarios are imported **and
  published as version 1**, the demo admin is created, the SPA serves the admin UI — **VERIFIED** (this
  session).
- LocalStack profile — **implemented but not runtime-verified** (optional, not used by application code).
- Real Claude provider inside Docker — **implemented but not runtime-verified** (no API key available).

## 5. Production notes (beyond the scope of the thesis demo)
- Terminate TLS in front of nginx (e.g. a cloud load balancer); set `CORS_ALLOWED_ORIGINS` to the real domain.
- Use a managed PostgreSQL with backups; keep `JWT_SECRET` and `ANTHROPIC_API_KEY` in a secret manager.
- Do not publish ports 8080 and 5432; only the frontend needs to be reachable.
- Provision real admin accounts instead of the demo account (set strong `DEMO_ADMIN_*` values or create rows
  directly); there is deliberately no self-registration.
