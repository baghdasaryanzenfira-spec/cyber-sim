# Recommended Thesis Figures (≈ 20)

Diagram sources are Mermaid blocks in the documentation — render them with any Mermaid tool
(e.g. https://mermaid.live, the VS Code / IntelliJ Mermaid plugins, or `npx @mermaid-js/mermaid-cli -i file.md`).
Screenshots: start the stack with `docker compose up --build` (after the schema rewrite a fresh database is
required: `docker compose down -v` first), sign in with the demo admin from `.env`, use a browser window of at
least **1600 × 900** and the default zoom. The three seed scenarios are published automatically at start-up, so the
dashboard and the scenario list have content immediately; generate one extra draft (Fig. 16) so that DRAFT rows and
a fresh quality report are visible too.

## Section 1 — Research of technologies and methods

| Fig. | Title | Purpose | Source | How to reproduce |
|-----:|-------|---------|--------|------------------|
| 1 | Incident response lifecycle (NIST SP 800-61) mapped to scenario phases | Methodological basis of the content being authored | Mermaid, `03-technology-research.md` §1.2 | Render the flowchart; add the mapping table below it |
| 2 | Technology stack and interaction | Backend/frontend/database/AI technologies and how they communicate | Mermaid, `04-system-architecture.md` §1 | Render the high-level architecture diagram |
| 3 | Container / infrastructure diagram | Docker Compose services, ports, optional LocalStack | Mermaid, `12-deployment.md` §1 | Render the container diagram |
| 4 | AI workflow: "AI proposes, application decides" | Request flow with validation, timeout and fallback | Mermaid sequence, `09-ai-integration.md` §3 | Render the sequence diagram |

## Section 2 — System design and software implementation

| Fig. | Title | Purpose | Source | How to reproduce |
|-----:|-------|---------|--------|------------------|
| 5 | System architecture: modules of the modular monolith | Module boundaries and dependencies | Mermaid, `04-system-architecture.md` §3 | Render the module diagram |
| 6 | Authoring workflow (generate → edit → graph → validate → test → score → publish → versions) | The core pipeline of this module | Table and flow in `17-admin-authoring-platform.md` §2 (Mermaid in `02-requirements.md` §4 if present) | Render the flowchart |
| 7 | Database ER diagram | Scenario template tables, version snapshots, AI audit | Mermaid, `05-database-design.md` §2 | Render the erDiagram (or generate from the DB with IntelliJ "Diagrams → Show Visualization") |
| 8 | Scenario dependency graph | Actions, events, resources; prerequisite / reveals / targets edges | Screenshot, editor → *Graph* tab | Open a seed scenario in the editor, switch to the Graph tab |
| 9 | Scenario execution model used by the test runner | Correct path vs dangerous path over the same rules | Mermaid / tables, `08-simulation-engine.md` | Render the diagram |
| 10 | Code: Spring Security configuration | Stateless JWT, ADMIN-only URL rules, JSON 401/403 | `backend/src/main/java/am/cybersim/security/SecurityConfig.java`, method `securityFilterChain` | Screenshot from the IDE (dark theme, ~40 lines) |
| 11 | Code: publish gates | Server-side re-checks before an immutable version is written | `authoring/AuthoringService.java`, method `publish` (+ `QualityScorer.PUBLISH_THRESHOLD`) | IDE screenshot |
| 12 | Code: deterministic scoring + AI gateway fallback | Shared grading rules without AI; AI wrapped in validation/fallback | `scoring/ScoringEngine.java` and `ai/AiGateway.execute` | Two small IDE screenshots side by side |
| 13 | AI request/response example | Prompt structure and a validated generation/variation answer | `09-ai-integration.md` example; or query the audit table: `docker compose exec postgres psql -U cybersim -c "select interaction_type, provider, status, left(request_text,80), left(response_text,80) from ai_interactions order by id desc limit 5;"` | Copy the example or capture the query output |

## Section 3 — Functional capabilities and results

| Fig. | Title | Purpose | Source | How to reproduce |
|-----:|-------|---------|--------|------------------|
| 14 | Admin login | Entry point, authentication, EN/ՀԱՅ switch | Screenshot `/login` | Log out, open the page |
| 15 | Admin dashboard | Content status tiles, the eight-step workflow, recent scenarios | Screenshot `/admin` | Sign in as admin |
| 16 | Scenario generator | Type, difficulty, asset, attacker IP, region, brief, optional AI | Screenshot `/admin/generate` | Fill the form and generate a draft |
| 17 | Scenario editor: actions & scoring | Configuring the scored action catalogue | Screenshot `/admin/scenarios/{id}`, tab *Actions & scoring* | Open the SSH seed scenario |
| 18 | Dependency graph panel | The structure of a scenario made visible | Screenshot, editor → *Graph* tab | Same scenario, Graph tab |
| 19 | Review panel: validation, test runs, quality score, publish gate | The quality-assurance core in one view | Screenshot, editor → *Review* tab | Run evaluate on the generated draft; capture findings, both test paths and the score breakdown |
| 20 | Version history and restore | Immutable published versions | Screenshot, editor → *Versions* tab | Publish the draft, edit it, publish again; open Versions |

Optional extras if space allows: the Armenian interface (any page after switching to ՀԱՅ); on-demand translation of
content (the *Translate* link under a scenario description); Swagger UI (`http://localhost:8080/swagger-ui.html`);
test report (`mvnw test` output: 63 tests, 0 failures).
