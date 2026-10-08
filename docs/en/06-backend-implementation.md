# 06 — Backend Implementation

> Scope: the **admin scenario-authoring platform** (see [17-admin-authoring-platform.md](17-admin-authoring-platform.md)).
> The learner-facing runtime is developed in another team's module; its hand-over point is the
> `scenario_versions` table of published, immutable scenario snapshots.

## 1. Project structure

```
backend/
├── pom.xml                     Spring Boot 4.1 parent, Java 21
├── mvnw, mvnw.cmd, .mvn/       Maven wrapper (no global Maven needed)
└── src/
    ├── main/java/am/cybersim/
    │   ├── CyberSimApplication.java
    │   ├── common/             ApiError, ApiException, GlobalExceptionHandler
    │   ├── config/             AppProperties, OpenApiConfig, ClockConfig, CORS
    │   ├── security/           SecurityConfig, JwtConfig, JwtTokenService, AuthUser, AuthUserArgumentResolver
    │   ├── auth/               AuthController, AuthService, dto/
    │   ├── user/               User, Role (ADMIN only), UserRepository, DemoDataInitializer
    │   ├── scenario/           Scenario + child entities, ScenarioVersion, ScenarioService, ScenarioMapper,
    │   │                       ScenarioDefinition(Validator), dto/
    │   ├── authoring/          AuthoringController, AuthoringService, ScenarioGeneratorService, ScenarioGraph,
    │   │                       ScenarioAnalyzer, ScenarioTestRunner, QualityScorer, ScenarioSeeder
    │   ├── scoring/            ScoringEngine, ScoreResult (reused by the test runner)
    │   └── ai/                 AiProvider, ClaudeAiProvider, MockAiProvider, AiGateway, AiPromptBuilder,
    │                           AiOutputValidator, AiInteraction, ScenarioVariationService,
    │                           TranslationService, TranslationController
    └── main/resources/
        ├── application.yml
        ├── db/migration/       Flyway V1__initial_schema.sql
        └── scenarios/          *.json — seed scenarios, also the generator templates
```

## 2. REST API boundaries

