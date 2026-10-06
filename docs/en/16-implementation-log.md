# 16 — Implementation Log

Chronological technical history of the project. Status terms:
**IMPLEMENTED** (code written) · **TESTED** (automated tests executed and passing) ·
**VERIFIED** (manually run and observed working).

## Roadmap

| Phase | Content | Status |
|-------|---------|--------|
| 0 | Project analysis and planning | done |
| 1 | Repository/project structure | in progress |
| 2 | Database and backend foundation | planned |
| 3 | Authentication and authorization | planned |
| 4 | Scenario management | planned |
| 5 | Simulation engine | planned |
| 6 | Scoring and progress | planned |
| 7 | AI integration | planned |
| 8 | Frontend foundation | planned |
| 9 | User simulation interface | planned |
| 10 | Admin panel | planned |
| 11 | Testing | planned |
| 12 | Docker/deployment | planned |
| 13 | Documentation review | planned |

---

## Step 0 — Project analysis and planning (2026-10-06)

**Repository state found:** an empty IntelliJ Maven project (`pom.xml` with `org.example:cyber-sim`,
Java 21, no dependencies, no source code), not a git repository. Nothing to preserve.

**Environment found:** JDK 21.0.4, Node.js 25.1, Docker Desktop installed (engine not running at first),
no global Maven.

**Decisions made with the project owner:**
- AI provider: Claude API (with a mock provider for offline use).
- Documentation in English (`docs/en`, primary) and Armenian (`docs/hy`, translation).
- LocalStack is acceptable → included as an optional Compose profile, not a core dependency.
- Scenarios: the three simplest — SSH brute-force, suspicious login / compromised credentials,
  publicly exposed storage bucket.
- Monorepo with `backend/`, `frontend/`, `docs/`.

**Created:** docs 01–10 (overview, requirements, technology research, architecture, database design,
backend API boundaries, frontend structure, simulation engine design, AI integration strategy, security).

**Why first:** the architecture, data model and API contract determine every later phase; writing them
first avoids rework and gives the thesis its design chapter early.

---

## Step 1 — Repository/project structure (2026-10-06)

**Implemented:**
- `git init` (branch `main`), root `.gitignore` (ignores `.env`, build outputs, IDE files).
- Backend skeleton generated with Spring Initializr for **Spring Boot 4.1.1** (latest stable at the time),
  Java 21, package `am.cybersim`, Maven wrapper — dependencies: Web MVC, Security, Data JPA, Validation,
  PostgreSQL, Flyway, Actuator, Testcontainers.
- Removed the original empty root `pom.xml` (agreed with the owner); the backend now lives in `backend/`.
- Directories `frontend/`, `docs/en/`, `docs/hy/`.

**Why Spring Initializr:** it produces a consistent, version-aligned set of starters. Spring Boot 4 split
the starters into smaller modules (e.g. `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`,
and matching `*-test` starters); generating the POM avoids guessing the new artifact names.

**Why the Maven wrapper:** Maven is not installed globally; `mvnw` downloads the pinned Maven version, so
every developer and the Docker build use the same build tool.

---

## Step 2 — Database and backend foundation (2026-10-06)

**Implemented:**
- `V1__initial_schema.sql` — complete schema for all modules (users, scenario templates, simulation
  instances, results, AI audit log) with CHECK constraints, unique keys, indexes and a **partial unique
  index** that allows only one unfinished simulation per student and scenario.
- `application.yml` — every environment-specific value read from environment variables; `ddl-auto: validate`
  (Flyway owns the schema, Hibernate only checks that entities match it); `open-in-view: false`.
- `AppProperties` — typed configuration record (`app.jwt`, `app.cors`, `app.demo`, `app.ai`, `app.scenarios`).
- `common/ApiError`, `ApiException`, `GlobalExceptionHandler` — one error format for all endpoints.
- `ClockConfig` (injectable clock for testable time logic), `OpenApiConfig` (Swagger with bearer auth).

**Why the whole schema in V1:** the data model was fully designed in Phase 0; one reviewed migration is easier
to present in the thesis than many tiny ones. Later changes will be new migrations (`V2__...`), never edits of V1.

**Why `open-in-view: false`:** prevents lazy loading during JSON rendering — all data a response needs must be
loaded inside the service transaction, which makes queries explicit and avoids N+1 surprises.

## Step 3 — Authentication and authorization (2026-10-06)

**Implemented:**
- `User` entity, `Role` enum (`STUDENT`, `ADMIN`), `UserRepository`, `UserDto`.
- `JwtConfig` — HS256 key from `JWT_SECRET` (≥ 32 bytes, otherwise start-up fails; random key with a warning
  if unset in development), `NimbusJwtEncoder` / `NimbusJwtDecoder` with issuer validation.
- `JwtTokenService` — issues tokens with `sub`, `email`, `name`, `role`, `iss`, `iat`, `exp`.
- `SecurityConfig` — stateless filter chain, URL rules (`/api/admin/**` → ADMIN), role claim → `ROLE_*`
  authorities, CORS, JSON 401/403 bodies, delegating BCrypt password encoder.
- `AuthUser` + `AuthUserArgumentResolver` — controllers receive the authenticated user as a parameter.
- `AuthService` / `AuthController` — `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`.
- `DemoDataInitializer` — optional demo admin/student created from environment variables.

**Problems encountered:**
- Spring Boot 4 moved test annotations to new modules/packages
  (e.g. `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`) and uses Jackson 3
  (`tools.jackson.databind`). Solution: located the classes in the downloaded jars instead of guessing.

**Security details worth noting in the thesis:**
- Same error for unknown e-mail / wrong password / disabled account, and a dummy BCrypt check for unknown
  e-mails (timing equalisation) → no user enumeration.
- The register DTO has no `role` field, so mass-assignment of `ADMIN` is impossible (tested).
- `toString()` of request DTOs masks passwords so they never appear in logs.

**Testing:** `AuthIntegrationTest` — 10 tests against real PostgreSQL 17 (Testcontainers):
registration, role escalation attempt, duplicate e-mail (case-insensitive), validation errors, login,
`/me` with token, wrong password vs unknown e-mail, disabled account, missing token → JSON 401,
tampered token → 401. **TESTED: 10/10 passing** (`mvnw test`, 2026-10-06).
