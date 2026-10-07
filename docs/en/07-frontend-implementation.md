# 07 — Frontend Implementation

## 1. Structure

```
frontend/
├── index.html
├── vite.config.ts          dev proxy /api → http://localhost:8080
├── Dockerfile              build with Node, serve with nginx (+ /api reverse proxy)
└── src/
    ├── main.tsx            React root, ThemeProvider, Router
    ├── App.tsx             route definitions
    ├── theme.ts            Dark "SOC" theme
    ├── api/                client.ts (axios + JWT interceptor), endpoints.ts (typed API functions), types.ts
    ├── auth/               AuthContext, guards.tsx (RequireAuth, RequireAdmin)
    ├── hooks/              useLoad (load / error / reload state)
    ├── i18n/               index.ts (i18next setup), en.ts, hy.ts (UI translations)
    ├── components/         Layout, Chips, StatTile, ScenarioCard, ResultView, ProgressView, Feedback, LanguageSwitcher
    └── pages/
        ├── AuthPages.tsx   login + registration
        ├── DashboardPage, ScenarioPages (catalogue + briefing)
        ├── simulation/     ActiveSimulationPage + panels (Resources, Logs incl. evidence board, Actions, Timeline, Assistant)
        ├── ResultPage, HistoryPage (progress)
        └── admin/          AdminDashboardPage, AdminUsersPages, AdminScenarioPages + RowEditor, AdminAttemptsPages, AdminAnalyticsPage
```

## 2. Key decisions

| Decision | Why | Alternative |
|----------|-----|-------------|
| Plain React state + Context for auth | Few global states (only the user/token); no need for Redux | Redux Toolkit, Zustand |
| Typed API layer (`api/*.ts`) mirroring backend DTOs | Compile-time detection of contract mismatches | Untyped axios calls in components |
| MUI with a custom dark theme | Professional look for screenshots with little custom CSS | Tailwind |
| JWT stored in `localStorage` | Simple for an SPA with bearer tokens; risk mitigated by short expiry and strict React escaping (no `dangerouslySetInnerHTML`) | HttpOnly cookie (needs CSRF protection and same-site setup) — documented as future improvement |
| `react-i18next` for English/Armenian UI | De-facto standard, no provider boilerplate (`useTranslation` hook), interpolation and language switching without a reload | Hand-rolled context (no interpolation, no pluralisation); URL-prefixed routes (`/hy/...`) — unnecessary for a single-user training tool |

### ADR-11 — UI translation with typed resource bundles

- **What:** All UI strings live in `src/i18n/en.ts` and `src/i18n/hy.ts`, grouped by area
  (`common`, `nav`, `auth`, `dashboard`, `scenarios`, `sim`, `result`, `history`, `admin`, `editor`, `enums`).
  Components read them through `useTranslation()`; `hy.ts` is declared as `typeof en`, so the TypeScript
  compiler rejects a missing or misspelled key — the two bundles cannot drift apart.
- **Scope:** UI chrome only. Scenario *content* (titles, log messages, action labels, AI answers) comes from the
  database and is shown exactly as authored; it is translated on request instead, by the per-text *Translate*
  button described in ADR-12 (09-ai-integration.md).
- **Switching:** `LanguageSwitcher` (EN / ՀԱՅ) sits in the app bar and on the login card. The choice is stored
  in `localStorage` under `cybersim.lang` and applied to `<html lang>`; reads and writes are wrapped in
  `try/catch` so private-browsing mode degrades to the default (English) instead of failing.
- **Enum labels:** backend enums (`SimulationStatus`, `Difficulty`, `Category`, `Severity`, `ActionOutcome`,
  `ScoreItemKind`) are translated in the `enums` namespace and rendered by the chip components, so status and
  severity read in the selected language while the API contract stays unchanged.
- **Fonts:** Inter carries no Armenian glyphs, so `@fontsource/noto-sans-armenian` is self-hosted alongside it
  and placed second in the font stack; the browser resolves it per glyph, leaving Latin text on Inter. Both are
  bundled, so the platform needs no external font CDN.

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
- **Reuse between student and admin views:** `ResultView`, `LogsPanel`, `TimelinePanel` and `ProgressView` are
  shared, so administrators see exactly what the student saw.
- **Content translation:** `TranslateButton` is a small inline link rendered next to each piece of content text
  (briefing, objectives, action descriptions, every log line, AI answers and feedback, recommendations). It calls
  `POST /api/ai/translate` for that one string and shows the result underneath; nothing is cached or stored, and
  the component is a plain block so the result panel stacks under the trigger instead of becoming a flex item of
  whatever row it sits in (ADR-12).
- **Scenario editor:** one generic `RowEditor` dialog driven by field specifications handles resources, events and
  actions; select options (resource keys, investigation actions) are derived from the current definition, which
  prevents most reference errors before the backend validator runs.
- **Responsive layout:** the three-column simulation console is used from the `xl` breakpoint; below it the log viewer
  spans the full width.
- **Lesson learned:** React effects must use a block body — an expression body returned a Promise from
  `scrollIntoView` in current Chrome and crashed the page (implementation log, Steps 8–10).
