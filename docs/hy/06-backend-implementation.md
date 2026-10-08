> Թարգմանություն՝ [English version](../en/06-backend-implementation.md)

# 06 — Սերվերային մասի (backend) իրականացում

> Ծավալը՝ **ադմինիստրատորի սցենարների հեղինակման հարթակ** (տե՛ս [17-admin-authoring-platform.md](17-admin-authoring-platform.md))։
> Ուսումնառողի (trainee) համար նախատեսված միջավայրը մշակվում է թիմի մեկ այլ անդամի մոդուլում. փոխանցման կետը
> հրապարակված, անփոփոխ սցենարային պատկերների `scenario_versions` աղյուսակն է։

## 1. Նախագծի կառուցվածքը

```
backend/
├── pom.xml                     Spring Boot 4.1 parent, Java 21
├── mvnw, mvnw.cmd, .mvn/       Maven wrapper (գլոբալ Maven պետք չէ)
└── src/
    ├── main/java/am/cybersim/
    │   ├── CyberSimApplication.java
    │   ├── common/             ApiError, ApiException, GlobalExceptionHandler
    │   ├── config/             AppProperties, OpenApiConfig, ClockConfig, CORS
    │   ├── security/           SecurityConfig, JwtConfig, JwtTokenService, AuthUser, AuthUserArgumentResolver
    │   ├── auth/               AuthController, AuthService, dto/
    │   ├── user/               User, Role (միայն ADMIN), UserRepository, DemoDataInitializer
    │   ├── scenario/           Scenario + ենթաէնտիտիներ, ScenarioVersion, ScenarioService, ScenarioMapper,
    │   │                       ScenarioDefinition(Validator), dto/
    │   ├── authoring/          AuthoringController, AuthoringService, ScenarioGeneratorService, ScenarioGraph,
    │   │                       ScenarioAnalyzer, ScenarioTestRunner, QualityScorer, ScenarioSeeder
    │   ├── scoring/            ScoringEngine, ScoreResult (վերաօգտագործվում է թեստերի գործարկիչի կողմից)
    │   └── ai/                 AiProvider, ClaudeAiProvider, MockAiProvider, AiGateway, AiPromptBuilder,
    │                           AiOutputValidator, AiInteraction, ScenarioVariationService,
    │                           TranslationService, TranslationController
    └── main/resources/
        ├── application.yml
        ├── db/migration/       Flyway V1__initial_schema.sql
        └── scenarios/          *.json — seed սցենարներ, միաժամանակ գեներատորի ձևանմուշներ
```

## 2. REST API-ի սահմանները

Բազային ուղի՝ `/api`։ Բոլոր մարմինները JSON ձևաչափով են։ Նույնականացում՝ `Authorization: Bearer <JWT>`։
Կենդանի փաստաթղթավորում՝ Swagger UI `/swagger-ui.html` հասցեով, OpenAPI JSON՝ `/v3/api-docs`։
`SecurityConfig`-ը կիրառում է մեկ URL կանոն. `/api/admin/**`-ը պահանջում է `ROLE_ADMIN`, `/api/**`-ի մնացած ամեն
ինչը՝ նույնականացում. հանրային են միայն մուտքը, API փաստաթղթերը և actuator health/info վերջնակետերը։
Ինքնագրանցում չկա — հաշիվները ստեղծում է օպերատորը (`DemoDataInitializer`)։

### Նույնականացում
| Մեթոդ | Ուղի | Նկարագրություն | Պատասխաններ |
|--------|------|-------------|-----------|
| POST | `/api/auth/login` | Մուտք, վերադարձնում է `{accessToken, expiresAt, user}` | 200, 400, 401 |
| GET | `/api/auth/me` | Ընթացիկ օգտատերը | 200, 401 |

### AI (ցանկացած մուտք գործած օգտատեր)
| Մեթոդ | Ուղի | Նկարագրություն | Պատասխաններ |
|--------|------|-------------|-----------|
| POST | `/api/ai/translate` | `{text, language}` → նույն տեքստը տվյալ լեզվով. ոչինչ չի պահպանվում (ADR-12) | 200, 400 (`UNSUPPORTED_LANGUAGE`, `EMPTY_TEXT`, `TEXT_TOO_LONG`) |

