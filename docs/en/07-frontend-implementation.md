# 07 — Frontend Implementation

> Scope: the **admin scenario-authoring platform** (see [17-admin-authoring-platform.md](17-admin-authoring-platform.md)).
> The SPA has exactly one audience — the administrator who generates, edits, analyses, publishes and versions
> scenarios. There are no student pages.

## 1. Structure

```
frontend/
├── index.html
├── vite.config.ts          dev proxy /api → http://localhost:8080
├── Dockerfile              build with Node, serve with nginx (+ /api reverse proxy)
└── src/
    ├── main.tsx            React root, ThemeProvider, Router, i18n and font imports
    ├── App.tsx             route definitions
    ├── theme.ts            Dark "SOC" theme
    ├── api/                client.ts (axios + JWT interceptor), endpoints.ts (typed API functions), types.ts
    ├── auth/               AuthContext, guards.tsx (RequireAuth, RequireAdmin)
    ├── hooks/              useLoad (load / error / reload state)
    ├── i18n/               index.ts (i18next setup), en.ts, hy.ts (UI translations)
    ├── components/         Layout, Chips, StatTile, Feedback, LanguageSwitcher, TranslateButton
    └── pages/
        ├── AuthPages.tsx   admin login (no registration)
        └── admin/          AdminDashboardPage, GeneratorPage, AdminScenarioPages (list + editor),
                            AuthoringPanels (review/versions), GraphPanel, RowEditor, scenarioMeta
```

## 2. Key decisions

| Decision | Why | Alternative |
|----------|-----|-------------|
| Plain React state + Context for auth | Few global states (only the user/token); no need for Redux | Redux Toolkit, Zustand |
| Typed API layer (`api/*.ts`) mirroring backend DTOs | Compile-time detection of contract mismatches | Untyped axios calls in components |
| MUI with a custom dark theme | Professional look for screenshots with little custom CSS | Tailwind |
| JWT stored in `localStorage` | Simple for an SPA with bearer tokens; risk mitigated by short expiry and strict React escaping (no `dangerouslySetInnerHTML`) | HttpOnly cookie (needs CSRF protection and same-site setup) — documented as future improvement |
| `react-i18next` for English/Armenian UI | De-facto standard, no provider boilerplate (`useTranslation` hook), interpolation and language switching without a reload | Hand-rolled context (no interpolation); URL-prefixed routes (`/hy/...`) — unnecessary for a single-operator tool |

### ADR-11 — UI translation with typed resource bundles

- **What:** All UI strings live in `src/i18n/en.ts` and `src/i18n/hy.ts`, grouped by area
  (`common`, `nav`, `auth`, `dashboard`, `generator`, `aiSource`, `list`, `editor`, `review`, `graph`,
  `versions`, `translate`, `enums`). Components read them through `useTranslation()`; `hy.ts` is declared as
  `typeof en`, so the TypeScript compiler rejects a missing or misspelled key — the two bundles cannot drift apart.
- **Scope:** UI chrome only. Scenario *content* (titles, log messages, action labels) is shown exactly as
  authored; it is translated on request instead, by the per-text *Translate* button described in ADR-12
  (09-ai-integration.md).
- **Switching:** `LanguageSwitcher` (EN / ՀԱՅ) sits in the app bar and on the login card. The choice is stored
  in `localStorage` under `cybersim.lang` and applied to `<html lang>`; reads and writes are wrapped in
  `try/catch` so private-browsing mode degrades to the default (English) instead of failing.
- **Enum labels:** backend enums (`ScenarioStatus`, `Difficulty`, `Category`, `Severity`, `ActionOutcome`,
  `ScenarioType`, issue severities, test paths, quality grades) are translated in the `enums` namespace and
  rendered by the chip components, so the UI reads in the selected language while the API contract stays unchanged.
- **Fonts:** Inter carries no Armenian glyphs, so `@fontsource/noto-sans-armenian` is self-hosted alongside it
  and placed second in the font stack; the browser resolves it per glyph, leaving Latin text on Inter. Both are
  bundled, so the platform needs no external font CDN.

## 3. Routes and pages

Routing: `createBrowserRouter` with nested layout routes; `RequireAuth` and `RequireAdmin` guards (UX only — the
backend enforces authorization). `/` redirects to `/admin`; unknown paths redirect to `/`.

| Route | Page | Purpose |
|-------|------|---------|
| `/login` | `AuthPages.tsx` | Admin sign-in with EN/ՀԱՅ switch; no registration link — accounts are provisioned by the operator |
| `/admin` | `AdminDashboardPage` | Status stat tiles (total / drafts / published / archived), the 8-step authoring workflow as a guided list, 5 most recently updated scenarios |
| `/admin/generate` | `GeneratorPage` | Scenario generator form: type, title, difficulty, primary asset, attacker IP, region, free-text brief, `useAi` switch; client-side checks mirror the backend `@Pattern` rules, server field errors land on the matching inputs |
| `/admin/scenarios` | `AdminScenariosPage` | All scenarios with status chip, revision, published version; archive/restore, delete (never-published only), AI variation |
| `/admin/scenarios/new`, `/admin/scenarios/:id` | `ScenarioEditorPage` | The full editor (next section) |

## 4. The scenario editor

One page with nine tabs; the last three only exist for saved scenarios:

| Tab | Content |
|-----|---------|
| General | metadata, penalties, objectives, incident explanation, recommended solution |
| Infrastructure | simulated cloud resources (`RowEditor` dialog per row) |
| Logs, alerts & evidence | the incident timeline; evidence flags and `revealedByActionKey` per event |
| Actions & scoring | the action catalogue with phases, outcomes, points and prerequisites |
| Hints | static hints, one per line |
| JSON | the whole definition as editable JSON (apply replaces the form state) |
| Review | validation report, test-runner paths with per-check results and expandable step tables, quality score breakdown, publish panel |
| Graph | the dependency graph (`GraphPanel`) |
| Versions | published versions with grade and change note; view a frozen definition, restore into the draft |

Implementation notes:

- **Live evaluation on unsaved content:** the Review tab evaluates the *current editor state* through
  `POST /api/admin/scenarios/evaluate`, so feedback updates while editing — but the **publish button uses the
  stored draft** and is disabled while the editor is dirty (`review.saveFirst`), matching the server-side gates.
- **Graph layout:** `GraphPanel` lays the nodes out itself (no graph library): start → actions by prerequisite
  depth → each event right after the action that reveals it → resources last, drawn as plain SVG. The relaxation
  loop is capped by the action count, so a prerequisite cycle (which validation reports anyway) cannot hang the
  layout. The panel re-fetches per saved revision and shows a notice when unsaved edits are not in the picture.
- **`RowEditor`:** one generic dialog driven by field specifications handles resources, events and actions;
  select options (resource keys, investigation actions) are derived from the current definition, which prevents
  most reference errors before the backend validator runs.
- **Content translation:** `TranslateButton` (a small inline link backed by `POST /api/ai/translate`) sits next
  to event log messages in the editor, so an author can read authored English telemetry in Armenian on demand;
  nothing is cached or stored (ADR-12).
- **Data loading:** the small `useLoad` hook (load, error, reload, local update) instead of a data-fetching
  library — the application has few, simple requests.
