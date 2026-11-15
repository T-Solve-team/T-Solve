--liquibase formatted sql

-- KAN-137: the Solution tables of changeset 001; nothing reads or writes them since the ticket model is one. No
-- CASCADE: anything still referencing them must fail this changeset. The rollback brings back empty tables only (for
-- the workspace migration test, BUG-243); their rows come back only from a backup.
--changeset ticket:039-drop-legacy-solution-tables
DROP TABLE solution_source, solution_approval, reuse_record, solution, resolved_ticket_snapshot;
--rollback CREATE TABLE resolved_ticket_snapshot (id BIGSERIAL PRIMARY KEY, workspace_id BIGINT NOT NULL REFERENCES legacy_workspace (id), source VARCHAR(50) NOT NULL, external_id VARCHAR(100) NOT NULL, source_url VARCHAR(500), title VARCHAR(500) NOT NULL, description TEXT, ticket_type VARCHAR(100), category VARCHAR(100), source_status VARCHAR(50), resolution TEXT, resolver VARCHAR(150), resolved_at TIMESTAMPTZ, imported_at TIMESTAMPTZ NOT NULL DEFAULT now(), CONSTRAINT uq_snapshot_source_external UNIQUE (source, external_id));
--rollback CREATE INDEX idx_snapshot_workspace ON resolved_ticket_snapshot (workspace_id);
--rollback CREATE TABLE solution (id BIGSERIAL PRIMARY KEY, workspace_id BIGINT NOT NULL REFERENCES legacy_workspace (id), title VARCHAR(500) NOT NULL, symptoms TEXT, root_cause TEXT, steps TEXT, workaround TEXT, applicability TEXT, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', review_due_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
--rollback CREATE INDEX idx_solution_workspace_status ON solution (workspace_id, status);
--rollback CREATE TABLE solution_source (solution_id BIGINT NOT NULL REFERENCES solution (id) ON DELETE CASCADE, snapshot_id BIGINT NOT NULL REFERENCES resolved_ticket_snapshot (id), PRIMARY KEY (solution_id, snapshot_id));
--rollback CREATE TABLE solution_approval (id BIGSERIAL PRIMARY KEY, solution_id BIGINT NOT NULL REFERENCES solution (id), reviewer VARCHAR(150) NOT NULL, decision VARCHAR(30) NOT NULL, reason TEXT, decided_at TIMESTAMPTZ NOT NULL DEFAULT now());
--rollback CREATE TABLE reuse_record (id BIGSERIAL PRIMARY KEY, solution_id BIGINT NOT NULL REFERENCES solution (id), external_ticket_ref VARCHAR(200), used_by VARCHAR(150), helpful BOOLEAN, feedback TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
