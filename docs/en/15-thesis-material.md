# 15 — Thesis Material

Material organised by the three thesis sections. Each bullet points to the detailed document; figures are listed in
[thesis-figures.md](thesis-figures.md). The thesis story of this module: **an AI-assisted authoring and
quality-assurance platform for cyber-incident training content**. The learner-facing runtime (trainees playing the
scenarios, grading of attempts, progress) is the other team member's module; published scenario versions are the
hand-over point between the two.

## Section 1 — Research of Technologies and Methods Used in the Platform

**1.1 Problem domain**
- Cloud shared-responsibility model; most cloud incidents are customer-side misconfigurations and identity
  compromises (03 §1.1).
- Incident response lifecycle (NIST SP 800-61) as the structure a training scenario must teach: detect → investigate
  → contain → eradicate → recover → harden (03 §1.2, Fig. 1).
- Cloud log sources a scenario must simulate credibly: auth logs, audit trail, sign-in logs, flow logs, storage
  access logs, threat-detection alerts (03 §1.3).

**1.2 Training-content methods**
- Cyber ranges vs. simulation with pre-authored telemetry vs. theoretical courses; pre-authored telemetry trains the
  decision workflow safely, cheaply and deterministically — and makes content quality the critical factor (03 §1.4).
- Scenario-as-data: one declarative document (resources, timeline, scored actions) instead of scenario-specific
  code; the same idea that made the engine generic now makes authoring tooling possible (17 §2).
- LLM-assisted content generation; risks: hallucination, structural corruption, non-determinism, prompt injection,
  cost — and the mitigations measured in this project (03 §6, 09 §4).

**1.3 Technologies** (each with WHAT / WHY / alternatives — 03 §2–5)
- Java 21, Spring Boot 4.1 (Web MVC, Security 7, Data JPA / Hibernate 7, Validation), Flyway, springdoc-openapi.
- PostgreSQL 17 with `jsonb` (version snapshots, quality reports); Testcontainers instead of H2.
- React 19, TypeScript, Vite, MUI, React Router, Axios; react-i18next for the bilingual UI.
- Docker, Docker Compose, nginx; LocalStack as optional AWS emulator.
- Claude API via the official Anthropic Java SDK; mock provider for offline, deterministic operation (03 §6, 09).
- Figures: 1–4.

**1.4 Methods for safe AI use** — "AI proposes, application decides": structural equivalence checks against the
draft, the same validator for every entry point, hard timeouts, deterministic fallback, audit logging, delimited
untrusted input in prompts (09 §4, 17 §4).

## Section 2 — System Design and Software Implementation

**2.1 Requirements** — actors, functional and non-functional requirements of the authoring platform, MVP flows (02).

**2.2 Architecture** — modular monolith (ADR-1), package by feature (ADR-2), DTO separation (ADR-3), stateless JWT
(ADR-4), scenario-as-data (ADR-5), deterministic scoring rules (ADR-6), simulated resources (ADR-7), AI provider
abstraction (ADR-8), Flyway (ADR-9) (04; Fig. 2, 5).

**2.3 Database** — scenario template tables, immutable `scenario_versions` snapshots in `jsonb`, status/revision
lifecycle, CHECK constraints mirroring enums, `ai_interactions` audit table (05; Fig. 7).

**2.4 Backend implementation** — module structure, REST API of the authoring platform, uniform error format,
transaction design that keeps AI calls outside database transactions (06, 16; Fig. 10).

**2.5 The authoring pipeline — technical core of this module** (17; Fig. 6, 8, 9, 11)
- *Generation*: vetted templates per scenario type, consistent parameter substitution, optional AI polish.
- *Dependency graph*: actions, events and resources as nodes; prerequisite / reveals / targets / effect edges.
- *Validation*: field and cross-reference rules plus graph-level analysis (errors vs warnings) — e.g. evidence
  reachable only through a harmful action, phase inversion, missing dangerous path.
- *Test runner*: plays the correct path and the dangerous path in memory with the exact scoring rules the learner
  module applies (`ScoringEngine` is shared); the correct path must reach 100 %, the dangerous path must fail.
