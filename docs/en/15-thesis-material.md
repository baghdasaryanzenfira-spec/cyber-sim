# 15 — Thesis Material

Material organised by the three thesis sections. Each bullet points to the detailed document; figures are listed in
[thesis-figures.md](thesis-figures.md).

## Section 1 — Research of Technologies and Methods Used in the Platform

**1.1 Problem domain**
- Cloud shared-responsibility model; most cloud incidents are customer-side misconfigurations and identity
  compromises (03 §1.1).
- Incident response lifecycle (NIST SP 800-61) and its mapping to the simulation states (03 §1.2, Fig. 1).
- Cloud log sources used in investigations: auth logs, audit trail, sign-in logs, flow logs, storage access logs,
  threat-detection alerts (03 §1.3).

**1.2 Training methods**
- Cyber ranges vs. simulation with pre-authored telemetry vs. theoretical courses; the chosen method trains the
  decision workflow safely, cheaply and deterministically (03 §1.4).
- Intelligent tutoring with LLMs; risks: hallucination, non-determinism, prompt injection, cost (03 §6.1).

**1.3 Technologies** (each with WHAT / WHY / alternatives — 03 §2–5)
- Java 21, Spring Boot 4.1 (Web MVC, Security 7, Data JPA / Hibernate 7, Validation), Flyway, springdoc-openapi.
- PostgreSQL 17 with `jsonb`; Testcontainers instead of H2.
- React 19, TypeScript, Vite, MUI, React Router, Axios.
- Docker, Docker Compose, nginx; LocalStack as optional AWS emulator.
- Claude API via the official Anthropic Java SDK; structured outputs; mock provider (03 §6, 09).
- Figures: 1–4.

**1.4 Methods for safe AI use** — "AI advises, application decides", deterministic grading, output validation,
least-information prompts, fallback (09 §4).

## Section 2 — System Design and Software Implementation

**2.1 Requirements** — actors, FR-1…FR-22, NFR-1…NFR-8, MVP flows (02; Fig. 6).

**2.2 Architecture** — modular monolith (ADR-1), package by feature (ADR-2), DTO separation (ADR-3), stateless JWT
(ADR-4), scenario-as-data (ADR-5), deterministic scoring (ADR-6), simulated resources (ADR-7), AI provider
abstraction (ADR-8), Flyway (ADR-9) (04; Fig. 2, 5).

**2.3 Database** — template vs instance tables, snapshots of scores, `jsonb` only for flexible attributes, partial
unique index for one active attempt, CHECK constraints mirroring enums (05; Fig. 7).

**2.4 Backend implementation** — module structure, REST API, uniform error format, transaction design without DB
transactions during AI calls (06, 16 Steps 2–7; Fig. 10).

**2.5 Simulation engine** — lifecycle, action catalogue, progressive disclosure of logs, effects on resources,
analyst events, scoring rules and formula, determinism (08; Fig. 8, 9, 11, 12).

**2.6 AI integration** — four AI capabilities, provider abstraction, prompt structure, validation (solution-leak check,
JSON parsing), hard timeout, fallback, audit log, validated scenario variations (09; Fig. 4, 12, 13).

**2.7 Security** — authentication flow, authorization layers, ownership checks returning 404, threat model (10).

**2.8 Frontend** — structure, typed API layer, route guards, the active simulation page (07, 16 Steps 8–10).

**2.9 Testing and deployment** — test strategy and inventory (74 backend tests), Docker setup (11, 12; Fig. 3).

**Implementation problems worth discussing** (from 16):
- Hibernate insert-before-delete ordering when replacing children with unique keys.
- `@Transactional` self-invocation bypassing the Spring proxy.
- React effect returning a Promise from `scrollIntoView` in current Chrome.
- Spring Boot 4 / Jackson 3 package changes.

## Section 3 — Functional Capabilities and Results of the Developed Platform

**3.1 Student functions** (13; Fig. 14–18) — registration/login, dashboard, scenario catalogue, briefing, active
simulation (investigation, evidence board, response actions, AI assistant), result with AI feedback, progress and AI
recommendations.

**3.2 Administrator functions** (14; Fig. 19–20) — dashboard, user management, scenario authoring with evidence,
expected actions and scoring rules, AI scenario variations, attempts with AI interaction log, analytics of common
mistakes.

**3.3 Demonstration scenarios** — SSH brute force (network), compromised credentials (authentication), public bucket
(storage); each: 10 expected actions worth 100 points, distractor and harmful actions, 11–13 timeline events,
progressive disclosure, 4 hints (`backend/src/main/resources/scenarios`).

**3.4 Results / verification**

| Aspect | Result |
|--------|--------|
| Automated tests | 74 backend tests (unit, Mockito, MockMvc + Testcontainers) — all passing |
| Determinism | Scripted run scored exactly the hand-calculated value (58/100: 10+15+15+20+10−10−2) |
| Security | Students receive 403 on the admin API; other students' simulations return 404; tampered JWT → 401 |
| AI reliability | Provider exception, timeout, invalid JSON and solution-leaking hints all fall back to a valid answer (tested) |
| Deployment | Full stack starts with `docker compose up --build` (verified) |
| UI | All main pages verified manually in Chrome |

**3.5 Limitations and future work**
- Real Claude API calls implemented but not runtime-verified without an API key.
- No automated frontend tests; no refresh tokens or login rate limiting.
- LocalStack integration only prepared (optional profile), not used by scenarios.
- Possible extensions: more scenarios (IAM privilege escalation, data exfiltration), team exercises, AI-generated
  hint variations per student, Playwright E2E tests, HttpOnly cookie authentication.
