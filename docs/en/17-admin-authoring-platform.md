# 17 — Admin scenario-authoring platform (scope change)

> **Status: current.** This document supersedes the student-facing parts of documents 01–16. The learner
> (trainee) side of CyberSim is developed by another team member; this repository now contains the
> **administrator's platform only**: creating, validating, testing, scoring and versioning incident scenarios.
> Documents 01–16 still describe the original, wider design (including the simulation engine); where they
> contradict this document, this document wins.

## 1. What changed

| Removed (learner side) | Kept / new (admin side) |
|---|---|
| Student pages: dashboard, scenario catalogue, active simulation, result, history, progress | Admin login, scenario list, scenario editor |
| `simulation`, `progress`, `analytics` backend modules and their tables | Scenario generator, dependency graph, validation, test runner, quality score, publishing, version history |
| STUDENT role and `POST /api/auth/register` | `ADMIN` is the only role; accounts are provisioned by the operator (`DemoDataInitializer`) |
| AI tutor (hints, questions, feedback, recommendations) | AI is used for scenario generation polish, variations and on-demand translation |
| Admin user management, attempt browser and learner analytics | — (they only existed to observe learners) |

The **scoring rules** (`ScoringEngine`) and the scenario data model are kept: the test runner reuses exactly the
rules the learner module will apply, so a scenario that passes the test runner behaves predictably there.

## 2. Authoring workflow

The screenshot requirement describes a workflow with a separate Python service. In this project every
"service" is a Spring Boot component in the `am.cybersim.authoring` package (one deployable, no extra database).

| # | Step | Component | Endpoint |
|---|------|-----------|----------|
| 1 | Admin creates a draft (e.g. *SSH Brute Force*) | `ScenarioGeneratorService` | `POST /api/admin/scenarios/generate` |
| 2 | Service generates timeline, commands/log lines, evidence, actions | template per scenario type + optional AI polish | (same call) |
| 3 | Admin edits the scenario | `ScenarioService` (CRUD) | `PUT /api/admin/scenarios/{id}` |
| 4 | Service builds the dependency graph | `ScenarioGraph` | `GET /api/admin/scenarios/{id}/graph` |
| 5 | Validation finds errors or confirms the scenario | `ScenarioAnalyzer` | `POST …/{id}/validate` |
| 6 | Test runner executes the correct path and the dangerous path | `ScenarioTestRunner` | `POST …/{id}/test-run` |
| 7 | System calculates the quality score | `QualityScorer` | `POST …/{id}/evaluate` (1–3 in one call) |
| 8 | Scenario is published as a new version | `AuthoringService.publish` | `POST …/{id}/publish` |
| 9 | Version history, restore into draft | `ScenarioVersion` | `GET …/{id}/versions[/{n}]`, `POST …/restore` |

Steps 9–11 of the original flow (a learner plays the scenario, the service analyses the path, strengths /
weaknesses / risk level / next-scenario recommendation) belong to the learner module and are **not** implemented
here. Published versions are the hand-over point: the learner module reads rows of `scenario_versions`.

### 2.1 Generation

`ScenarioType` = `SSH_BRUTE_FORCE`, `COMPROMISED_CREDENTIALS`, `PUBLIC_STORAGE_BUCKET`. Each type maps to a vetted
template (`resources/scenarios/*.json`: full timeline of log lines and alerts, evidence markers, scored response
actions, hints). The request may override title, difficulty, primary asset (host / user / bucket name), attacker IP
and region; they are substituted consistently across the whole document. With `useAi=true` the draft is sent to
the AI provider to polish the narrative. *AI proposes, application decides*: the answer must keep the structure
(keys, phases, outcomes, points, references) identical and pass the normal validator, otherwise the deterministic
draft is kept (`aiSource = FALLBACK`). Input is validated (`@Pattern` on IP / asset / region).

### 2.2 Dependency graph

Nodes: start, actions, events (logs / alerts), resources. Edges: `INITIAL` (start → visible event), `PREREQUISITE`
(action → dependent action), `REVEALS` (action → event it discloses), `TARGETS` / `EFFECT` (action → resource,
`EFFECT` when it changes the resource status), `ABOUT` (event → resource).

### 2.3 Validation (`ScenarioAnalyzer`)

Layer 1 – field and cross-reference rules, identical to those applied on every write (`ScenarioDefinitionValidator`):
unique keys, resolvable references, prerequisite cycles, points sign matches outcome, ≥ 1 expected action, ≥ 1
initially visible event, ≥ 1 evidence event.

Layer 2 – graph rules (only when layer 1 is clean):

| Code | Severity | Meaning |
|------|----------|---------|
| `EXPECTED_DEPENDS_ON_UNEXPECTED` | error | a correct action requires a neutral/harmful action |
| `PHASE_INVERSION` | error | an investigation action depends on a response action |
| `EVENT_ONLY_VIA_UNEXPECTED_ACTION` | error (evidence) / warning | evidence can only be obtained through a wrong action |
| `NO_RESPONSE_ACTION` | error | the correct path never responds |
| `NO_DANGEROUS_PATH` | error | no HARMFUL action, so the dangerous path cannot be tested |
| `NO_INVESTIGATION_ACTION`, `NO_CONTAINMENT`, `NO_INITIAL_ALERT`, `FEW_EVIDENCE`, `NO_DISTRACTORS`, `NO_HINTS`, `UNBALANCED_POINTS`, `SHORT_EXPLANATION`, `DUPLICATE_LABEL`, `ORPHAN_RESOURCE` | warning | quality weaknesses; they lower the score but do not block publishing |