- *Quality score*: 0–100 over validity (25), tests (25), coverage (20), pedagogy (20), structure (10), each with a
  text justification; grades EXCELLENT / GOOD / FAIR / POOR.
- *Publishing gates*: server-side re-checks (no errors, both paths pass, score ≥ 70) before an immutable version is
  written; editing a published scenario re-opens a draft without touching the published version; restore.

**2.6 AI integration** — three AI tasks (generation polish, variation, on-demand translation), provider abstraction,
prompt structure, structural validation, hard timeout, fallback, `ai_interactions` audit (09; Fig. 4, 12, 13).

**2.7 Security** — authentication flow, ADMIN-only authorization on `/api/admin/**`, server-enforced publish gates,
threat model including prompt injection through the administrator's brief (10, 17 §4).

**2.8 Frontend** — admin SPA structure, typed API layer, route guards, the scenario editor with review/graph/version
panels; bilingual UI with typed resource bundles (ADR-11) and on-demand AI translation of content (ADR-12)
(07, 16; Fig. 14–20).

**2.9 Testing and deployment** — test strategy and inventory (63 backend tests: unit, MockMvc + Testcontainers
integration), Docker setup (11, 12; Fig. 3).

**Implementation problems worth discussing** (from 16):
- Hibernate insert-before-delete ordering when replacing children with unique keys.
- `@Transactional` self-invocation bypassing the Spring proxy.
- React effect returning a Promise from `scrollIntoView` in current Chrome.
- Spring Boot 4 / Jackson 3 package changes.
- Rewriting a Flyway migration during the scope change: existing databases must be recreated, and a stale compiled
  copy of a deleted migration in `target/classes` produces "Found more than one migration with version X" until a
  clean build.
- The scope change itself as an engineering exercise: deleting the learner runtime while keeping the scoring rules
  and scenario model shared with the other module (16, 17 §1).

## Section 3 — Functional Capabilities and Results of the Developed Platform

**3.1 Administrator functions** (14, 17; Fig. 14–20) — login; dashboard with the authoring workflow and content
status; scenario generator (type, difficulty, asset, attacker IP, region, free-text brief, optional AI); structured
editor for resources, timeline, actions, scoring and hints; dependency graph; validation findings; test runs;
quality score with breakdown; publishing with enforced gates; version history with restore; bilingual UI with
on-demand translation.

**3.2 Demonstration scenarios** — SSH brute force (network), compromised credentials (authentication), public bucket
(storage); each ships as a generator template and as a seed scenario published as version 1 at start-up; each has a
full timeline with progressive disclosure, evidence markers, distractor and harmful actions, hints and 100 points of
expected actions (`backend/src/main/resources/scenarios`).

**3.3 Results / verification**

| Aspect | Result |
|--------|--------|
| Automated tests | 63 backend tests (unit, Mockito, MockMvc + Testcontainers) — all passing |
| Content quality gates | All three seed scenarios are valid, pass both test paths and reach the publishing threshold (asserted by tests) |
| Publish enforcement | A draft with validation errors or a low quality score is rejected server-side with `422 PUBLISH_BLOCKED` (tested) |
| Versioning | Published versions are immutable; editing re-opens a draft; restore verified (tested) |
| Security | Anonymous and non-admin requests to `/api/admin/**` are rejected; tampered JWT → 401 (tested) |
| AI reliability | Provider exception, timeout, invalid JSON and structure-breaking output all fall back to the deterministic draft (tested) |
| Deployment | Full stack starts with `docker compose up --build` (fresh volume required after the schema rewrite) |

**3.4 Limitations and future work**
- Real Claude API calls implemented but not runtime-verified without an API key (mock provider verified).
- No automated frontend tests; no refresh tokens or login rate limiting.
- Admin accounts are provisioned by the operator (environment variables); there is no in-app user management.
- The learner module consumes published versions directly from the database; a read-only HTTP contract between the
  modules is future work.
- Possible extensions: more scenario types (IAM privilege escalation, data exfiltration), AI-assisted validation
  explanations, scenario diff between versions, Playwright E2E tests, HttpOnly cookie authentication.
