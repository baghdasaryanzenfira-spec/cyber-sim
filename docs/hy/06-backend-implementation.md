> Թարգմանություն՝ [English version](../en/06-backend-implementation.md)

# 06 — Սերվերային մասի (backend) իրականացում

## 1. Նախագծի կառուցվածքը

```
backend/
├── pom.xml                     Spring Boot 4.1 parent, Java 21
├── mvnw, mvnw.cmd, .mvn/       Maven wrapper (no global Maven needed)
└── src/
    ├── main/java/am/cybersim/
    │   ├── CyberSimApplication.java
    │   ├── common/             ApiError, GlobalExceptionHandler, domain exceptions
    │   ├── config/             AppProperties, OpenApiConfig, ClockConfig, CORS
    │   ├── security/           SecurityConfig, JwtConfig, JwtTokenService, AuthUser, AuthUserArgumentResolver
    │   ├── auth/               AuthController, AuthService, dto/
    │   ├── user/               User, Role, UserRepository, DemoDataInitializer
    │   ├── scenario/           Scenario + child entities, ScenarioService, ScenarioDefinition(Validator), ScenarioSeeder
    │   ├── simulation/         Simulation + child entities, SimulationEngine, SimulationStateMachine, SimulationService, SimulationAssistantService
    │   ├── scoring/            ScoringEngine, ScoreResult
    │   ├── ai/                 AiProvider, ClaudeAiProvider, MockAiProvider, AiGateway, TutorService, ScenarioVariationService, TranslationService, AiPromptBuilder, AiOutputValidator
    │   ├── progress/           ProgressService, ProgressController
    │   ├── analytics/          AnalyticsService
    │   └── admin/              AdminController, AdminService
    └── main/resources/
        ├── application.yml
        ├── db/migration/       Flyway V1__initial_schema.sql, V2__ai_translation_task.sql
        └── scenarios/          *.json seed scenario definitions
```

## 2. REST API-ի սահմանները

Բազային ուղի՝ `/api`։ Բոլոր հարցումների և պատասխանների մարմինները JSON ձևաչափով են։ Նույնականացում՝ `Authorization: Bearer <JWT>`։
Կենդանի փաստաթղթավորում՝ Swagger UI `/swagger-ui.html` հասցեով, OpenAPI JSON՝ `/v3/api-docs` հասցեով։

### Նույնականացում (հանրային)
| Մեթոդ | Ուղի | Նկարագրություն | Պատասխաններ |
|--------|------|-------------|-----------|
| POST | `/api/auth/register` | Ուսանողի գրանցում | 201, 400, 409 (էլ. փոստն արդեն զբաղված է) |
| POST | `/api/auth/login` | Մուտք համակարգ, վերադարձնում է `{accessToken, expiresAt, user}` | 200, 400, 401 |
| GET | `/api/auth/me` | Ընթացիկ օգտատերը | 200, 401 |

### Սցենարներ (ուսանող)
| Մեթոդ | Ուղի | Նկարագրություն |
|--------|------|-------------|
| GET | `/api/scenarios` | Ակտիվ սցենարներ (կատալոգ) |
| GET | `/api/scenarios/{id}` | Ներածական տեղեկանք (briefing)՝ նկարագրություն, նպատակներ, բարդություն — առանց լուծման տվյալների |

### Սիմուլյացիաներ (ուսանող, միայն սեփականատերը)
| Մեթոդ | Ուղի | Նկարագրություն | Պատասխաններ |
|--------|------|-------------|-----------|
| POST | `/api/simulations` | `{scenarioId}` → ստեղծել (կամ վերսկսել ակտիվը) | 201 / 200, 404 |
| GET | `/api/simulations` | Իմ սիմուլյացիաների պատմությունը | 200 |
| GET | `/api/simulations/{id}` | Ամբողջական ընթացիկ վիճակը (ռեսուրսներ, ժամանակագրություն, գործողությունների կատալոգ, կատարված գործողություններ) | 200, 403, 404 |
| POST | `/api/simulations/{id}/start` | `CREATED → RUNNING` | 200, 409 |
| POST | `/api/simulations/{id}/actions` | `{actionKey, note?}` գործողության կատարում | 200, 400, 409 |
| POST | `/api/simulations/{id}/complete` | Ավարտ, գնահատում, AI հետադարձ կապ | 200, 409 |
| POST | `/api/simulations/{id}/abandon` | Լքել սիմուլյացիան | 200, 409 |
| GET | `/api/simulations/{id}/result` | Միավոր, մանրամասն բաշխում, բաց թողնված գործողություններ, բացատրություն, հետադարձ կապ | 200, 409 (ավարտված չէ) |

