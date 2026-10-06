# CyberSim — Cloud Cyber Incident Simulation & Security Specialist Training Platform

University graduation project: *"Development of a Cloud Cyber Incident Simulation and Security Specialist
Training Platform Using Artificial Intelligence"*.

CyberSim lets students investigate **simulated** cloud security incidents (SSH brute force, compromised
credentials, public storage bucket), perform response actions, ask an **AI tutor** (Claude) for hints, and receive a
deterministic score plus AI-generated feedback. Administrators author scenarios, review attempts and analyse common
mistakes. Everything runs locally; nothing touches real external systems.

| | |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA / Hibernate 7, Flyway, PostgreSQL 17 |
| Frontend | React 19, TypeScript, Vite 8, MUI 9, MUI X Charts, React Router 8, Axios |
| AI | Claude API via the official Anthropic Java SDK (default model `claude-opus-5-5`) + offline mock provider |
| Infrastructure | Docker Compose (PostgreSQL, backend, nginx frontend, optional LocalStack) |
| Tests | JUnit 5, Mockito, Spring Boot Test, MockMvc, Testcontainers — 74 backend tests |

## Quick start (Docker)

Requirements: Docker Desktop.

```bash
cp .env.example .env          # then replace every "change-me" value (Windows: copy .env.example .env)
docker compose up --build     # first build takes a few minutes
```

Open **http://localhost:3000** and sign in with the demo accounts configured in `.env`
(`DEMO_ADMIN_EMAIL` / `DEMO_ADMIN_PASSWORD`, `DEMO_STUDENT_EMAIL` / `DEMO_STUDENT_PASSWORD`).

- API documentation (Swagger UI): http://localhost:8080/swagger-ui.html
- To use the real Claude API set `AI_PROVIDER=claude` and `ANTHROPIC_API_KEY=...` in `.env`. Without a key the
  platform uses the deterministic offline tutor.
- Optional LocalStack: `docker compose --profile localstack up`

## Development

| Task | Command |
|------|---------|
| Database only | `docker compose up -d postgres` |
| Backend (port 8080) | `cd backend && ./mvnw spring-boot:run` (Windows: `mvnw.cmd`) — set `DB_PASSWORD` to `POSTGRES_PASSWORD` from `.env`, and `APP_DEMO_DATA_ENABLED=true` + demo passwords if you want demo users |
| Frontend (port 5173, proxies /api) | `cd frontend && npm install && npm run dev` |
| Backend tests (needs Docker for Testcontainers) | `cd backend && ./mvnw test` |
| Frontend type check / build | `cd frontend && npm run build` |
| Production-like start | `docker compose up --build -d` |
| Reset the database | `docker compose down -v` (deletes the `pgdata` volume), then `docker compose up` |
| Logs | `docker compose logs -f backend` |

## Repository layout

```
backend/    Spring Boot modular monolith (auth, user, scenario, simulation, scoring, ai, progress, analytics, admin)
frontend/   React SPA (student pages, active simulation, admin panel)
docs/en/    Technical documentation (primary)
docs/hy/    Armenian translation
```

## Documentation

Start with [docs/en/01-project-overview.md](docs/en/01-project-overview.md). Key documents:
[architecture](docs/en/04-system-architecture.md) · [database](docs/en/05-database-design.md) ·
[simulation engine](docs/en/08-simulation-engine.md) · [AI integration](docs/en/09-ai-integration.md) ·
[security](docs/en/10-security.md) · [testing](docs/en/11-testing.md) · [deployment](docs/en/12-deployment.md) ·
[implementation log](docs/en/16-implementation-log.md) · [thesis material](docs/en/15-thesis-material.md) ·
[thesis figures](docs/en/thesis-figures.md). Armenian versions are in [docs/hy](docs/hy).

## Safety

The platform simulates incidents with pre-authored data. Students choose from a fixed action catalogue; no command is
ever executed. The AI has no tools and cannot change state — its output is validated and only used as text or as an
inactive draft that an administrator must approve.
