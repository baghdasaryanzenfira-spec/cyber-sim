-- =====================================================================
-- CyberSim schema (admin scenario-authoring platform)
-- Tables: users (admins), scenarios (+ children = editable draft),
--         scenario_versions (immutable published snapshots), ai_interactions (audit log).
-- The learner-facing simulation runtime lives in another team's module and is not part of this schema.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    last_login_at  TIMESTAMPTZ,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN')),
    CONSTRAINT ck_users_email_lower CHECK (email = LOWER(email))
);

-- ---------------------------------------------------------------------
-- Scenarios: the editable working copy (draft). Published content is frozen in scenario_versions.
-- ---------------------------------------------------------------------
CREATE TABLE scenarios (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug                  VARCHAR(100) NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    summary               VARCHAR(500) NOT NULL,
    description           TEXT         NOT NULL,
    difficulty            VARCHAR(20)  NOT NULL,
    category              VARCHAR(30)  NOT NULL,
    estimated_minutes     INT          NOT NULL,
    incident_explanation  TEXT         NOT NULL,
    recommended_solution  TEXT         NOT NULL,
    hint_penalty          INT          NOT NULL DEFAULT 2,
    out_of_order_penalty  INT          NOT NULL DEFAULT 5,
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    revision              INT          NOT NULL DEFAULT 1,
    published_version     INT,
    source_scenario_id    BIGINT       REFERENCES scenarios (id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_scenarios_slug UNIQUE (slug),
    CONSTRAINT ck_scenarios_difficulty CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_scenarios_category CHECK (category IN
        ('AUTHENTICATION', 'IAM', 'NETWORK', 'STORAGE', 'LOGGING', 'INCIDENT_RESPONSE')),
    CONSTRAINT ck_scenarios_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_scenarios_minutes CHECK (estimated_minutes BETWEEN 1 AND 600),
    CONSTRAINT ck_scenarios_penalties CHECK (hint_penalty >= 0 AND out_of_order_penalty >= 0)
);
CREATE INDEX ix_scenarios_status ON scenarios (status);

CREATE TABLE scenario_objectives (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id  BIGINT       NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    position     INT          NOT NULL,
    text         VARCHAR(500) NOT NULL,
    CONSTRAINT uq_scenario_objectives UNIQUE (scenario_id, position)
);

-- Initial infrastructure state of the scenario.
CREATE TABLE scenario_resources (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id    BIGINT       NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    position       INT          NOT NULL,
    resource_key   VARCHAR(64)  NOT NULL,
    resource_type  VARCHAR(30)  NOT NULL,
    name           VARCHAR(200) NOT NULL,
    region         VARCHAR(40)  NOT NULL,
    status         VARCHAR(40)  NOT NULL,
    properties     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_scenario_resources_key UNIQUE (scenario_id, resource_key),
    CONSTRAINT ck_scenario_resources_type CHECK (resource_type IN
        ('VIRTUAL_MACHINE', 'IAM_USER', 'IAM_ROLE', 'ACCESS_KEY', 'STORAGE_BUCKET',
         'SECURITY_GROUP', 'DATABASE', 'LOAD_BALANCER'))
);

-- Incident timeline: log lines and alerts. revealed_by_action_key = NULL -> visible from the start.
CREATE TABLE scenario_events (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id             BIGINT       NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    position                INT          NOT NULL,
    event_key               VARCHAR(64)  NOT NULL,
    offset_seconds          INT          NOT NULL,
    event_type              VARCHAR(10)  NOT NULL,
    source                  VARCHAR(60)  NOT NULL,
    severity                VARCHAR(10)  NOT NULL,
    resource_key            VARCHAR(64),
    message                 TEXT         NOT NULL,
    details                 JSONB        NOT NULL DEFAULT '{}'::jsonb,
    evidence                BOOLEAN      NOT NULL DEFAULT FALSE,
    evidence_note           TEXT,
    revealed_by_action_key  VARCHAR(64),
    CONSTRAINT uq_scenario_events_key UNIQUE (scenario_id, event_key),
    CONSTRAINT ck_scenario_events_offset CHECK (offset_seconds >= 0),
    CONSTRAINT ck_scenario_events_type CHECK (event_type IN ('LOG', 'ALERT')),
    CONSTRAINT ck_scenario_events_severity CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- Action catalogue + scoring rules (points live on the action).
CREATE TABLE scenario_actions (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id              BIGINT        NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    position                 INT           NOT NULL,
    action_key               VARCHAR(64)   NOT NULL,
    label                    VARCHAR(200)  NOT NULL,
    description              VARCHAR(1000) NOT NULL,
    phase                    VARCHAR(20)   NOT NULL,
    category                 VARCHAR(20)   NOT NULL,
    target_resource_key      VARCHAR(64),
    outcome                  VARCHAR(10)   NOT NULL,
    points                   INT           NOT NULL,
    prerequisite_action_key  VARCHAR(64),
    effect_status            VARCHAR(40),
    result_message           TEXT          NOT NULL,
    explanation              TEXT          NOT NULL,
    CONSTRAINT uq_scenario_actions_key UNIQUE (scenario_id, action_key),
    CONSTRAINT ck_scenario_actions_phase CHECK (phase IN ('INVESTIGATION', 'RESPONSE')),
    CONSTRAINT ck_scenario_actions_category CHECK (category IN
        ('INSPECT', 'IDENTIFY', 'CONTAIN', 'ERADICATE', 'RECOVER', 'HARDEN')),
    CONSTRAINT ck_scenario_actions_outcome CHECK (outcome IN ('EXPECTED', 'NEUTRAL', 'HARMFUL')),
    -- the sign of the points must match the outcome
    CONSTRAINT ck_scenario_actions_points CHECK (
        (outcome = 'EXPECTED' AND points > 0) OR
        (outcome = 'NEUTRAL'  AND points = 0) OR
        (outcome = 'HARMFUL'  AND points < 0))
);

CREATE TABLE scenario_hints (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id  BIGINT        NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    position     INT           NOT NULL,
    text         VARCHAR(1000) NOT NULL,
    CONSTRAINT uq_scenario_hints UNIQUE (scenario_id, position)
);

-- ---------------------------------------------------------------------
-- Published versions: immutable snapshots of the full scenario definition
-- ---------------------------------------------------------------------
CREATE TABLE scenario_versions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id     BIGINT      NOT NULL REFERENCES scenarios (id) ON DELETE CASCADE,
    version_number  INT         NOT NULL,
    definition      JSONB       NOT NULL,
    quality_score   INT         NOT NULL,
    quality         JSONB       NOT NULL,
    change_note     VARCHAR(500),
    published_by    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    published_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_scenario_versions UNIQUE (scenario_id, version_number),
    CONSTRAINT ck_scenario_versions_score CHECK (quality_score BETWEEN 0 AND 100)
);

-- ---------------------------------------------------------------------
-- AI audit log
-- ---------------------------------------------------------------------
CREATE TABLE ai_interactions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scenario_id       BIGINT      REFERENCES scenarios (id) ON DELETE SET NULL,
    interaction_type  VARCHAR(20) NOT NULL,
    provider          VARCHAR(20) NOT NULL,
    model             VARCHAR(60),
    status            VARCHAR(20) NOT NULL,
    request_text      TEXT,
    response_text     TEXT        NOT NULL,
    latency_ms        INT         NOT NULL,
    input_tokens      INT,
    output_tokens     INT,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ai_type CHECK (interaction_type IN ('VARIATION', 'GENERATION', 'TRANSLATION')),
    CONSTRAINT ck_ai_provider CHECK (provider IN ('CLAUDE', 'MOCK')),
    CONSTRAINT ck_ai_status CHECK (status IN ('SUCCESS', 'FALLBACK', 'ERROR'))
);
CREATE INDEX ix_ai_interactions_created ON ai_interactions (created_at);
