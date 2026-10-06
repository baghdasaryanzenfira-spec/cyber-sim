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

*(Implementation details for each module are added below as the phases are completed.)*
