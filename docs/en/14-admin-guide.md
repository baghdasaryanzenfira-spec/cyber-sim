# 14 — Administrator Guide

Administrators sign in at **http://localhost:3000** with an `ADMIN` account (demo:
`DEMO_ADMIN_EMAIL` / `DEMO_ADMIN_PASSWORD` from `.env`). There is no self-registration; accounts are
provisioned by the operator. The interface is available in English and Armenian (EN / ՀԱՅ switch in
the top bar), and every piece of scenario content carries a small **Translate** link that renders an
on-demand translation next to the original (nothing is stored).

## 1. Dashboard

Status tiles (total / drafts / published / archived), the eight-step authoring workflow as a
checklist, and the five most recently updated scenarios with an **Open** shortcut.

## 2. Generating a draft

**To generate a scenario:** *Generate* in the menu → choose a **type** (`SSH brute force`,
`Compromised credentials`, `Public storage bucket`) → optionally override:

| Field | Rule |
|-------|------|
| Title | free text, defaults to the template title |
| Difficulty | defaults to the template difficulty |
| Primary asset | host / user / bucket name — letters, digits, dot, dash, underscore |
| Attacker IP | IPv4 (use documentation ranges such as `203.0.113.0/24`) |
| Region | lower-case letters, digits, dashes (e.g. `eu-central-1`) |
| Brief | free text; used by the AI polish as instructions, treated as data in the prompt |
| Polish with AI | off by default; see below |

Overrides are substituted consistently through the whole document (timeline, log lines, actions).
Invalid input is rejected field-by-field with the same rules on the client and the server.

**Expected result:** you land in the editor on a new `DRAFT`. With *Polish with AI* on, a banner
names the source: `AI` (Claude rewrote the narrative; structure verified unchanged) or `FALLBACK` /
`MOCK` (the deterministic template was kept — e.g. no API key). The draft is **never** published
automatically.

**AI variation:** in the scenario list, the sparkle icon creates a new draft that retells an
existing scenario (new names, IPs, wording) with identical structure, keys and points. Review it in
the editor like any other draft.

## 3. Editing

The editor has tabs; the first six edit the draft, the last three analyse it:

| Tab | What you configure |
|-----|-------------------|
| General | slug (fixed after creation), title, summary, briefing, difficulty, category, estimated time, hint penalty, order penalty, learning objectives, incident explanation, recommended solution |
| Infrastructure | simulated resources: key, type, name, region, initial status, properties (JSON) |
| Logs, alerts & evidence | timeline entries: offset, type, severity, source, message, resource, **evidence** marker + note, and the investigation action that **reveals** the entry (empty = visible from the start) |
| Actions & scoring | the action catalogue: phase, category, target resource, **outcome** (EXPECTED / NEUTRAL / HARMFUL), **points**, "should come after" (prerequisite), effect on the resource status, console output, explanation |
| Hints | static hints from general to specific |
| JSON | the complete definition as JSON (import/export) |
| Review | validation + test runner + quality + publish (see §5) |
| Graph | the dependency graph (see §4) |
| Versions | published history (see §6) |

**Save** validates the whole definition; every problem is reported together (e.g. *points must be
positive for EXPECTED*, *unknown resource key*, *prerequisites form a cycle*). Saving a published
scenario turns it back into a `DRAFT` (revision + 1) — the published version stays live for the
learner module until you publish again.

Rule of thumb: make the points of all EXPECTED actions add up to 100, so the raw score equals the
percentage.

## 4. Dependency graph

The *Graph* tab draws the scenario as nodes (start, actions coloured by outcome, events, resources)
and edges (`INITIAL`, `PREREQUISITE`, `REVEALS`, `TARGETS` / `EFFECT`, `ABOUT`). Use it to spot
unreachable evidence, inverted phases or orphan resources at a glance. The graph reflects the
**saved** draft; an "unsaved changes" note appears when the editor content is newer.

## 5. Review: validate, test, evaluate, publish

**To check a draft:** open the *Review* tab and press **Run checks**. Checks run on the current
editor content (even unsaved), so feedback is live. Three sections appear:

1. **Validation** — ERROR / WARNING / INFO findings with code, path and message
   (e.g. `EXPECTED_DEPENDS_ON_UNEXPECTED`, `NO_DANGEROUS_PATH`, `FEW_EVIDENCE`). Errors block
   publishing; warnings only lower the quality score.
2. **Test runner** — two in-memory walks with the real scoring rules:
   the **correct path** (all expected actions in dependency order; must reach 100 %, reveal all
   evidence, change at least one resource) and the **dangerous path** (all harmful actions; must
   stay penalised and unresolved). Each path lists its checks and an expandable step table.
   Skipped while validation has errors.
3. **Quality score** — 0–100 across validity (25), tests (25), coverage (20), pedagogy (20),
   structure (10), each with a progress bar and justification, plus a grade:
   ≥ 85 `EXCELLENT`, ≥ 70 `GOOD`, ≥ 50 `FAIR`, else `POOR`.

**To publish:** fix all blockers listed in the publish panel (no validation errors, both paths
passed, score ≥ 70, draft saved), optionally write a change note, press **Publish as version N**.
The gates are re-checked on the server against the stored draft, so the UI cannot be bypassed.

**Expected result:** an immutable version (definition + quality report + change note + author) and
the scenario shows `PUBLISHED vN`. The three seed scenarios arrive already published as version 1.

## 6. Versions

The *Versions* tab lists published versions newest first (number, date, author, quality score,
change note). Opening one shows the frozen definition summary and its quality breakdown.
**Restore** copies a version back into the editable draft (the version itself is never modified) —
publish again to make it live.

## 7. Archive and delete

- **Archive** (scenario list): hides the scenario from consumers; restoring makes it an editable
  `DRAFT` again. Archived scenarios cannot be published until restored.
- **Delete**: only drafts that have **never been published** can be deleted (confirmation dialog).
  A published scenario must be archived instead, so its versions remain for the learner module.

## 8. Student exams and the AI review

The **Student exams** page lists every attempt the learner module has submitted. For each row you see the
claimed score next to the **verified** score this platform computed by replaying the submission, and a
`matches` / `MISMATCH` chip — a mismatch means the learner module reported a grade our deterministic replay
contradicts.

To review an exam with AI:
1. Open the attempt. The left card is the verified result (score, raw points, hints, evidence, missed expected
   actions); the table below lists every submitted action with its assessment.
2. Press **Run AI review**. The AI receives the scenario (including the solution) and the verified replay —
   never the student's free text — and returns an advisory rating out of 100, a message for the student,
   what was done well, the mistakes, and study recommendations.
3. The review is stored with its source (`Claude` or the offline reviewer when no API key is configured) and
   the learner module can fetch it from `GET /api/learner/attempts/{externalId}`. Pressing the button again
   replaces the review.

The AI rating is advisory; the verified score is the grade and the AI cannot change it.

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| Publish button disabled | A blocker is listed above it: unsaved changes, validation errors, a failing path, score < 70, or the scenario is archived. |
| Generation rejects input | Field rules in §2; the server reports the same errors per field. |
| AI polish returns `FALLBACK` | No `ANTHROPIC_API_KEY`, timeout, or the AI changed the structure; the deterministic draft is kept — safe to continue. |
| Translate shows the English text | Offline (`mock`) provider; set `AI_PROVIDER=claude` for real translation. |