### AI օգնական (ուսանող, միայն սեփականատերը)
| Մեթոդ | Ուղի | Նկարագրություն |
|--------|------|-------------|
| POST | `/api/simulations/{id}/assistant/hint` | Համատեքստային հուշում (հաշվվում է հուշումների տուգանքի մեջ) |
| POST | `/api/simulations/{id}/assistant/ask` | `{question}` ազատ հարց |
| GET | `/api/simulations/{id}/assistant/messages` | Օգնականի հետ զրույցի պատմությունը |

### AI (ցանկացած մուտք գործած օգտատեր)
| Մեթոդ | Ուղի | Նկարագրություն |
|--------|------|-------------|
| POST | `/api/ai/translate` | `{text, language}` → նույն տեքստը տվյալ լեզվով. ոչինչ չի պահպանվում (ADR-12) |

### Առաջընթաց (ուսանող)
| Մեթոդ | Ուղի | Նկարագրություն |
|--------|------|-------------|
| GET | `/api/progress/me` | Ընդհանուր ցուցանիշներ, ըստ կատեգորիաների վիճակագրություն, ըստ սցենարների առաջընթաց, միավորների պատմություն, վերջին փորձեր |
| GET | `/api/progress/me/recommendations` | AI ուսումնական առաջարկություններ՝ իմ պատմության հիման վրա |

### Ադմինիստրատոր (`ADMIN` դեր)
| Մեթոդ | Ուղի | Նկարագրություն |
|--------|------|-------------|
| GET | `/api/admin/users` | Օգտատերեր՝ փորձերի վիճակագրությամբ |
| GET | `/api/admin/users/{id}` | Օգտատիրոջ մանրամասներ + առաջընթաց |
| PATCH | `/api/admin/users/{id}/status` | `{enabled}` |
| GET | `/api/admin/scenarios` | Բոլոր սցենարները, ներառյալ ոչ ակտիվները |
| GET | `/api/admin/scenarios/{id}` | Ամբողջական `ScenarioDefinition` (ներառյալ լուծումը) |
| POST | `/api/admin/scenarios` | Ստեղծել `ScenarioDefinition`-ից |
| PUT | `/api/admin/scenarios/{id}` | Փոխարինել սահմանումը (version++) |
| PATCH | `/api/admin/scenarios/{id}/status` | `{active}` |
| POST | `/api/admin/scenarios/{id}/variations` | AI-ով գեներացված, վավերացված, ոչ ակտիվ սևագիր |
| GET | `/api/admin/simulations` | Փորձեր՝ զտում ըստ օգտատիրոջ/սցենարի/կարգավիճակի (էջավորված) |
| GET | `/api/admin/simulations/{id}` | Փորձի մանրամասներ՝ գործողություններ, ժամանակագրություն, արդյունք, AI փոխազդեցություններ |
| GET | `/api/admin/analytics/overview` | Ընդհանուր ցուցանիշներ, միջին միավորներ ըստ սցենարների, փորձերն ըստ ժամանակի, AI-ի օգտագործում |
| GET | `/api/admin/analytics/mistakes` | Ամենատարածված վնասակար գործողությունները և ամենահաճախ բաց թողնված սպասվող գործողությունները |

### Սխալների ձևաչափը
Յուրաքանչյուր սխալ օգտագործում է նույն մարմինը (`ApiError`).

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

Փուլ 5-ի ընթացքում իրականացված լրացուցիչ վերջնակետ (endpoint)՝ `PUT /api/simulations/{id}/events/{eventId}/flag` `{flagged}` —
ավելացնում/հեռացնում է մատյանի գրառումը ուսանողի ապացույցների տախտակից (evidence board)։

## 3. Իրականացման նշումներ