### Սովորողի մոդուլ (ծառայություն, `X-API-Key`, բազան՝ `/api/learner`) — ADR-13

| Մեթոդ | Ուղի | Նկարագրություն | Պատասխաններ |
|--------|------|-------------|-----------|
| GET | `/api/learner/scenarios` | Հրապարակված տարբերակ ունեցող սցենարների կատալոգ | 200 |
| GET | `/api/learner/scenarios/{slug}` | Մեկ սցենարի վերջին հրապարակված սահմանումը | 200, 404 |
| POST | `/api/learner/attempts` | Ուղարկել ավարտված փորձ. պատասխանը կրում է **ստուգված** միավորը, որը հաշվարկվում է այստեղ՝ գործողությունների վերարտադրմամբ | 201, 404, 409 `ATTEMPT_EXISTS` / `SCENARIO_NOT_PUBLISHED`, 422 `UNKNOWN_ACTION` |
| GET | `/api/learner/attempts/{externalId}` | Ուղարկված փորձը՝ ստուգված արդյունքով և AI գնահատմամբ, երբ այն կա | 200, 404 |

Չկարգավորված `LEARNER_API_KEY`-ի դեպքում այս վերջնակետերը պատասխանում են `503 INTEGRATION_DISABLED`,
բացակայող կամ սխալ բանալու դեպքում՝ `401 INVALID_API_KEY`։ Ադմինի JWT-ն չի բացում այս մակերեսը, իսկ բանալին
չի բացում `/api/admin/**`-ը։

### Քննություններ (`ADMIN`, բազան՝ `/api/admin/exams`) — ADR-13

| Մեթոդ | Ուղի | Նկարագրություն |
|--------|------|-------------|
| GET | `/api/admin/exams` | Ուղարկված փորձերը՝ նորերը սկզբում (էջավորված, ընտրովի `scenarioId`) |
| GET | `/api/admin/exams/{id}` | Մեկ փորձ՝ ուղարկված գործողություններ, ստուգված արդյունք, AI գնահատում |
| POST | `/api/admin/exams/{id}/review` | Կատարել (կամ կրկնել) AI գնահատումը. պահպանվում է փորձի վրա և տեսանելի է սովորողի մոդուլին |

### Սցենարների հեղինակում (`ADMIN`, բազան՝ `/api/admin/scenarios`)

CRUD.

| Մեթոդ | Ուղի | Նկարագրություն | Պատասխաններ |
|--------|------|-------------|-----------|
| GET | `/` | Բոլոր սցենարները՝ կարգավիճակով, խմբագրման համարով (revision) և հրապարակված տարբերակով | 200 |
| GET | `/{id}` | Սցենարն իր ամբողջական խմբագրելի սահմանմամբ | 200, 404 |
| POST | `/` | Ստեղծել սևագիր ամբողջական `ScenarioDefinition`-ից | 201, 409 (`SLUG_TAKEN`), 422 (`INVALID_SCENARIO`) |
| PUT | `/{id}` | Փոխարինել սևագրի բովանդակությունը. հրապարակված սցենարը կրկին դառնում է սևագիր (revision + 1) | 200, 400 (`SLUG_IMMUTABLE`), 409 (`SCENARIO_ARCHIVED`), 422 |
| PATCH | `/{id}/archive` | `{archived}` — արխիվացնել կամ վերադարձնել սևագիր վիճակին | 200 |
| DELETE | `/{id}` | Ջնջել երբեք չհրապարակված սևագիրը | 204, 409 (`SCENARIO_PUBLISHED` — փոխարենը արխիվացրեք) |

Գեներացում և վերլուծություն.

