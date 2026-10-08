# CyberSim frontend

React 19 + TypeScript + Vite single-page application: the **admin scenario-authoring platform** (the learner-facing
UI is owned by a separate team). Admins sign in, generate scenario drafts from templates, edit them, review
validation / test-runner / quality results, view the dependency graph, publish versions and restore earlier ones.
The UI is available in English and Armenian. See `docs/en/07-frontend-implementation.md`.

```bash
npm install
npm run dev      # http://localhost:5173, proxies /api to http://localhost:8080
npm run build    # type check + production build into dist/
npm run lint     # oxlint
```
