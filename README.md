# CyberSim — Cloud Cyber Incident Simulation & Security Specialist Training Platform

University graduation project: *"Development of a Cloud Cyber Incident Simulation and Security Specialist
Training Platform Using Artificial Intelligence"*.

CyberSim is the **administrator's scenario-authoring platform**: an admin generates a draft incident scenario
(SSH brute force, compromised credentials, public storage bucket), edits it, inspects its **dependency graph**, runs
**graph-based validation**, lets the **test runner** play the correct and the dangerous path, receives a
**quality score** and publishes the scenario as a new immutable **version**. The trainee-facing simulation is
developed by another team member and consumes the published versions (see
[docs/en/17-admin-authoring-platform.md](docs/en/17-admin-authoring-platform.md) for the scope change). The
interface is available in English and Armenian (EN / ՀԱՅ switch in the app bar), and scenario text can be translated
to Armenian on request without storing anything. The learner module (the other team member's project) integrates over a service API: it reads published
scenario versions, submits completed student exams, and this platform **verifies every submission by replaying
it** with the deterministic scoring rules; administrators can run a per-exam **AI review** whose feedback flows
back to the learner module. Everything runs locally; nothing touches real external systems.

| | |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA / Hibernate 7, Flyway, PostgreSQL 17 |
| Frontend | React 19, TypeScript, Vite 8, MUI 9, React Router 8, Axios, react-i18next (English + Armenian UI) |
| AI | Claude API via the official Anthropic Java SDK (default model `claude-opus-5-5`) + offline mock provider; used to polish generated scenarios, create variations and translate |
| Infrastructure | Docker Compose (PostgreSQL, backend, nginx frontend, optional LocalStack) |
| Tests | JUnit 5, Mockito, Spring Boot Test, MockMvc, Testcontainers — 76 backend tests |

## Quick start (Docker)

Requirements: Docker Desktop.

```bash
cp .env.example .env          # then replace every "change-me" value (Windows: copy .env.example .env)
docker compose up --build     # first build takes a few minutes
```

Open **http://localhost:3000** and sign in with the demo admin account configured in `.env`
(`DEMO_ADMIN_EMAIL` / `DEMO_ADMIN_PASSWORD`). There is no self-registration. **Upgrading from the earlier version:**
the database schema was rewritten, run `docker compose down -v` once to recreate it.

- API documentation (Swagger UI): http://localhost:8080/swagger-ui.html
- To use the real Claude API set `AI_PROVIDER=claude` and `ANTHROPIC_API_KEY=...` in `.env`. Without a key the
  platform uses the deterministic offline provider (generated drafts are then kept exactly as the template).
- Optional LocalStack: `docker compose --profile localstack up`

## Development

| Task | Command |
|------|---------|
| Database only | `docker compose up -d postgres` |
| Backend (port 8080) | `cd backend && ./mvnw spring-boot:run` (Windows: `mvnw.cmd`) — set `DB_PASSWORD` to `POSTGRES_PASSWORD` from `.env`, and `APP_DEMO_DATA_ENABLED=true` + `DEMO_ADMIN_PASSWORD` if you want the demo admin |
| Frontend (port 5173, proxies /api) | `cd frontend && npm install && npm run dev` |
| Backend tests (needs Docker for Testcontainers) | `cd backend && ./mvnw test` |
| Frontend type check / build | `cd frontend && npm run build` |
| Production-like start | `docker compose up --build -d` |
| Reset the database | `docker compose down -v` (deletes the `pgdata` volume), then `docker compose up` |
| Logs | `docker compose logs -f backend` |

## Repository layout

```
backend/    Spring Boot modular monolith (auth, user, scenario, authoring, scoring, ai)
frontend/   React SPA (admin: dashboard, generator, scenario editor, review & publish, graph, versions)
docs/en/    Technical documentation (primary)
docs/hy/    Armenian translation
```

## Documentation

**Current scope:** [docs/en/17-admin-authoring-platform.md](docs/en/17-admin-authoring-platform.md) (admin-only
platform; supersedes the learner-facing parts of documents 01–16). Original design documents:
[docs/en/01-project-overview.md](docs/en/01-project-overview.md). Key documents:
[architecture](docs/en/04-system-architecture.md) · [database](docs/en/05-database-design.md) ·
[simulation engine](docs/en/08-simulation-engine.md) · [AI integration](docs/en/09-ai-integration.md) ·
[security](docs/en/10-security.md) · [testing](docs/en/11-testing.md) · [deployment](docs/en/12-deployment.md) ·
[implementation log](docs/en/16-implementation-log.md) · [thesis material](docs/en/15-thesis-material.md) ·
[thesis figures](docs/en/thesis-figures.md). Armenian versions are in [docs/hy](docs/hy).

## Safety

The platform simulates incidents with pre-authored data. Scenario actions come from a fixed catalogue; no command is
ever executed. The AI has no tools and cannot change state — its output is validated and only used as text or as a
draft that an administrator must review and publish.
