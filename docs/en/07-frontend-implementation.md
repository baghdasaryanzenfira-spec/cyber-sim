# 07 — Frontend Implementation

## 1. Structure (planned in Phase 0)

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

## 2. Key decisions

| Decision | Why | Alternative |
|----------|-----|-------------|
| Plain React state + Context for auth | Few global states (only the user/token); no need for Redux | Redux Toolkit, Zustand |
| Typed API layer (`api/*.ts`) mirroring backend DTOs | Compile-time detection of contract mismatches | Untyped axios calls in components |
| MUI with a custom dark theme | Professional look for screenshots with little custom CSS | Tailwind |
| JWT stored in `localStorage` | Simple for an SPA with bearer tokens; risk mitigated by short expiry and strict React escaping (no `dangerouslySetInnerHTML`) | HttpOnly cookie (needs CSRF protection and same-site setup) — documented as future improvement |

## 3. Active simulation page layout

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

## 4. Implemented pages

| Route | Page | Role |
|-------|------|------|
| `/login`, `/register` | `AuthPages.tsx` | public |
| `/` | `DashboardPage` — stats, score history chart, unfinished simulations, scenario cards | student |
| `/scenarios`, `/scenarios/:id` | `ScenarioPages.tsx` — catalogue with filters, briefing + *Start simulation* | student |
| `/simulations/:id` | `simulation/ActiveSimulationPage` + `ResourcesPanel`, `LogsPanel` (evidence board), `AssistantPanel`, `ActionsPanel`, `TimelinePanel` | student |
| `/simulations/:id/result` | `ResultPage` → shared `ResultView` (gauge, AI feedback, breakdown, missed actions, evidence, explanation) | student |
| `/history` | `HistoryPage` — progress charts, AI recommendations, attempt history | student |
| `/admin` | `AdminDashboardPage` | admin |
| `/admin/users`, `/admin/users/:id` | `AdminUsersPages.tsx` | admin |
| `/admin/scenarios`, `/admin/scenarios/:id` (`new`) | `AdminScenarioPages.tsx` + generic `RowEditor` | admin |
| `/admin/attempts`, `/admin/attempts/:id` | `AdminAttemptsPages.tsx` (reuses `ResultView`, `LogsPanel`, `TimelinePanel`) | admin |
| `/admin/analytics` | `AdminAnalyticsPage` | admin |

## 5. Implementation notes
- **Routing:** `createBrowserRouter` with nested layout routes; `RequireAuth` and `RequireAdmin` guards (UX only — the
  backend enforces authorization).
- **Data loading:** a small `useLoad` hook (load, error, reload, local update) instead of a data-fetching library —
  the application has few, simple requests.
- **Reuse between student and admin views:** `ResultView`, `LogsPanel`, `TimelinePanel` and `ProgressSummary` are
  shared, so administrators see exactly what the student saw.
- **Scenario editor:** one generic `RowEditor` dialog driven by field specifications handles resources, events and
  actions; select options (resource keys, investigation actions) are derived from the current definition, which
  prevents most reference errors before the backend validator runs.
- **Responsive layout:** the three-column simulation console is used from the `xl` breakpoint; below it the log viewer
  spans the full width.
- **Lesson learned:** React effects must use a block body — an expression body returned a Promise from
  `scrollIntoView` in current Chrome and crashed the page (implementation log, Steps 8–10).
