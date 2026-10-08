# 01 — Project Overview

**Project title:** Development of a Cloud Cyber Incident Simulation and Security Specialist Training Platform Using Artificial Intelligence

**Working name:** CyberSim

**Scope history.** The platform is built by a two-person team. The first iteration of this repository contained the
complete product, including the learner side (students running simulated incidents, an AI tutor, progress and
analytics). The learner-facing runtime was then moved to the other team member's module, and this repository was
refocused on the **administrator's scenario-authoring platform**: generating, editing, validating, test-running,
quality-scoring, publishing and versioning incident scenarios. Published scenario versions are the hand-over point
between the two modules. The change is recorded in [16 — Implementation log](16-implementation-log.md) and the new
platform is specified in [17 — Admin scenario-authoring platform](17-admin-authoring-platform.md); documents 02–14
describe the current, admin-only scope.

## 1. Problem

Incident-response training lives or dies by its content. A training scenario is not prose — it is a coherent
system of simulated cloud resources, an attacker timeline of log lines and alerts, evidence, a catalogue of scored
response actions and their dependencies. Authoring such content well is hard:

- **Hand-authored scenarios break silently.** A dangling reference (an action targeting a resource that does not
  exist), a circular prerequisite, evidence that can only be reached through a *wrong* action, or unbalanced
  points — none of these are visible by reading the JSON, but each one ruins the exercise for the trainee.
- **Quality is subjective without measurement.** Instructors have no objective way to tell whether a scenario
  teaches anything: does it have distractors, hints, a dangerous path, progressive disclosure of evidence?
- **AI can draft content quickly, but AI output cannot be trusted blindly.** Generated scenarios must be held to
  the same structural rules as hand-written ones before a trainee ever sees them.
- **Content evolves.** Scenarios need revisions, while trainees must keep seeing a stable, approved version.

## 2. Solution

CyberSim (this module) is the administrator's authoring platform. An instructor takes a scenario from first draft
to a published, immutable version through an eight-step pipeline:

1. **Generate** a draft from a vetted template (SSH brute force, compromised credentials, public storage bucket) —
   with optional AI polish of the narrative (*AI proposes, application decides*).
2. **Edit** every part of the scenario — resources, timeline, actions, scoring, hints — in a structured editor.
3. **Graph**: the platform builds the dependency graph (actions, events, resources, reveals, prerequisites) so the
   structure can be seen, not guessed.
4. **Validate**: field and cross-reference rules plus graph-level analysis (e.g. evidence reachable only through a
   harmful action) produce errors and warnings.
5. **Test-run**: the scenario is played in memory — the correct path must score 100 %, the dangerous path must
   fail — using exactly the scoring rules the learner module applies.
6. **Quality score**: a 0–100 score over validity, tests, coverage, pedagogy and structure, each with a
   justification.
7. **Publish**: allowed only when validation is clean, both test paths pass and the quality score reaches the
   threshold (70). Publishing snapshots the scenario as an immutable version.
8. **Versions**: every published version is kept, comparable, and restorable into a new draft.

The scenario content itself describes **simulated** incidents: all "cloud resources" are rows in the platform's
database, all "attacks" are pre-authored log entries. Nothing touches real external systems, and no command is
ever executed.

## 3. Main goals

| # | Goal |
|---|------|
| G1 | A runnable, demonstrable full-stack application (Spring Boot + PostgreSQL + React). |
| G2 | Scenario-as-data: one validated JSON document drives generation, editing, testing and publishing — no scenario-specific code. |
| G3 | Meaningful AI integration — draft generation, scenario variations, on-demand UI translation — with a mock provider so everything runs without a paid API key. |
| G4 | Treat AI output as untrusted: structural equivalence checks, validation, timeouts, fallbacks, audit logging. |
| G5 | Objective, explainable quality measurement and server-enforced publishing gates. |
| G6 | Automated tests for the critical logic (validation, graph analysis, test runner, quality score, publish gates, security, AI fallback). |
| G7 | Thesis-ready documentation explaining WHAT, WHY and HOW for every important decision, in English and Armenian. |

## 4. Scope of the first version (MVP)

Three fully implemented scenario types, each shipped as a vetted template and as a published seed scenario:

1. **SSH Brute-Force on a Cloud VM** — network.
2. **Suspicious Login with Compromised Credentials** — authentication.
3. **Publicly Exposed Storage Bucket** — storage.

Out of scope for this module (owned by the other team member's learner module): trainee accounts, running
simulations, grading attempts, progress tracking, learner analytics. Out of scope for the MVP overall:
real cloud provider integration, live attack traffic, team exercises.

## 5. Repository layout

```
cyber-sim/
├── backend/            Spring Boot 4 (Java 21) modular monolith
├── frontend/           React + TypeScript + Vite single-page application (admin UI, EN/HY)
├── docs/
│   ├── en/             English documentation (primary)
│   └── hy/             Armenian translation of the same documents
├── docker-compose.yml  PostgreSQL, backend, frontend, optional LocalStack
├── .env.example        All configurable environment variables (no secrets)
└── README.md
```

## 6. Document map

| File | Content |
|------|---------|
| 02-requirements.md | Functional and non-functional requirements of the authoring platform, MVP definition |
| 03-technology-research.md | Technologies and methods, alternatives considered |
| 04-system-architecture.md | Modules, layers, diagrams, architecture decisions |
| 05-database-design.md | ER diagram, tables, constraints, versioning model |
| 06-backend-implementation.md | Backend structure, REST API of the authoring platform |
| 07-frontend-implementation.md | Admin SPA structure, pages, bilingual UI (ADR-11) |
| 08-simulation-engine.md | Scenario execution model and the in-memory test runner |
| 09-ai-integration.md | AI provider abstraction, generation/variation/translation tasks, safety |
| 10-security.md | Authentication, authorization, threat model |
| 11-testing.md | Test strategy and results |
| 12-deployment.md | Docker, environment variables, commands |
| 13-user-guide.md | Learner guide — moved to the learner module (stub) |
| 14-admin-guide.md | How administrators author, test and publish scenarios |
| 15-thesis-material.md | Material organised by the three thesis sections |
| 16-implementation-log.md | Chronological implementation history, including the scope change |
| 17-admin-authoring-platform.md | The authoring pipeline in detail (workflow, validation, quality, publishing) |
| thesis-figures.md | Recommended thesis figures and how to reproduce them |
