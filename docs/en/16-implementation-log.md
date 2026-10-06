# 16 — Implementation Log

Chronological technical history of the project. Status terms:
**IMPLEMENTED** (code written) · **TESTED** (automated tests executed and passing) ·
**VERIFIED** (manually run and observed working).

## Roadmap

| Phase | Content | Status |
|-------|---------|--------|
| 0 | Project analysis and planning | done |
| 1 | Repository/project structure | done |
| 2 | Database and backend foundation | done |
| 3 | Authentication and authorization | done |
| 4 | Scenario management | done |
| 5 | Simulation engine | done |
| 6 | Scoring and progress | done |
| 7 | AI integration | done (real API not runtime-verified) |
| 8 | Frontend foundation | planned |
| 9 | User simulation interface | planned |
| 10 | Admin panel | backend done |
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

---

## Step 4 — Scenario management (2026-10-06)

**Implemented:** scenario entities (`Scenario` + objectives, resources, events, actions, hints), `ScenarioDefinition`
(one JSON document per scenario), `ScenarioDefinitionValidator` (cross-reference rules), `ScenarioMapper`,
`ScenarioService`, student catalogue endpoints, `ScenarioSeeder` and three fully authored scenarios in
`resources/scenarios/*.json` (SSH brute-force, compromised credentials, public bucket — each with exactly 100
points of expected actions, harmful and neutral distractor actions, progressive log disclosure and 4 hints).

**Why one document for seed files, admin editor and AI output:** a single validator protects all three entry points;
see ADR-5.

**Problems encountered:**
- Hibernate executes inserts before orphan deletes, so replacing children with the same keys violated the unique
  constraints. *Solution:* clear the collections and `flush()` before adding the new children (`ScenarioService.update`).
- Unit tests used one-letter action keys which the key pattern rejects; the cross-reference rules were never reached.
  *Solution:* realistic keys in tests (the validator was right).

**Testing:** `ScenarioDefinitionValidatorTest` (9 unit tests), `ScenarioCatalogIntegrationTest` (5) — includes a check
that the briefing JSON contains no solution data. **TESTED.**

## Step 5 — Simulation engine (2026-10-06)

**Implemented:** `SimulationStatus`, `SimulationStateMachine` (pure transition functions), entities `Simulation`,
`SimulationResource`, `SimulationEvent`, `SimulationAction`, `SimulationResult`; `SimulationEngine` (start, apply
action, reveal events, effects, analyst timeline events, evidence flags); `SimulationService`; `SimulationController`.

**Key decisions:**
- *Engine without persistence:* the engine only mutates the aggregate; the service handles transactions and
  ownership → the engine is unit-tested without a database (`SimulationEngineTest`).
- *Snapshots on actions:* label, outcome and points are copied into `simulation_actions`, so history is stable.
- *Optimistic locking* (`lock_version`) on `simulations` protects against double-clicks/parallel requests.
- *Information hiding:* student DTOs contain `null` for outcome/points/evidence until completion.
- *Partial unique index* guarantees at most one unfinished attempt per student and scenario.

**Problem encountered:** `complete()` called the `@Transactional` method `result()` on `this`; Spring's proxy was
bypassed and the lazy scenario failed to load (`LazyInitializationException`). *Solution:* explicit
`TransactionTemplate` for that step (documented in the code).

**Testing:** `SimulationStateMachineTest` (15), `SimulationEngineTest` (8), `SimulationFlowIntegrationTest` (7 —
perfect run = 100, mistakes run = exactly 13 points as calculated by hand, invalid transitions = 409, ownership = 404).
**TESTED.**

## Step 6 — Scoring and progress (2026-10-06)

**Implemented:** `ScoringEngine` (pure, deterministic), `ScoreResult`; `ProgressService` / `ProgressController`
(`/api/progress/me`, `/api/progress/me/recommendations`).

**Why progress is computed, not stored:** a separate progress table would duplicate data already present in
`simulations` + `simulation_results` and could become inconsistent; at this scale computing it is fast.

**Testing:** `ScoringEngineTest` (5) + progress assertions in `AdminIntegrationTest`. **TESTED.**

## Step 7 — AI integration (2026-10-06)

**Implemented:** `AiProvider` interface; `ClaudeAiProvider` (official Anthropic Java SDK 2.68.0, model
`claude-opus-5-5` by default, configurable effort/timeout/max tokens, structured outputs for JSON tasks, server-side
refusal fallback, refusal/truncation detection); `MockAiProvider` (rule-based, progress-aware); `AiConfig`
(provider selection); `AiPromptBuilder`; `AiOutputValidator` (length limits, JSON parsing, solution-leak check);
`AiGateway` (hard timeout on a virtual thread, fallback, audit log); `AiInteraction` audit entity; `TutorService`
(hint, question, feedback, recommendations); `ScenarioVariationService` (validated AI variations);
`SimulationAssistantService` (hint counter + penalty decided by the application).

**Why no DB transaction during AI calls:** the service builds an immutable `SimulationSnapshot` in a short
transaction, calls the AI without holding a connection, then writes the result in a second short transaction.

**Why `sealed interface AiPayload`:** every provider must handle every task type — the compiler enforces it
through exhaustive `switch` pattern matching.

**Testing:** `AiGatewayTest` (6: mock primary, valid AI answer, exception → fallback, invalid JSON → fallback,
solution-leaking hint → fallback, 10-second provider vs 1-second timeout → fallback in ~3 s), `MockAiProviderTest` (3).
**TESTED with the mock provider and a mocked Claude provider. The real Claude API call is implemented but not
runtime-verified because no `ANTHROPIC_API_KEY` is configured in this environment.**

## Step 10a — Admin backend and analytics (2026-10-06)

**Implemented:** `AdminService`, `AdminController` (`/api/admin/**`: users, user detail with progress, enable/disable,
scenario CRUD + activation + AI variation, attempts list with filters/paging, attempt detail with AI interactions,
analytics overview and common mistakes); `AnalyticsService` using `JdbcClient` and PostgreSQL features
(`FILTER`, `jsonb_array_elements` over the frozen `missed_actions` snapshots).

**Testing:** `AdminIntegrationTest` (6): role protection, create → edit (version 2) → deactivate (hidden from
students), invalid scenario → 422 with field errors, AI variation stored inactive with changed IPs, attempts and
analytics reflect a student run, admin cannot disable themselves. **TESTED.**

**Test suite status after Step 10a:** `mvnw test` → **74 tests, 0 failures** (2026-10-06).