Base path `/api`. All bodies are JSON. Authentication: `Authorization: Bearer <JWT>`.
Live documentation: Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`.
`SecurityConfig` applies one URL rule: `/api/admin/**` requires `ROLE_ADMIN`, everything else under `/api/**`
requires authentication; only login, the API docs and the actuator health/info endpoints are public.
There is no self-registration — accounts are provisioned by the operator (`DemoDataInitializer`).

### Authentication
| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| POST | `/api/auth/login` | Log in, returns `{accessToken, expiresAt, user}` | 200, 400, 401 |
| GET | `/api/auth/me` | Current user | 200, 401 |

### AI (any signed-in user)
| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| POST | `/api/ai/translate` | `{text, language}` → the same text in that language; nothing is stored (ADR-12) | 200, 400 (`UNSUPPORTED_LANGUAGE`, `EMPTY_TEXT`, `TEXT_TOO_LONG`) |

### Learner module (service, `X-API-Key`, base `/api/learner`) — ADR-13

| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| GET | `/api/learner/scenarios` | Catalogue of scenarios with a published version | 200 |
| GET | `/api/learner/scenarios/{slug}` | The latest published definition of one scenario | 200, 404 |
| POST | `/api/learner/attempts` | Submit a completed attempt; the response carries the **verified** score computed here by replaying the actions | 201, 404, 409 `ATTEMPT_EXISTS` / `SCENARIO_NOT_PUBLISHED`, 422 `UNKNOWN_ACTION` |
| GET | `/api/learner/attempts/{externalId}` | A submitted attempt: verified result plus the AI review once it exists | 200, 404 |

Without a configured `LEARNER_API_KEY` these endpoints answer `503 INTEGRATION_DISABLED`; a missing or wrong
key answers `401 INVALID_API_KEY`. An admin JWT does not open this surface, and the key does not open `/api/admin/**`.

### Exams (`ADMIN`, base `/api/admin/exams`) — ADR-13

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/exams` | Submitted attempts, newest first (paged, optional `scenarioId`) |
| GET | `/api/admin/exams/{id}` | One attempt: submitted actions, verified result, AI review |
| POST | `/api/admin/exams/{id}/review` | Run (or re-run) the AI review; stored on the attempt and visible to the learner module |

### Scenario authoring (`ADMIN`, base `/api/admin/scenarios`)

CRUD:

| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| GET | `/` | All scenarios with lifecycle status, revision and published version | 200 |
| GET | `/{id}` | Scenario with its full editable definition | 200, 404 |
| POST | `/` | Create a draft from a complete `ScenarioDefinition` | 201, 409 (`SLUG_TAKEN`), 422 (`INVALID_SCENARIO`) |
| PUT | `/{id}` | Replace the draft content; a published scenario becomes a draft again (revision + 1) | 200, 400 (`SLUG_IMMUTABLE`), 409 (`SCENARIO_ARCHIVED`), 422 |
| PATCH | `/{id}/archive` | `{archived}` — archive, or restore to draft | 200 |
| DELETE | `/{id}` | Delete a never-published draft | 204, 409 (`SCENARIO_PUBLISHED` — archive instead) |

Generation and analysis:

| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| POST | `/generate` | Build a draft from a scenario type (template + optional AI polish) and store it | 201, 400 (field errors) |
| POST | `/{id}/variations` | AI variation of a scenario, validated and stored as a new draft | 201, 422 (`INVALID_VARIATION`) |
| GET | `/{id}/graph` | Dependency graph (nodes: start, actions, events, resources) | 200 |
| POST | `/{id}/validate` | Validation report (field, cross-reference and graph rules) | 200 |
| POST | `/{id}/test-run` | Correct-path and dangerous-path report; `null` while validation has errors | 200 |
| POST | `/{id}/evaluate` | Validation + tests + quality score + publishing blockers in one call | 200 |
| POST | `/evaluate` | The same for an **unsaved** definition — live feedback while editing | 200 |

Publishing and versions:

| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| POST | `/{id}/publish` | `{changeNote?}` — freeze the draft as the next immutable version | 200, 409 (`SCENARIO_ARCHIVED`), 422 (`PUBLISH_BLOCKED` with the blocker list) |
| GET | `/{id}/versions` | Published versions, newest first | 200 |
| GET | `/{id}/versions/{number}` | One version with its frozen definition and quality report | 200, 404 |
| POST | `/{id}/versions/{number}/restore` | Load a published version back into the editable draft | 200 |

`GenerateRequest`: `type` (required — `SSH_BRUTE_FORCE`, `COMPROMISED_CREDENTIALS`, `PUBLIC_STORAGE_BUCKET`) plus
optional `title`, `difficulty`, `primaryAsset`, `attackerIp`, `region`, `brief`, `useAi`; the free-text fields are
constrained with `@Pattern` (IPv4, asset and region character sets) so template substitution stays injection-free.

### Error format
Every error uses the same body (`ApiError`):

```json
{
  "timestamp": "2026-10-08T10:15:30Z",
  "status": 422,
  "error": "Unprocessable Content",
  "code": "PUBLISH_BLOCKED",
  "message": "The scenario cannot be published: Quality score 58 is below the publishing threshold of 70",
  "path": "/api/admin/scenarios/4/publish",
  "fieldErrors": []
}
```

`GlobalExceptionHandler` maps `ApiException` (status + code), Bean Validation errors (400 `VALIDATION_FAILED`
with field errors), malformed JSON and type mismatches (400), unsupported methods (405), unknown routes (404) and
unexpected exceptions (500 without internal details, full stack trace in the log). Spring Security's entry point
and access-denied handler write the same JSON body for 401/403.

## 3. Implementation notes

### 3.1 Validation strategy
The `ScenarioDefinition` body of `POST /` and `PUT /{id}` is deliberately **not** annotated with `@Valid`:
`ScenarioService` runs `ScenarioDefinitionValidator` itself so that Bean Validation violations and
cross-reference problems are reported together in **one** 422 `INVALID_SCENARIO` response with a field error per
rule. The same validator guards every entry point — seed files, the generator, the editor, AI variations and
version restore.

### 3.2 Transaction design
| Operation | Transactions | Why |
|-----------|-------------|-----|
| CRUD, graph, evaluate(id), versions | one `@Transactional` service method; DTO mapping happens inside it | `open-in-view` is disabled, so lazy children must be read before the transaction ends |
| Generate | template + optional AI call **outside** any transaction → `TransactionTemplate` only around the save | no DB connection or locks held during a slow external call |
| Update | children are cleared and the session **flushed** before re-adding | Hibernate executes inserts before orphan deletes; flushing avoids unique-key collisions between removed and re-added rows |
| Publish | one transaction; the publishing gates are **re-evaluated server-side** on the stored draft before the version row is written | a client cannot publish a draft the analysis would reject |

### 3.3 Module summary

| Module | Main classes |
|--------|-------------|
| security | `SecurityConfig`, `JwtConfig`, `JwtTokenService`, `AuthUser`, `AuthUserArgumentResolver` |
| auth / user | `AuthController`, `AuthService`, `User`, `Role` (ADMIN only), `UserRepository`, `DemoDataInitializer` |
| scenario | `Scenario` (+5 child entities), `ScenarioVersion`, `ScenarioDefinition`, `ScenarioDefinitionValidator`, `ScenarioMapper`, `ScenarioService` |
| authoring | `AuthoringController`, `AuthoringService`, `ScenarioGeneratorService`, `ScenarioGraph`, `ScenarioAnalyzer`, `ScenarioTestRunner`, `QualityScorer`, `ScenarioSeeder` |
| scoring | `ScoringEngine`, `ScoreResult` — the deterministic rules the test runner replays and the learner module will apply |
| ai | `AiProvider`, `ClaudeAiProvider`, `MockAiProvider`, `AiConfig`, `AiGateway`, `AiPromptBuilder`, `AiOutputValidator`, `AiInteraction`, `ScenarioVariationService`, `TranslationService`, `TranslationController` |

### 3.4 Start-up data
`ScenarioSeeder` imports `resources/scenarios/*.json` when the slug does not exist yet and **publishes each seed as
version 1** (a seed that fails the publishing gate stays a draft); the same files double as the generator's
templates. An invalid seed file stops the start-up (fail fast). `DemoDataInitializer` creates one admin account
only when its password is provided through environment variables.
