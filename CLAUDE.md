# CLAUDE.md — CyberSim (admin scenario-authoring platform)

## What this repository is

The **administrator's half** of CyberSim, a university graduation project (thesis by Arman Gasparyan).
Administrators **generate, edit, validate, test, quality-score, publish and version** simulated
cloud-incident training scenarios. The **learner/student runtime is NOT here** — it is built by another
team member in a separate module that integrates over the `/api/learner/**` service API (published
scenarios out, completed exam submissions in; ADR-13). Do not re-add student-facing features
(registration, an interactive simulation runtime, student pages); they were removed deliberately in commit
`42ae909`. `student_attempts` is NOT a student feature — it stores the records the learner module submits,
verified here.

History in two lines: the repo originally implemented the full product (student simulator + admin panel,
commits up to `5c9925e`); the scope was then split between team members and this repo was cut down to the
admin flow. `docs/en/17-admin-authoring-platform.md` is the canonical description of the current scope.

## Architecture (backend: Spring Boot 4.1 / Java 21, frontend: React 19 + TS + MUI, PostgreSQL 17)

- Five controllers:
  - `AuthController` — `POST /api/auth/login`, `GET /api/auth/me` (JWT; no self-registration, ADMIN is the only role)
  - `AuthoringController` — everything under `/api/admin/scenarios` (CRUD, generate, variations, graph,
    validate, test-run, evaluate, publish, versions/restore)
  - `TranslationController` — `POST /api/ai/translate` (on-demand UI text translation, never stored; ADR-12)
  - `LearnerController` — `/api/learner/**`, the learner module's service API (ADR-13). Auth is the
    `X-API-Key` header checked by `LearnerApiKeyFilter` against `LEARNER_API_KEY` (503 if unset). Submissions
    are **verified server-side** by replaying actions against the pinned `scenario_versions` row via
    `ScenarioTestRunner.play` + `ScoringEngine`; the claimed score only sets `scoreMatches`.
  - `ExamAdminController` — `/api/admin/exams`: list/detail of submitted exams + `POST /{id}/review`
    (AiTask.REVIEW → advisory rating/feedback, stored on `student_attempts`, fetched by the learner module)
- The 8-step authoring pipeline lives in `backend/.../am/cybersim/authoring/`:
  `ScenarioGeneratorService` (3 templates in `resources/scenarios/*.json`, optional AI polish) →
  `ScenarioGraph` (dependency graph) → `ScenarioAnalyzer` (ERROR/WARNING findings) →
  `ScenarioTestRunner` (in-memory correct path + dangerous path, reuses `ScoringEngine`) →
  `QualityScorer` (0–100; **publish threshold 70**, gates recomputed server-side) →
  `AuthoringService.publish` (immutable `ScenarioVersion` snapshot; editing a PUBLISHED scenario
  reverts it to DRAFT without touching published versions).
- AI tasks (`AiTask`): `GENERATION`, `VARIATION`, `TRANSLATION`, `REVIEW`. All go through `AiGateway`
  (timeout → `AiOutputValidator` → `MockAiProvider` fallback, audited in `ai_interactions`).
  Core principle everywhere: **AI proposes, the application decides** — AI output is parsed,
  structure-checked and validated; it never changes state directly.
- `ScoringEngine` + `hint_penalty` + `scenario_hints` are kept **for the learner module**, exercised
  here only by the test runner. Don't delete them as dead code.

## Commands

| Task | Command |
|---|---|
| Full stack | `docker compose up --build -d` → app at http://localhost:3000, Swagger at :8080/swagger-ui.html |
| Backend tests (needs Docker for Testcontainers) | `cd backend && ./mvnw test` — 76 executions |
| Frontend build / type check | `cd frontend && npm run build` |
| DB reset | `docker compose down -v && docker compose up -d` (demo admin + 3 seed scenarios re-seed) |

Demo admin credentials come from `.env` (`DEMO_ADMIN_EMAIL`/`DEMO_ADMIN_PASSWORD`); `.env` is gitignored —
never commit it, and never put a real `ANTHROPIC_API_KEY` in `.env.example`. `AI_PROVIDER=mock` by default;
the mock cannot really translate or polish (it echoes/substitutes deterministically) and the UI says so.

## Gotchas that have actually bitten us

- **Single-migration policy (pre-release):** `V1__initial_schema.sql` gets rewritten instead of adding
  V2+ while unreleased. Any change to it ⇒ existing volumes fail Flyway checksum validation ⇒
  `docker compose down -v` is required. Also: a deleted/renamed migration can linger in
  `backend/target/classes` and cause "Found more than one migration with version X" — run `./mvnw clean`.
- **`ai_interactions.ck_ai_type` CHECK constraint** enumerates AiTask values; adding an enum value
  without extending the constraint = runtime 500 that unit tests do not catch.
- **Local Node is v18 via nvm default; Vite 8 needs ≥20.** Use `~/.nvm/versions/node/v23.11.1/bin`
  (Docker builds on node:24 and is unaffected). If Rolldown complains about a missing native binding,
  reinstall `node_modules` under the newer Node.
- **i18n:** all UI strings live in `frontend/src/i18n/en.ts` + `hy.ts`; `hy` is typed `typeof en`, so a
  missing key is a compile error — never hardcode user-facing strings in components. Armenian glyphs come
  from self-hosted `@fontsource/noto-sans-armenian` (Inter has none). When writing Armenian anywhere,
  never emit U+0596 (֖) where the letter ֆ (U+0586) is meant.
- **Docs are bilingual and mirrored:** every `docs/en/NN-*.md` has a `docs/hy/NN-*.md` with identical
  structure. If you change one, change the other. Doc 17 wins over 01–16 on any contradiction.
- **Git identity:** commit as the repo-configured user (Arman Gasparyan); no AI co-author trailers,
  no "Claude" in commit messages — this is a graduation project.

## Where to look first

`docs/en/17-admin-authoring-platform.md` (current scope) → `docs/en/16-implementation-log.md`
(decision history, including what was removed and why) → `AuthoringIntegrationTest` (the end-to-end
spec of the whole pipeline in executable form).
