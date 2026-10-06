> Թարգմանություն՝ [English version](../en/07-frontend-implementation.md)

# 07 — Հաճախորդային մասի (frontend) իրականացում

## 1. Կառուցվածքը (պլանավորված Փուլ 0-ում)

```
frontend/
├── index.html
├── vite.config.ts          dev proxy /api → http://localhost:8080
├── Dockerfile              build with Node, serve with nginx (+ /api reverse proxy)
└── src/
    ├── main.tsx            React root, ThemeProvider, Router
    ├── theme.ts            Dark "SOC" theme
    ├── api/                axios client (JWT interceptor), typed API functions, types.ts
    ├── auth/               AuthContext, ProtectedRoute, RoleRoute
    ├── components/         Layout, StatusChip, SeverityChip, ScoreGauge, ...
    └── pages/
        ├── LoginPage, RegisterPage
        ├── DashboardPage, ScenarioListPage, ScenarioDetailPage
        ├── simulation/     ActiveSimulationPage + panels (Resources, Logs, Evidence, Actions, Timeline, Assistant)
        ├── ResultPage, HistoryPage (progress)
        └── admin/          AdminDashboard, AdminUsers, AdminScenarios, ScenarioEditor, AdminAttempts, AdminAnalytics
```

## 2. Հիմնական որոշումներ

| Որոշում | Ինչու | Այլընտրանք |
|----------|-----|-------------|
| Պարզ React վիճակ (state) + Context՝ նույնականացման համար | Քիչ գլոբալ վիճակներ (միայն օգտատերը/տոկենը). Redux-ի կարիք չկա | Redux Toolkit, Zustand |
| Տիպավորված API շերտ (`api/*.ts`), որը կրկնում է backend-ի DTO-ները | Պայմանագրի (contract) անհամապատասխանությունների հայտնաբերում կոմպիլյացիայի ժամանակ | Չտիպավորված axios կանչեր կոմպոնենտներում |
| MUI՝ հատուկ մուգ թեմայով | Պրոֆեսիոնալ տեսք էկրանապատկերների համար՝ նվազագույն հատուկ CSS-ով | Tailwind |
| JWT-ը պահվում է `localStorage`-ում | Պարզ է bearer տոկեններով SPA-ի համար. ռիսկը մեղմվում է կարճ ժամկետով և React-ի խիստ էսկեյփինգով (escaping) (առանց `dangerouslySetInnerHTML`) | HttpOnly cookie (պահանջում է CSRF պաշտպանություն և same-site կարգավորում) — փաստաթղթավորված է որպես ապագա բարելավում |

## 3. Ակտիվ սիմուլյացիայի էջի դասավորությունը

```
┌──────────────────────────────────────────────────────────────────┐
│ Scenario title · difficulty · status chip · elapsed time · Finish │
├────────────────────┬───────────────────────────┬─────────────────┤
│ Cloud resources    │ Logs / Alerts (filterable) │ AI Assistant    │
│ (status chips)     │ evidence highlighted       │ hint / ask      │
├────────────────────┴───────────────────────────┴─────────────────┤
│ Available actions (grouped: Investigate / Respond)                │
├──────────────────────────────────────────────────────────────────┤
│ Incident timeline (attacker events + analyst actions)             │
└──────────────────────────────────────────────────────────────────┘
```

## 4. Իրականացված էջեր

| Երթուղի | Էջ | Դեր |
|-------|------|------|
| `/login`, `/register` | `AuthPages.tsx` | հանրային |
| `/` | `DashboardPage` — վիճակագրություն, միավորների պատմության գծապատկեր, չավարտված սիմուլյացիաներ, սցենարների քարտեր | ուսանող |
| `/scenarios`, `/scenarios/:id` | `ScenarioPages.tsx` — կատալոգ՝ զտիչներով, ներածական տեղեկանք + *Start simulation* | ուսանող |
| `/simulations/:id` | `simulation/ActiveSimulationPage` + `ResourcesPanel`, `LogsPanel` (ապացույցների տախտակ), `AssistantPanel`, `ActionsPanel`, `TimelinePanel` | ուսանող |
| `/simulations/:id/result` | `ResultPage` → ընդհանուր `ResultView` (չափիչ, AI հետադարձ կապ, միավորների բաշխում, բաց թողնված գործողություններ, ապացույցներ, բացատրություն) | ուսանող |
| `/history` | `HistoryPage` — առաջընթացի գծապատկերներ, AI առաջարկություններ, փորձերի պատմություն | ուսանող |
| `/admin` | `AdminDashboardPage` | ադմինիստրատոր |
| `/admin/users`, `/admin/users/:id` | `AdminUsersPages.tsx` | ադմինիստրատոր |
| `/admin/scenarios`, `/admin/scenarios/:id` (`new`) | `AdminScenarioPages.tsx` + ընդհանրական `RowEditor` | ադմինիստրատոր |
| `/admin/attempts`, `/admin/attempts/:id` | `AdminAttemptsPages.tsx` (վերաօգտագործում է `ResultView`, `LogsPanel`, `TimelinePanel`) | ադմինիստրատոր |
| `/admin/analytics` | `AdminAnalyticsPage` | ադմինիստրատոր |

## 5. Իրականացման նշումներ
- **Երթուղավորում (routing).** `createBrowserRouter`՝ ներդրված դասավորության երթուղիներով. `RequireAuth` և `RequireAdmin` պահակներ (guards) (միայն UX-ի
  համար — թույլտվությունները պարտադրում է backend-ը)։
- **Տվյալների բեռնում.** փոքր `useLoad` hook (բեռնում, սխալ, վերաբեռնում, տեղային թարմացում)՝ տվյալներ ստանալու գրադարանի փոխարեն —
  հավելվածն ունի քիչ և պարզ հարցումներ։
- **Վերաօգտագործում ուսանողի և ադմինիստրատորի տեսքերի միջև.** `ResultView`, `LogsPanel`, `TimelinePanel` և `ProgressSummary` կոմպոնենտներն
  ընդհանուր են, ուստի ադմինիստրատորները տեսնում են ճիշտ այն, ինչ տեսել է ուսանողը։
- **Սցենարների խմբագրիչ.** մեկ ընդհանրական `RowEditor` երկխոսության պատուհան, որը ղեկավարվում է դաշտերի սպեցիֆիկացիաներով, մշակում է ռեսուրսները,
  իրադարձությունները և գործողությունները. ընտրության տարբերակները (ռեսուրսների բանալիներ, հետազոտական գործողություններ) ստացվում են ընթացիկ սահմանումից, ինչը
  կանխում է հղումային սխալների մեծ մասը դեռ մինչև backend-ի վավերացնողի աշխատելը։
- **Արձագանքող (responsive) դասավորություն.** սիմուլյացիայի եռասյուն վահանակը օգտագործվում է `xl` սահմանակետից (breakpoint) սկսած. դրանից ցածր մատյանների
  դիտիչը զբաղեցնում է ամբողջ լայնությունը։
- **Քաղված դաս.** React-ի էֆեկտները պետք է օգտագործեն բլոկային մարմին — արտահայտությամբ մարմինը Chrome-ի ընթացիկ տարբերակում վերադարձնում էր Promise
  `scrollIntoView`-ից և խափանում էջը (իրականացման մատյան, Քայլեր 8–10)։