### 3.1 Տրանզակցիաների նախագծում
| Գործողություն | Տրանզակցիաներ | Ինչու |
|-----------|-------------|-----|
| Սովորական ընթերցում/գրառում | մեկ `@Transactional` սերվիսային մեթոդ | աշխատանքի ստանդարտ միավոր (unit of work) |
| Սիմուլյացիայի ավարտ | TX1 (վիճակ + միավոր + արդյունք + AI snapshot) → AI կանչ (առանց TX) → TX2 (հետադարձ կապի կցում) | դանդաղ արտաքին կանչի ընթացքում DB կապ/կողպեքներ չեն պահվում. միավորը պահպանվում է, նույնիսկ եթե AI-ը ձախողվի |
| Հուշում / հարց | TX1 (սեփականության ստուգում, վիճակ, snapshot) → AI կանչ → TX2 (հուշումների հաշվիչ) | նույնը. տուգանքը կիրառվում է միայն այն դեպքում, երբ հուշումը տրամադրվել է |
| Զուգահեռություն | `@Version lock_version` `simulations`-ում, մասնակի եզակի ինդեքս (partial unique index)՝ մեկ ակտիվ փորձի համար | կրկնակի սեղմումները կամ զուգահեռ ներդիրները չեն կարող վնասել վիճակը |

`open-in-view`-ն անջատված է, ուստի յուրաքանչյուր DTO քարտեզագրվում է սերվիսային տրանզակցիայի ներսում։ Այն մեթոդները, որոնց
անհրաժեշտ է տրանզակցիա նույն դասի ներսում, բացահայտորեն օգտագործում են `TransactionTemplate` (`@Transactional`-ի ինքնականչը
կշրջանցեր proxy-ն — տե՛ս իրականացման մատյանի Քայլ 5-ը)։

### 3.2 Սխալների մշակում
`GlobalExceptionHandler`-ը քարտեզագրում է `ApiException`-ը (կարգավիճակ + կոդ), Bean Validation սխալները (400 `VALIDATION_FAILED`՝
դաշտերի սխալներով), սխալ ձևավորված JSON-ը (400), անհայտ երթուղիները (404) և չսպասված բացառությունները (500՝ առանց ներքին
մանրամասների, ստեկի ամբողջական հետքը՝ մատյանում)։ Spring Security-ի մուտքի կետը (entry point) և մուտքի մերժման մշակիչը (access-denied handler)
401/403-ի համար գրում են նույն JSON մարմինը։

### 3.3 Մոդուլների ամփոփում

| Մոդուլ | Հիմնական դասեր |
|--------|-------------|
| security | `SecurityConfig`, `JwtConfig`, `JwtTokenService`, `AuthUser`, `AuthUserArgumentResolver` |
| auth / user | `AuthController`, `AuthService`, `User`, `Role`, `UserRepository`, `DemoDataInitializer` |
| scenario | `Scenario` (+5 դուստր էնտիտի), `ScenarioDefinition`, `ScenarioDefinitionValidator`, `ScenarioMapper`, `ScenarioService`, `ScenarioSeeder`, `ScenarioController` |
| simulation | `Simulation` (+4 դուստր էնտիտի, `SimulationResult`), `SimulationStateMachine`, `SimulationEngine`, `SimulationService`, `SimulationAssistantService`, `SimulationSnapshotFactory`, `SimulationMapper`, `SimulationController` |
| scoring | `ScoringEngine`, `ScoreResult` |
| ai | `AiProvider`, `ClaudeAiProvider`, `MockAiProvider`, `AiConfig`, `AiGateway`, `AiPromptBuilder`, `AiOutputValidator`, `AiInteraction`, `TutorService`, `ScenarioVariationService` |
| progress | `ProgressService`, `ProgressController`, `ProgressStats` |
| analytics | `AnalyticsService` (SQL՝ `JdbcClient`-ի միջոցով) |
| admin | `AdminService`, `AdminController` |

### 3.4 Սկզբնական տվյալներ (seed data)
`ScenarioSeeder`-ը գործարկման ժամանակ ներմուծում է `resources/scenarios/*.json` ֆայլերը, եթե համապատասխան slug-ը գոյություն չունի (վավերացվում են
ադմինիստրատորի մուտքագրման նման. անվավեր ֆայլը կանգնեցնում է գործարկումը)։ `DemoDataInitializer`-ը ստեղծում է ցուցադրական հաշիվներ միայն այն
դեպքում, երբ դրանց գաղտնաբառերը տրամադրված են միջավայրի փոփոխականների միջոցով։
