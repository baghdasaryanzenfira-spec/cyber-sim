# 06 — Backend Implementation

## 1. Project structure

```
backend/
├── pom.xml                     Spring Boot 4.1 parent, Java 21
├── mvnw, mvnw.cmd, .mvn/       Maven wrapper (no global Maven needed)
└── src/
    ├── main/java/am/cybersim/
    │   ├── CyberSimApplication.java
    │   ├── common/             ApiError, GlobalExceptionHandler, domain exceptions
    │   ├── config/             AppProperties, OpenApiConfig, ClockConfig, CORS
    │   ├── security/           SecurityConfig, JwtTokenService, CurrentUser
    │   ├── auth/               AuthController, AuthService, dto/
    │   ├── user/               User, Role, UserRepository, UserService, DemoDataInitializer
    │   ├── scenario/           Scenario + child entities, ScenarioService, ScenarioDefinition(Validator), ScenarioSeeder
    │   ├── simulation/         Simulation + child entities, SimulationEngine, SimulationStateMachine, SimulationService
    │   ├── scoring/            ScoringEngine, ScoreResult
    │   ├── ai/                 AiProvider, ClaudeAiProvider, MockAiProvider, AiAssistantService, AiPromptBuilder, AiOutputValidator
    │   ├── progress/           ProgressService, ProgressController
    │   ├── analytics/          AnalyticsService
    │   └── admin/              Admin* controllers
    └── main/resources/
        ├── application.yml
        ├── db/migration/       Flyway V1__..., V2__...
        └── scenarios/          *.json seed scenario definitions
```

## 2. REST API boundaries

Base path `/api`. All bodies are JSON. Authentication: `Authorization: Bearer <JWT>`.
Live documentation: Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`.

### Authentication (public)
| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| POST | `/api/auth/register` | Register a student | 201, 400, 409 (e-mail taken) |
| POST | `/api/auth/login` | Log in, returns `{accessToken, expiresAt, user}` | 200, 400, 401 |
| GET | `/api/auth/me` | Current user | 200, 401 |

### Scenarios (student)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/scenarios` | Active scenarios (catalogue) |
| GET | `/api/scenarios/{id}` | Briefing: description, objectives, difficulty — no solution data |

### Simulations (student, owner only)
| Method | Path | Description | Responses |
|--------|------|-------------|-----------|
| POST | `/api/simulations` | `{scenarioId}` → create (or resume active) | 201 / 200, 404 |
| GET | `/api/simulations` | My simulation history | 200 |
| GET | `/api/simulations/{id}` | Full current state (resources, timeline, actions catalogue, performed actions) | 200, 403, 404 |
| POST | `/api/simulations/{id}/start` | `CREATED → RUNNING` | 200, 409 |
| POST | `/api/simulations/{id}/actions` | `{actionKey, note?}` perform action | 200, 400, 409 |
| POST | `/api/simulations/{id}/complete` | Finish, score, AI feedback | 200, 409 |
| POST | `/api/simulations/{id}/abandon` | Abandon | 200, 409 |
| GET | `/api/simulations/{id}/result` | Score, breakdown, missed actions, explanation, feedback | 200, 409 (not completed) |

### AI assistant (student, owner only)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/simulations/{id}/assistant/hint` | Contextual hint (counts towards hint penalty) |
| POST | `/api/simulations/{id}/assistant/ask` | `{question}` free question |
| GET | `/api/simulations/{id}/assistant/messages` | Assistant conversation history |

### Progress (student)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/progress/me` | Totals, per-category stats, recent attempts, AI recommendations |