### 2.4 Test runner

Plays the scenario in memory with the same rules a trainee would face (prerequisites, progressive evidence
disclosure, resource effects, `ScoringEngine`); deterministic and free of side effects.

* **Correct path** – all expected actions in dependency order. Must: perform every correct action, have no
  ordering penalty, reach 100 %, reveal all evidence, change at least one resource, miss nothing.
* **Dangerous path** – all harmful actions. Must: be penalised (negative points), stay failing (< 50 % and raw ≤ 0),
  not resolve the incident, not reproduce the end state of the correct fix, and explain why each action is wrong.

### 2.5 Quality score (0–100)

| Component | Max | Based on |
|-----------|-----|----------|
| Validity | 25 | no errors; each warning costs 2.5 |
| Tests | 25 | correct path 15 + dangerous path 10 (partial credit per passed check) |
| Coverage | 20 | events (8), evidence (3), resources (3), response categories (4) |
| Pedagogy | 20 | learning objectives (4), hints (3), harmful (2) and neutral (1) actions, explanation length |
| Structure | 10 | prerequisite depth (3), share of progressively revealed events (40 %) |

Grades: ≥ 85 `EXCELLENT`, ≥ 70 `GOOD`, ≥ 50 `FAIR`, else `POOR`. Each component carries a text justification.

### 2.6 Publishing and versions

Publishing is allowed only when validation has no errors, both test paths pass and the quality score is ≥ 70
(`QualityScorer.PUBLISH_THRESHOLD`). The gates are recomputed server-side on the stored draft. A successful
publish stores an immutable snapshot (definition + quality report + change note + author) as the next
`scenario_versions` row and sets `scenarios.published_version`. Editing a published scenario turns it back into a
`DRAFT` (revision + 1) without touching the published version. Lifecycle: `DRAFT → PUBLISHED → (edit) DRAFT … →
ARCHIVED`; only never-published drafts can be deleted, published ones are archived. A published version can be
restored into the draft.

The three seed scenarios are imported and published as version 1 at start-up (they also serve as generator
templates).

## 2.7 Learner-module integration (ADR-13)

The hand-over is no longer only a shared table: the learner module talks to a dedicated service API under
`/api/learner/**`, authenticated with `X-API-Key` (`LEARNER_API_KEY`; 503 when unset, 401 when wrong, and the
key never opens `/api/admin/**`):

| Step | Endpoint |
|------|----------|
| Read the published catalogue | `GET /api/learner/scenarios` |
| Read one published definition | `GET /api/learner/scenarios/{slug}` |
| Submit a completed attempt | `POST /api/learner/attempts` |
| Read result + AI feedback back | `GET /api/learner/attempts/{externalId}` |

On submission the platform **replays the submitted actions against the pinned published version** with the test
runner's rules and stores its own verified score; a claimed score is only compared (`scoreMatches`). The admin
UI gains a **Student exams** page: every submission with claimed vs verified score and a per-attempt
**Run AI review** button — the AI writes an advisory rating, message, strengths, mistakes and recommendations
around the deterministic grade, the result is stored (and audited as `REVIEW` in `ai_interactions`) and the
learner module fetches it from its own endpoint. Two demo submissions are seeded so the page is demonstrable
without the learner module running.

## 3. Data model

`V1__initial_schema.sql` was rewritten for this scope: `users` (role `ADMIN` only), `scenarios` (+ `scenario_objectives`,
`scenario_resources`, `scenario_events`, `scenario_actions`, `scenario_hints`; `status`, `revision`,
`published_version`), `scenario_versions` (JSONB snapshot, quality score and report) and `ai_interactions`
(`VARIATION`, `GENERATION`, `TRANSLATION`). **Existing databases must be recreated** (`docker compose down -v`).

## 4. Security notes

* All authoring endpoints live under `/api/admin/**` and require `ROLE_ADMIN`; there is no self-registration.
* Generator input is validated with Bean Validation; scenario definitions go through the same validator for every
  entry point (seed, generator, editor, AI variation, restore).
* AI output is untrusted: parsed, structure-checked against the draft, validated, and the free-text brief is
  delimited in the prompt as data. An AI failure never blocks authoring (deterministic fallback).
* Publishing gates are enforced on the server, not only in the UI.

## 5. Tests

* `AuthoringUnitTest` – graph, validation rules, test runner on a small fixture and on all three real scenarios
  (each must be valid, pass both paths and reach the publishing threshold), quality score properties.
* `AuthoringIntegrationTest` – full HTTP flow against PostgreSQL (Testcontainers): seed publication, generation of
  each type with parameters, graph/validate/test-run/evaluate, publish v1 → edit → publish v2, version immutability,
  archive/delete rules, publish blocked by errors, live evaluation of unsaved definitions, input validation.
* `AiGatewayTest`, `MockAiProviderTest`, `TranslationServiceTest`, `ScoringEngineTest`,
  `ScenarioDefinitionValidatorTest`, `AuthIntegrationTest` – adapted to the admin-only scope.
