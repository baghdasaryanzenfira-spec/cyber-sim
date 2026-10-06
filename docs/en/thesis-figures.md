# Recommended Thesis Figures (≈ 20)

Diagram sources are Mermaid blocks in the documentation — render them with any Mermaid tool
(e.g. https://mermaid.live, the VS Code / IntelliJ Mermaid plugins, or `npx @mermaid-js/mermaid-cli -i file.md`).
Screenshots: run `docker compose up --build`, sign in with the demo accounts from `.env`, use a browser window of
**at least 1600 × 900** (the active simulation shows three columns from 1536 px content width) and the default zoom.
Complete one or two simulations first so that dashboards and charts contain data.

## Section 1 — Research of technologies and methods

| Fig. | Title | Purpose | Source | How to reproduce |
|-----:|-------|---------|--------|------------------|
| 1 | Incident response lifecycle (NIST SP 800-61) mapped to CyberSim states | Shows the methodological basis of the training workflow | Mermaid, `03-technology-research.md` §1.2 + table | Render the flowchart; add the mapping table below it |
| 2 | Technology stack and interaction | Backend/frontend/database/AI technologies and how they communicate | Mermaid, `04-system-architecture.md` §1 | Render the high-level architecture diagram |
| 3 | Container / infrastructure diagram | Docker Compose services, ports, optional LocalStack | Mermaid, `12-deployment.md` §1 | Render the container diagram |
| 4 | AI workflow: "AI advises, application decides" | Request flow with validation, timeout and fallback | Mermaid sequence, `09-ai-integration.md` §3 | Render the sequence diagram |

## Section 2 — System design and software implementation

| Fig. | Title | Purpose | Source | How to reproduce |
|-----:|-------|---------|--------|------------------|
| 5 | System architecture: modules of the modular monolith | Module boundaries and dependencies | Mermaid, `04-system-architecture.md` §3 | Render the module diagram |
| 6 | Main user training workflow | MVP flow from login to progress | Mermaid, `02-requirements.md` §4 | Render the first flowchart |
| 7 | Database ER diagram | Template vs instance tables, relationships | Mermaid, `05-database-design.md` §2 | Render the erDiagram (or generate from the DB with IntelliJ "Diagrams → Show Visualization") |
| 8 | Simulation engine state transitions | CREATED → RUNNING → INVESTIGATING → RESPONDING → COMPLETED / ABANDONED | Mermaid stateDiagram, `08-simulation-engine.md` §2 | Render the state diagram |
| 9 | Action processing flow | What happens when a student performs an action | Mermaid flowchart, `08-simulation-engine.md` §3 | Render the flowchart |
| 10 | Code: Spring Security configuration | Stateless JWT, URL rules, JSON 401/403 | `backend/src/main/java/am/cybersim/security/SecurityConfig.java`, method `securityFilterChain` | Screenshot from the IDE (dark theme, ~40 lines) |
| 11 | Code: simulation engine `apply` method | Generic, data-driven action handling | `SimulationEngine.java`, method `apply` | IDE screenshot |
| 12 | Code: deterministic scoring + AI gateway fallback | Grading without AI; AI wrapped in validation/fallback | `ScoringEngine.pointsFor` and `AiGateway.execute` | Two small IDE screenshots side by side |
| 13 | AI request/response example | Prompt structure and validated structured feedback | `09-ai-integration.md` §7 | Copy the example; or capture a live one from *Admin → Attempts → Details → AI interactions* |

## Section 3 — Functional capabilities and results

| Fig. | Title | Purpose | Source | How to reproduce |
|-----:|-------|---------|--------|------------------|
| 14 | Login / registration | Entry point, authentication | Screenshot `/login` (and `/register`) | Log out, open the page |
| 15 | Student dashboard | Statistics, score history, scenarios | Screenshot `/` as student | After 2+ completed simulations |
| 16 | Scenario selection and briefing | Catalogue and learning objectives | Screenshots `/scenarios` and `/scenarios/{id}` | Open a scenario briefing |
| 17 | Active simulation console | Resources, logs & alerts, AI assistant, actions, timeline | Screenshot `/simulations/{id}` | Start a scenario, perform 2–3 investigation actions, flag one log entry, request one hint, then capture |
| 18 | Simulation result with AI feedback | Score gauge, feedback, breakdown | Screenshot `/simulations/{id}/result` | Finish a simulation with some mistakes (e.g. a harmful action) for meaningful feedback |
| 19 | Admin dashboard and analytics | Platform statistics, common mistakes | Screenshots `/admin` and `/admin/analytics` | Sign in as admin after several student attempts |
| 20 | Scenario management / editor | Configuring evidence, expected actions and scoring | Screenshot `/admin/scenarios/{id}`, tab *Actions & scoring* (optionally *Logs, alerts & evidence*) | Open the SSH scenario in the editor |

Optional extras if space allows: progress & history page with AI recommendations (`/history`); attempt detail with
AI interactions (`/admin/attempts/{id}`); Swagger UI (`http://localhost:8080/swagger-ui.html`); test report
(`mvnw test` output: 74 tests, 0 failures).