### Admin (`ADMIN` role)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/users` | Users with attempt statistics (paged) |
| GET | `/api/admin/users/{id}` | User detail + progress |
| PATCH | `/api/admin/users/{id}/status` | `{enabled}` |
| GET | `/api/admin/scenarios` | All scenarios incl. inactive |
| GET | `/api/admin/scenarios/{id}` | Full `ScenarioDefinition` (incl. solution) |
| POST | `/api/admin/scenarios` | Create from `ScenarioDefinition` |
| PUT | `/api/admin/scenarios/{id}` | Replace definition (version++) |
| PATCH | `/api/admin/scenarios/{id}/status` | `{active}` |
| POST | `/api/admin/scenarios/{id}/variations` | AI-generated, validated, inactive draft |
| GET | `/api/admin/simulations` | Attempts, filter by user/scenario/status (paged) |
| GET | `/api/admin/simulations/{id}` | Attempt detail: actions, timeline, result, AI interactions |
| GET | `/api/admin/analytics/overview` | Totals, average scores per scenario, attempts over time, AI usage |
| GET | `/api/admin/analytics/mistakes` | Most common harmful actions and most often missed expected actions |

### Error format
Every error uses the same body (`ApiError`):

```json
{
  "timestamp": "2026-10-06T10:15:30Z",
  "status": 409,
  "error": "Conflict",
  "code": "INVALID_STATE_TRANSITION",
  "message": "Cannot perform actions on a simulation in status COMPLETED",
  "path": "/api/simulations/42/actions",
  "fieldErrors": []
}
```

Additional endpoint implemented during Phase 5: `PUT /api/simulations/{id}/events/{eventId}/flag` `{flagged}` —
add/remove a log entry on the student's evidence board.

## 3. Implementation notes

### 3.1 Transaction design
| Operation | Transactions | Why |
|-----------|-------------|-----|
| Normal reads/writes | one `@Transactional` service method | standard unit of work |
| Complete simulation | TX1 (state + score + result + AI snapshot) → AI call (no TX) → TX2 (attach feedback) | no DB connection/locks held during a slow external call; the score is saved even if the AI fails |
| Hint / question | TX1 (ownership, state, snapshot) → AI call → TX2 (hint counter) | same; the penalty is applied only when a hint was delivered |
| Concurrency | `@Version lock_version` on `simulations`, partial unique index for one active attempt | double clicks or parallel tabs cannot corrupt the state |

`open-in-view` is disabled, so every DTO is mapped inside the service transaction. Methods that need a transaction
inside the same class use `TransactionTemplate` explicitly (self-invocation of `@Transactional` would bypass the
proxy — see implementation log Step 5).

### 3.2 Error handling
`GlobalExceptionHandler` maps `ApiException` (status + code), Bean Validation errors (400 `VALIDATION_FAILED` with
field errors), malformed JSON (400), unknown routes (404) and unexpected exceptions (500 without internal details,
full stack trace in the log). Spring Security's entry point and access-denied handler write the same JSON body for
401/403.

### 3.3 Module summary

| Module | Main classes |
|--------|-------------|
| security | `SecurityConfig`, `JwtConfig`, `JwtTokenService`, `AuthUser`, `AuthUserArgumentResolver` |
| auth / user | `AuthController`, `AuthService`, `User`, `Role`, `UserRepository`, `DemoDataInitializer` |
| scenario | `Scenario` (+5 child entities), `ScenarioDefinition`, `ScenarioDefinitionValidator`, `ScenarioMapper`, `ScenarioService`, `ScenarioSeeder`, `ScenarioController` |
| simulation | `Simulation` (+4 child entities, `SimulationResult`), `SimulationStateMachine`, `SimulationEngine`, `SimulationService`, `SimulationAssistantService`, `SimulationSnapshotFactory`, `SimulationMapper`, `SimulationController` |
| scoring | `ScoringEngine`, `ScoreResult` |
| ai | `AiProvider`, `ClaudeAiProvider`, `MockAiProvider`, `AiConfig`, `AiGateway`, `AiPromptBuilder`, `AiOutputValidator`, `AiInteraction`, `TutorService`, `ScenarioVariationService` |
| progress | `ProgressService`, `ProgressController`, `ProgressStats` |
| analytics | `AnalyticsService` (SQL via `JdbcClient`) |
| admin | `AdminService`, `AdminController` |

### 3.4 Seed data
`ScenarioSeeder` imports `resources/scenarios/*.json` on start-up if the slug does not exist (validated like admin
input; an invalid file stops the start-up). `DemoDataInitializer` creates demo accounts only when their passwords are
provided through environment variables.
