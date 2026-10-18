--liquibase formatted sql

--changeset core:001-init-core-schema
-- A database first built by the old Flyway V1 script already has this schema: mark the changeset as run.
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'workspace'

-- T-Solve core schema (MVP). T-Solve stores snapshots of resolved tickets and owns the Solution lifecycle;
-- the ticket lifecycle itself stays in the source ticket tracker.

CREATE TABLE workspace (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    domain      VARCHAR(50)  NOT NULL,           -- IT, HR, FINANCE, ASSET, ...
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE resolved_ticket_snapshot (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT       NOT NULL REFERENCES workspace (id),
    source         VARCHAR(50)  NOT NULL,          -- JIRA, CSV, MANUAL, ...
    external_id    VARCHAR(100) NOT NULL,
    source_url     VARCHAR(500),
    title          VARCHAR(500) NOT NULL,
    description    TEXT,
    ticket_type    VARCHAR(100),
    category       VARCHAR(100),
    source_status  VARCHAR(50),                    -- metadata only, not owned by T-Solve
    resolution     TEXT,
    resolver       VARCHAR(150),
    resolved_at    TIMESTAMPTZ,
    imported_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_snapshot_source_external UNIQUE (source, external_id)   -- import idempotency
);
CREATE INDEX idx_snapshot_workspace ON resolved_ticket_snapshot (workspace_id);

CREATE TABLE solution (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT       NOT NULL REFERENCES workspace (id),
    title          VARCHAR(500) NOT NULL,
    symptoms       TEXT,
    root_cause     TEXT,
    steps          TEXT,
    workaround     TEXT,
    applicability  TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    review_due_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_solution_workspace_status ON solution (workspace_id, status);

CREATE TABLE solution_source (
    solution_id  BIGINT NOT NULL REFERENCES solution (id) ON DELETE CASCADE,
    snapshot_id  BIGINT NOT NULL REFERENCES resolved_ticket_snapshot (id),
    PRIMARY KEY (solution_id, snapshot_id)
);

CREATE TABLE solution_approval (
    id           BIGSERIAL PRIMARY KEY,
    solution_id  BIGINT      NOT NULL REFERENCES solution (id),
    reviewer     VARCHAR(150) NOT NULL,
    decision     VARCHAR(30) NOT NULL,             -- APPROVED, REJECTED, CHANGES_REQUESTED
    reason       TEXT,
    decided_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reuse_record (
    id                  BIGSERIAL PRIMARY KEY,
    solution_id         BIGINT       NOT NULL REFERENCES solution (id),
    external_ticket_ref VARCHAR(200),
    used_by             VARCHAR(150),
    helpful             BOOLEAN,
    feedback            TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_event (                          -- append-only
    id         BIGSERIAL PRIMARY KEY,
    actor      VARCHAR(150) NOT NULL,
    action     VARCHAR(100) NOT NULL,
    resource   VARCHAR(200) NOT NULL,
    result     VARCHAR(30)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Seed workspaces for the demo domains
INSERT INTO workspace (name, domain) VALUES
    ('IT Support', 'IT'), ('Human Resources', 'HR'), ('Finance', 'FINANCE'), ('Asset Management', 'ASSET');
