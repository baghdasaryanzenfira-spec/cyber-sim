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

*(Details added in Phases 8–10.)*