| Մեթոդ | Ուղի | Նկարագրություն | Պատասխաններ |
|--------|------|-------------|-----------|
| POST | `/generate` | Կառուցել սևագիր սցենարի տեսակից (ձևանմուշ + ըստ ցանկության AI հղկում) և պահել այն | 201, 400 (դաշտային սխալներ) |
| POST | `/{id}/variations` | Սցենարի AI վարիացիա՝ վավերացված և պահված որպես նոր սևագիր | 201, 422 (`INVALID_VARIATION`) |
| GET | `/{id}/graph` | Կախվածությունների գրաֆ (հանգույցներ՝ start, գործողություններ, իրադարձություններ, ռեսուրսներ) | 200 |
| POST | `/{id}/validate` | Վավերացման հաշվետվություն (դաշտային, խաչաձև հղումների և գրաֆային կանոններ) | 200 |
| POST | `/{id}/test-run` | Ճիշտ և վտանգավոր ուղիների հաշվետվություն. `null`, քանի դեռ վավերացումը սխալներ ունի | 200 |
| POST | `/{id}/evaluate` | Վավերացում + թեստեր + որակի միավոր + հրապարակման արգելքները մեկ կանչով | 200 |
| POST | `/evaluate` | Նույնը՝ **չպահպանված** սահմանման համար. կենդանի արձագանք խմբագրելիս | 200 |

Հրապարակում և տարբերակներ.

| Մեթոդ | Ուղի | Նկարագրություն | Պատասխաններ |
|--------|------|-------------|-----------|
| POST | `/{id}/publish` | `{changeNote?}` — սառեցնել սևագիրը որպես հաջորդ անփոփոխ տարբերակ | 200, 409 (`SCENARIO_ARCHIVED`), 422 (`PUBLISH_BLOCKED`՝ արգելքների ցանկով) |
| GET | `/{id}/versions` | Հրապարակված տարբերակները՝ նորագույնը սկզբում | 200 |
| GET | `/{id}/versions/{number}` | Մեկ տարբերակ՝ սառեցված սահմանմամբ և որակի հաշվետվությամբ | 200, 404 |
| POST | `/{id}/versions/{number}/restore` | Բեռնել հրապարակված տարբերակը ետ՝ խմբագրելի սևագրի մեջ | 200 |

`GenerateRequest`՝ `type` (պարտադիր — `SSH_BRUTE_FORCE`, `COMPROMISED_CREDENTIALS`, `PUBLIC_STORAGE_BUCKET`), գումարած
ոչ պարտադիր `title`, `difficulty`, `primaryAsset`, `attackerIp`, `region`, `brief`, `useAi`. ազատ տեքստային դաշտերը
սահմանափակված են `@Pattern`-ով (IPv4, ակտիվի և տարածաշրջանի նիշերի բազմություններ), որպեսզի ձևանմուշում տեղադրումը
մնա ներարկումից զերծ։

### Սխալների ձևաչափը
Յուրաքանչյուր սխալ օգտագործում է նույն մարմինը (`ApiError`).

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

`GlobalExceptionHandler`-ը քարտեզագրում է `ApiException`-ը (կարգավիճակ + կոդ), Bean Validation սխալները (400
`VALIDATION_FAILED`՝ դաշտային սխալներով), սխալ ձևավորված JSON-ը և տիպերի անհամապատասխանությունները (400),
չաջակցվող մեթոդները (405), անհայտ երթուղիները (404) և չսպասված բացառությունները (500՝ առանց ներքին մանրամասների,
ամբողջական stack trace-ը մատյանում)։ Spring Security-ի entry point-ը և access-denied մշակիչը 401/403-ի համար
գրում են նույն JSON մարմինը։

## 3. Իրականացման նշումներ

### 3.1 Վավերացման ռազմավարությունը
`POST /`-ի և `PUT /{id}`-ի `ScenarioDefinition` մարմինը միտումնավոր **նշված չէ** `@Valid`-ով.
`ScenarioService`-ը ինքն է գործարկում `ScenarioDefinitionValidator`-ը, որպեսզի Bean Validation խախտումները և
խաչաձև հղումների խնդիրները զեկուցվեն միասին՝ **մեկ** 422 `INVALID_SCENARIO` պատասխանով, յուրաքանչյուր կանոնի
համար առանձին դաշտային սխալով։ Նույն վավերացնողը պաշտպանում է բոլոր մուտքի կետերը — seed ֆայլեր, գեներատոր,
խմբագրիչ, AI վարիացիաներ և տարբերակի վերականգնում։

