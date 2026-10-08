> **Scope change:** the project is now an admin-only scenario-authoring platform; the student side was removed. See [17 — Admin scenario-authoring platform](17-admin-authoring-platform.md), which supersedes the learner-facing parts of this document.

# 01 — Project Overview

**Project title:** Development of a Cloud Cyber Incident Simulation and Security Specialist Training Platform Using Artificial Intelligence

**Working name:** CyberSim

## 1. Problem

Cloud security incidents (stolen credentials, brute-force attacks, exposed storage, excessive IAM
permissions) are among the most common causes of real-world breaches. Training security specialists
to *respond* to such incidents is difficult:

- Real cloud environments are expensive and risky to use for training — a mistake can cause
  real damage or real cost.
- Theoretical courses teach concepts but rarely let students practise the investigation and
  response *workflow* (read logs → find evidence → identify the compromised resource → contain → recover).
- Feedback is usually delayed and generic; an instructor cannot review every student attempt in detail.

## 2. Solution

CyberSim is a web platform in which students investigate **simulated** cloud cyber incidents in a
safe, isolated, deterministic environment:

1. The student chooses a scenario (e.g. *SSH brute-force against a cloud VM*).
2. The platform creates a private simulation instance: virtual cloud resources, logs, alerts and
   an incident timeline.
3. The student investigates — inspects log sources, which reveal new evidence — and performs
   response actions (isolate a VM, disable credentials, rotate keys, …).
4. An **AI assistant** that knows the scenario context gives hints without revealing the full
   solution and answers questions.
5. When the student finishes, a deterministic **scoring engine** evaluates their actions against
   the scenario's expected actions, and the **AI** produces personalised feedback
   (strengths, mistakes, missed evidence, order of actions).
6. Progress is stored; the AI recommends topics to study next.
7. Administrators create and edit scenarios, configure expected actions and scoring, and view
   attempts, statistics and the most common mistakes.

Nothing in the platform touches real external systems. All "cloud resources" are rows in the
platform's own database; all "attacks" are pre-authored log entries.

## 3. Main goals

| # | Goal |
|---|------|
| G1 | A runnable, demonstrable full-stack application (Spring Boot + PostgreSQL + React). |
| G2 | A reusable, deterministic simulation engine driven by scenario *data*, not scenario-specific code. |
| G3 | Meaningful AI integration: contextual hints, post-simulation analysis, learning recommendations, scenario variation — with a mock provider so it runs without a paid API key. |
| G4 | Treat AI output as untrusted: validation, timeouts, fallbacks, logging. |
| G5 | Admin panel for scenario authoring and analytics. |
| G6 | Automated tests for the critical logic (security, state machine, scoring, AI fallback). |
| G7 | Thesis-ready documentation explaining WHAT, WHY and HOW for every important decision. |

## 4. Scope of the first version (MVP)

Three fully implemented scenarios (chosen because they are the simplest to understand and explain):

1. **SSH Brute-Force on a Cloud VM** — beginner.
2. **Suspicious Login with Compromised Credentials** — beginner.
3. **Publicly Exposed Storage Bucket** — beginner/intermediate.

Out of scope for the MVP (documented as future work): multiplayer/team exercises, real cloud
provider integration, live attack traffic, certificates/grading exports.

## 5. Repository layout

```
cyber-sim/
├── backend/            Spring Boot 4 (Java 21) modular monolith
├── frontend/           React + TypeScript + Vite single-page application
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
| 02-requirements.md | Functional and non-functional requirements, MVP definition |
| 03-technology-research.md | Technologies and methods, alternatives considered |
| 04-system-architecture.md | Modules, layers, diagrams, architecture decisions |
| 05-database-design.md | ER diagram, tables, constraints, indexes |
| 06-backend-implementation.md | Backend structure, REST API |
| 07-frontend-implementation.md | Frontend structure, pages |
| 08-simulation-engine.md | Lifecycle, actions, events, scoring |
| 09-ai-integration.md | AI provider abstraction, prompts, safety |
| 10-security.md | Authentication, authorization, threat model |
| 11-testing.md | Test strategy and results |
| 12-deployment.md | Docker, environment variables, commands |
| 13-user-guide.md | How students use the platform |
| 14-admin-guide.md | How administrators use the platform |
| 15-thesis-material.md | Material organised by the three thesis sections |
| 16-implementation-log.md | Chronological implementation history |
| thesis-figures.md | ~20 recommended thesis figures and how to reproduce them |
