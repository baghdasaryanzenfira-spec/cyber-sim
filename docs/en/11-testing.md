# 11 — Testing

## 1. Strategy

| Level | Tools | What is tested | Why |
|-------|-------|---------------|-----|
| Unit | JUnit 5, AssertJ | Scenario validator, dependency graph, analyzer rules, test runner, quality score, scoring engine, mock AI | The authoring rules are pure Java → fast, exhaustive tests without Spring |
| Unit with mocks | Mockito | `AiGateway` (fallback, timeout, invalid output) | The AI provider is replaced by a mock to simulate failures deterministically |
| Integration / API | Spring Boot Test, MockMvc, Testcontainers PostgreSQL 17 | Auth and security rules, seeding, the full authoring flow (generate → evaluate → publish → versions) | Real database = real Flyway migration, `jsonb` snapshots, PostgreSQL-specific behaviour |
| Manual UI | Chrome | Pages render and work with real data | Visual check of the SPA |

### Decision: Testcontainers instead of H2
- **Why:** the schema uses PostgreSQL-specific features (`jsonb` columns for properties, details and version
  snapshots). H2 would either fail or behave differently, giving false confidence.
- **Cost:** tests require Docker; the first run downloads the `postgres:17-alpine` image. All integration test
  classes share one Spring context and one container (context caching), so the suite stays fast.

## 2. Test inventory (backend)

8 test classes; **63 test executions** from 61 annotated test methods — 60 `@Test` plus one
`@ParameterizedTest` that runs once per seed scenario (×3).

| Class | Type | Executions | Focus |
|-------|------|-----------:|-------|
| `AuthoringUnitTest` | unit | 18 | graph nodes/edges and topological order; analyzer rules (expected-depends-on-unexpected, phase inversion, evidence only via wrong action, missing dangerous path); warnings vs errors; test runner on a fixture (correct and dangerous path, failure cases); **every seed scenario is valid, passes both paths and reaches the publishing threshold** (parameterized ×3); quality-score component bounds and determinism |
| `AuthoringIntegrationTest` | API | 9 | anonymous callers rejected; seeds imported and published as version 1; full flow generate → evaluate → publish → version for SSH brute force; every scenario type generates a publishable draft with parameters; AI generation uses the validated pipeline; invalid generator parameters rejected; publish blocked while validation has errors; live evaluation of an unsaved definition; invalid definition rejected with all errors |
| `ScenarioDefinitionValidatorTest` | unit | 9 | points/outcome sign, unknown references, reveal by response action, prerequisite cycles, duplicates, unplayable scenarios, SYSTEM events and missing evidence notes, bean validation |
| `AuthIntegrationTest` | API | 7 | registration endpoint absent, login issues a token that authenticates `/me`, identical error for wrong password and unknown e-mail, disabled admin cannot log in, JSON 401 without token, tampered token rejected, bearer token type |
| `TranslationServiceTest` | unit | 7 | unsupported language, empty and over-length input, plausible-length acceptance, explanation-instead-of-translation rejected, mock echo, source labelling |
| `AiGatewayTest` | unit + Mockito | 5 | mock primary keeps the draft, valid AI answer used, provider exception → fallback, invalid JSON → fallback, slow provider times out → fallback |
| `ScoringEngineTest` | unit | 5 | perfect run = 100 %, penalties reduce the score, rounding, order-penalty floor at zero, determinism |
| `MockAiProviderTest` | unit | 3 | generation returns the draft unchanged, variation keeps structure and sets the new slug, translation echoes the source |
| **Total** | | **63** | |

## 3. Results

| Date | Command | Result | Status |
|------|---------|--------|--------|
| 2026-10-06 | `cd backend && mvnw test` | 74 tests, 0 failures (original student-platform suite) | historical |
| 2026-10-07 | `cd backend && mvnw test` | 81 tests, 0 failures (student platform + translation guards) | historical |
| 2026-10-09 | `cd backend && mvnw test` | **63 tests, 0 failures, 0 errors** (admin authoring suite) | **TESTED** |
| 2026-10-09 | `cd frontend && npm run build` (tsc + vite) | build successful | **TESTED** (type check) |

The drop from 81 to 63 is the removal of the student runtime (state machine, simulation engine/flow, catalogue,
progress and admin-analytics tests) and the addition of the authoring suite (27 executions in
`AuthoringUnitTest` + `AuthoringIntegrationTest`).

## 4. Not covered / limitations
- No automated frontend tests (component or browser E2E). Recommended next step: Playwright tests for the
  login → generate → publish flow.
- The real Claude API is exercised only through a mocked provider; a live call requires an API key
  (**implemented but not runtime-verified** in this environment).
- No load/performance tests (not required for the expected single-administrator usage).