### 3.2 Տրանզակցիաների նախագծում
| Գործողություն | Տրանզակցիաներ | Ինչու |
|-----------|-------------|-----|
| CRUD, graph, evaluate(id), versions | մեկ `@Transactional` սերվիսային մեթոդ. DTO-ի քարտեզագրումը կատարվում է դրա ներսում | `open-in-view`-ն անջատված է, ուստի lazy ենթատողերը պետք է կարդացվեն մինչև տրանզակցիայի ավարտը |
| Generate | ձևանմուշ + ըստ ցանկության AI կանչ՝ ցանկացած տրանզակցիայից **դուրս** → `TransactionTemplate` միայն պահպանման շուրջ | դանդաղ արտաքին կանչի ընթացքում DB կապ կամ կողպեքներ չեն պահվում |
| Update | ենթատողերը մաքրվում են, և սեսիան **flush** է արվում նախքան վերաավելացումը | Hibernate-ը insert-ները կատարում է orphan ջնջումներից առաջ. flush-ը կանխում է հեռացված ու վերաավելացված տողերի եզակի բանալիների բախումը |
| Publish | մեկ տրանզակցիա. հրապարակման պայմանները **կրկին հաշվարկվում են սերվերի կողմում** պահված սևագրի վրա՝ նախքան տարբերակի տողը գրվելը | հաճախորդը չի կարող հրապարակել մի սևագիր, որը վերլուծությունը կմերժեր |

### 3.3 Մոդուլների ամփոփում

| Մոդուլ | Հիմնական դասեր |
|--------|-------------|
| security | `SecurityConfig`, `JwtConfig`, `JwtTokenService`, `AuthUser`, `AuthUserArgumentResolver` |
| auth / user | `AuthController`, `AuthService`, `User`, `Role` (միայն ADMIN), `UserRepository`, `DemoDataInitializer` |
| scenario | `Scenario` (+5 ենթաէնտիտի), `ScenarioVersion`, `ScenarioDefinition`, `ScenarioDefinitionValidator`, `ScenarioMapper`, `ScenarioService` |
| authoring | `AuthoringController`, `AuthoringService`, `ScenarioGeneratorService`, `ScenarioGraph`, `ScenarioAnalyzer`, `ScenarioTestRunner`, `QualityScorer`, `ScenarioSeeder` |
| scoring | `ScoringEngine`, `ScoreResult` — դետերմինիստիկ կանոնները, որոնք վերարտադրում է թեստերի գործարկիչը և որոնք կկիրառի ուսումնառողի մոդուլը |
| ai | `AiProvider`, `ClaudeAiProvider`, `MockAiProvider`, `AiConfig`, `AiGateway`, `AiPromptBuilder`, `AiOutputValidator`, `AiInteraction`, `ScenarioVariationService`, `TranslationService`, `TranslationController` |

### 3.4 Գործարկման տվյալներ
`ScenarioSeeder`-ը ներմուծում է `resources/scenarios/*.json` ֆայլերը, երբ տվյալ slug-ը դեռ գոյություն չունի, և
**յուրաքանչյուր seed հրապարակում է որպես տարբերակ 1** (հրապարակման պայմանը չանցնող seed-ը մնում է սևագիր).
նույն ֆայլերը ծառայում են նաև որպես գեներատորի ձևանմուշներ։ Անվավեր seed ֆայլը կանգնեցնում է գործարկումը
(fail fast)։ `DemoDataInitializer`-ը ստեղծում է մեկ ադմինիստրատորի հաշիվ միայն այն դեպքում, երբ նրա գաղտնաբառը
տրված է միջավայրի փոփոխականներով։
