# 11 — Testing

## 1. Strategy

| Level | Tools | What is tested | Why |
|-------|-------|---------------|-----|
| Unit | JUnit 5, AssertJ | State machine, scoring engine, simulation engine, scenario validator, mock AI | Core rules are pure Java → fast, exhaustive tests without Spring |
| Unit with mocks | Mockito | `AiGateway` (fallback, timeout, invalid output) | The AI provider is replaced by a mock to simulate failures deterministically |
| Integration / API | Spring Boot Test, MockMvc, Testcontainers PostgreSQL 17 | Auth, security rules, scenario catalogue, full simulation flow, admin API, analytics SQL | Real database = real Flyway migrations, `jsonb`, partial unique indexes, PostgreSQL-specific SQL |
| End-to-end smoke | PowerShell script against `docker compose` | Login → simulation → AI → completion → admin analytics through nginx | Verifies the containerised deployment |
| Manual UI | Chrome | Pages render and work with real data | Visual check of the SPA |

### Decision: Testcontainers instead of H2
- **Why:** the schema uses PostgreSQL-specific features (`jsonb`, partial unique index, `FILTER`, `jsonb_array_elements`).
  H2 would either fail or behave differently, giving false confidence.
- **Cost:** tests require Docker; the first run downloads the `postgres:17-alpine` image. All integration test
  classes share one Spring context and one container (context caching), so the suite stays fast (~1 minute).

## 2. Test inventory (backend)

| Class | Type | Tests | Focus |
|-------|------|------:|-------|
| `AuthIntegrationTest` | API | 10 | registration, role escalation attempt, duplicate e-mail, validation, login, wrong password vs unknown e-mail, disabled user, missing / tampered token |
| `ScenarioDefinitionValidatorTest` | unit | 9 | points/outcome sign, unknown references, reveal by response action, prerequisite cycles, duplicates, unplayable scenarios, bean validation |
| `ScenarioCatalogIntegrationTest` | API | 5 | seeding with JSON columns, catalogue, briefing without solution data, 401, 404 |
| `SimulationStateMachineTest` | unit | 15 | every allowed and forbidden transition |
| `SimulationEngineTest` | unit | 8 | start, reveal, effects, out-of-order penalty, duplicates, harmful actions, unknown actions |
| `ScoringEngineTest` | unit | 5 | perfect run, penalties, rounding, order penalty floor, determinism |
| `SimulationFlowIntegrationTest` | API | 7 | full student flow, hidden solution data, exact score of a mistakes run, invalid transitions (409), ownership (404), evidence flags, assistant, abandon |
| `AiGatewayTest` | unit + Mockito | 6 | mock primary, valid AI answer, exception, invalid JSON, solution-leaking hint, timeout → fallback |
| `MockAiProviderTest` | unit | 3 | escalating hints, refusal of full solution, feedback content |
| `AdminIntegrationTest` | API | 6 | role protection, create/edit/deactivate scenario, 422 validation, AI variation draft, attempts & analytics, user disabling |
| **Total** | | **74** | |

## 3. Results

| Date | Command | Result | Status |
|------|---------|--------|--------|
| 2026-10-06 | `cd backend && mvnw test` | 74 tests, 0 failures, 0 errors | **TESTED** |
| 2026-10-06 | `npm run build` (tsc + vite) | build successful | **TESTED** (type check) |
| 2026-10-06 | smoke script against `docker compose up` | login, simulation, hint, question, completion (score 58 = hand-calculated), admin analytics, student → admin API = 403 | **VERIFIED** |
| 2026-10-06 | manual check in Chrome | login, dashboard, scenario list/briefing, start simulation | **VERIFIED** (layout issue found and fixed, see log) |

## 4. Not covered / limitations
- No automated frontend tests (component or browser E2E). Recommended next step: Playwright tests for the login →
  simulation → result flow.
- The real Claude API is tested only through a mocked provider; a live call requires an API key
  (**implemented but not runtime-verified** in this environment).
- No load/performance tests (not required for the expected classroom-scale usage).
