-- T-Solve tenant database (one per Workspace). PostgreSQL.
-- Source: Report/Sua_31-10/ERD_T-Solve_31-10.drawio (23 tenant tables). Business tables carry no workspace_id.
-- Run once per company database, e.g. on tsolve_fpt.

-- ===== Organisation =====

CREATE TABLE workspace (                             -- exactly one row per tenant database
    workspace_id                BIGINT       PRIMARY KEY,   -- same value as tenant_workspace.workspace_id in catalog
    company_name                VARCHAR(150) NOT NULL,
    sign_in_provider            VARCHAR(30)  NOT NULL,      -- GOOGLE, MICROSOFT, ...
    jira_site_address           VARCHAR(255),
    service_account_email       VARCHAR(254),
    service_account_api_token   TEXT,                       -- encrypted by the application
    token_expiry_date           DATE,
    singleton                   BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_workspace_singleton UNIQUE (singleton),
    CONSTRAINT ck_workspace_singleton CHECK (singleton)
);

CREATE TABLE department (
    department_id       BIGSERIAL PRIMARY KEY,
    default_project_id  BIGINT,                      -- FK added after project; NULL = no Chatbot issues
    name                VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_department_name UNIQUE (name)
);

CREATE TABLE app_user (                              -- "user" is a reserved word in PostgreSQL
    user_id                 BIGSERIAL PRIMARY KEY,
    department_id           BIGINT       REFERENCES department (department_id),
    full_name               VARCHAR(150) NOT NULL,
    company_email           VARCHAR(254) NOT NULL,
    linked_jira_account_id  VARCHAR(128),
    account_status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_user_email        UNIQUE (company_email),
    CONSTRAINT uq_user_jira_account UNIQUE (linked_jira_account_id)
);
CREATE INDEX idx_user_department ON app_user (department_id);

CREATE TABLE user_role (                             -- Employee is implicit, not stored
    user_role_id   BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    role           VARCHAR(30) NOT NULL,
    date_assigned  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_role UNIQUE (user_id, role),
    CONSTRAINT ck_user_role CHECK (role IN ('TICKET_RESOLVER', 'PROJECT_MANAGER', 'KNOWLEDGE_MANAGER', 'ADMIN'))
);

-- ===== Jira project and ticket =====

CREATE TABLE project (                               -- never deleted
    project_id        BIGSERIAL PRIMARY KEY,
    department_id     BIGINT       NOT NULL REFERENCES department (department_id),
    jira_project_key  VARCHAR(20)  NOT NULL,
    field_mapping     JSONB,
    done_status       VARCHAR(100),
    review_range      INT,                           -- days allowed for review
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_project_jira_key UNIQUE (jira_project_key)
);
CREATE INDEX idx_project_department ON project (department_id);

ALTER TABLE department
    ADD CONSTRAINT fk_department_default_project
    FOREIGN KEY (default_project_id) REFERENCES project (project_id);

CREATE TABLE project_membership (
    user_id          BIGINT      NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    project_id       BIGINT      NOT NULL REFERENCES project (project_id),
    role_in_project  VARCHAR(30) NOT NULL,
    date_added       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, project_id)
);
CREATE INDEX idx_membership_project ON project_membership (project_id);

CREATE TABLE import_history (                        -- never updated or deleted
    import_history_id  BIGSERIAL PRIMARY KEY,
    performed_by       BIGINT       NOT NULL REFERENCES app_user (user_id),
    source             VARCHAR(20)  NOT NULL,        -- JIRA, FILE
    date_range_from    DATE,                         -- Jira import
    date_range_to      DATE,
    file_name          VARCHAR(255),                 -- file import
    tickets_published  INT          NOT NULL DEFAULT 0,
    removed_count      INT          NOT NULL DEFAULT 0,   -- removed issues (Jira) / error rows (file)
    errors             TEXT,
    status             VARCHAR(20)  NOT NULL,        -- COMPLETED, FAILED
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_import_source CHECK (source IN ('JIRA', 'FILE'))
);

CREATE TABLE ticket (
    ticket_id               BIGSERIAL PRIMARY KEY,
    project_id              BIGINT       NOT NULL REFERENCES project (project_id),
    writer_id               BIGINT       REFERENCES app_user (user_id),           -- NULL for imported tickets
    import_history_id       BIGINT       REFERENCES import_history (import_history_id),
    source                  VARCHAR(20)  NOT NULL,   -- SOLUTION_FORM, JIRA_IMPORT, FILE_IMPORT
    title                   VARCHAR(200) NOT NULL,
    description             TEXT,
    symptoms                TEXT         NOT NULL,
    root_cause              TEXT         NOT NULL,
    resolution_steps        TEXT         NOT NULL,
    workaround              TEXT,
    scope                   TEXT         NOT NULL,
    status                  VARCHAR(20)  NOT NULL,   -- DRAFT, PENDING_REVIEW, PUBLISHED, REJECTED, ...
    review_due_date         DATE,
    rejection_reason        TEXT,
    change_request_comment  TEXT,
    source_link             VARCHAR(500),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_project_status ON ticket (project_id, status);
CREATE INDEX idx_ticket_writer ON ticket (writer_id);
CREATE INDEX idx_ticket_import ON ticket (import_history_id);

CREATE TABLE evidence (                              -- max 5 per ticket, enforced by the application
    evidence_id  BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL REFERENCES ticket (ticket_id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    file_type    VARCHAR(100) NOT NULL,
    file_size    BIGINT       NOT NULL,              -- bytes
    upload_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_evidence_size CHECK (file_size >= 0)
);
CREATE INDEX idx_evidence_ticket ON evidence (ticket_id);

CREATE TABLE review_decision (
    decision_id         BIGSERIAL PRIMARY KEY,
    ticket_id           BIGINT      NOT NULL REFERENCES ticket (ticket_id),
    project_manager_id  BIGINT      NOT NULL REFERENCES app_user (user_id),
    decision            VARCHAR(20) NOT NULL,
    reason_or_comment   TEXT,
    decided_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_review_decision CHECK (decision IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED'))
);
CREATE INDEX idx_review_ticket ON review_decision (ticket_id);

CREATE TABLE draft (
    draft_id         BIGSERIAL PRIMARY KEY,
    creator_id       BIGINT       NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    ticket_id        BIGINT       REFERENCES ticket (ticket_id) ON DELETE SET NULL,  -- "saved for"; not a column on the ERD
    type             VARCHAR(30)  NOT NULL,
    jira_issue_keys  VARCHAR(50)[] NOT NULL DEFAULT '{}',
    saved_content    JSONB        NOT NULL,
    date_saved       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_draft_creator ON draft (creator_id);

CREATE TABLE feedback (
    feedback_id  BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT      NOT NULL REFERENCES ticket (ticket_id),
    user_id      BIGINT      NOT NULL REFERENCES app_user (user_id),
    rating       SMALLINT    NOT NULL,
    comment      TEXT,
    channel      VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_feedback_channel CHECK (channel IN ('WEB', 'ISSUE_PANEL'))
);
CREATE INDEX idx_feedback_ticket ON feedback (ticket_id);

-- ===== AI Solution Suggestion =====

CREATE TABLE suggestion_result (                     -- one result per Jira issue (GBR-36, Forge retries)
    suggestion_id       BIGSERIAL PRIMARY KEY,
    project_id          BIGINT      NOT NULL REFERENCES project (project_id),
    jira_issue_key      VARCHAR(50) NOT NULL,
    sources_used        JSONB       NOT NULL DEFAULT '[]',
    suggested_solution  TEXT,
    reporter_answer     VARCHAR(5),                  -- NULL until reporter answers
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_suggestion_issue UNIQUE (jira_issue_key),
    CONSTRAINT ck_suggestion_answer CHECK (reporter_answer IN ('YES', 'NO'))
);
CREATE INDEX idx_suggestion_project ON suggestion_result (project_id, created_at);

-- ===== Search and audit =====

CREATE TABLE search_log (
    search_log_id      BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES app_user (user_id),
    department_id      BIGINT      REFERENCES department (department_id),
    query              TEXT        NOT NULL,
    channel            VARCHAR(20) NOT NULL,
    number_of_results  INT         NOT NULL,         -- 0 = knowledge gap
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_search_channel CHECK (channel IN ('WEB', 'ISSUE_PANEL', 'CHATBOT'))
);
CREATE INDEX idx_search_log_created ON search_log (created_at);
CREATE INDEX idx_search_log_gap ON search_log (department_id, created_at) WHERE number_of_results = 0;

CREATE TABLE audit_log_entry (                       -- append only
    audit_entry_id  BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT       REFERENCES app_user (user_id),   -- NULL = system / job
    action          VARCHAR(50)  NOT NULL,
    target          VARCHAR(255) NOT NULL,
    details         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_log_entry (created_at);
CREATE INDEX idx_audit_actor ON audit_log_entry (actor_id);

-- ===== Knowledge Chatbot =====

CREATE TABLE chat_session (
    chat_session_id  BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    start_time       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_session_user ON chat_session (user_id);

CREATE TABLE chat_message (                          -- deleted when the session ends (UC-48)
    message_id       BIGSERIAL PRIMARY KEY,
    chat_session_id  BIGINT      NOT NULL REFERENCES chat_session (chat_session_id) ON DELETE CASCADE,
    question         TEXT        NOT NULL,
    answer           TEXT,
    citations        JSONB       NOT NULL DEFAULT '[]',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_message_session ON chat_message (chat_session_id, created_at);

CREATE TABLE chat_feedback (                         -- kept forever (UC-49); copies question and answer
    chat_feedback_id  BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (user_id),
    question          TEXT        NOT NULL,
    answer            TEXT        NOT NULL,
    rating            SMALLINT    NOT NULL,
    comment           TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ===== Wiki (no versions, no review: upload / overwrite publishes at once) =====

CREATE TABLE space (                                 -- 1-1 with department
    space_id       BIGSERIAL PRIMARY KEY,
    department_id  BIGINT       NOT NULL REFERENCES department (department_id),
    name           VARCHAR(100) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_space_department UNIQUE (department_id)
);

CREATE TABLE space_manager (
    space_id       BIGINT      NOT NULL REFERENCES space (space_id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,  -- Knowledge Manager
    date_assigned  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (space_id, user_id)
);

CREATE TABLE space_permission (
    permission_id           BIGSERIAL PRIMARY KEY,
    space_id                BIGINT      NOT NULL REFERENCES space (space_id) ON DELETE CASCADE,
    grantee_department_id   BIGINT      REFERENCES department (department_id) ON DELETE CASCADE,
    grantee_user_id         BIGINT      REFERENCES app_user (user_id) ON DELETE CASCADE,
    level                   VARCHAR(10) NOT NULL,
    date_granted            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_permission_level   CHECK (level IN ('VIEW', 'CREATE')),
    CONSTRAINT ck_permission_grantee CHECK (num_nonnulls(grantee_department_id, grantee_user_id) = 1)
);
CREATE UNIQUE INDEX uq_permission_department ON space_permission (space_id, grantee_department_id)
    WHERE grantee_department_id IS NOT NULL;
CREATE UNIQUE INDEX uq_permission_user ON space_permission (space_id, grantee_user_id)
    WHERE grantee_user_id IS NOT NULL;

CREATE TABLE folder (                                -- max 3 levels deep, enforced by the application
    folder_id         BIGSERIAL PRIMARY KEY,
    space_id          BIGINT       NOT NULL REFERENCES space (space_id) ON DELETE CASCADE,
    parent_folder_id  BIGINT       REFERENCES folder (folder_id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL,
    CONSTRAINT ck_folder_not_self CHECK (parent_folder_id <> folder_id)
);
CREATE INDEX idx_folder_space ON folder (space_id);
CREATE INDEX idx_folder_parent ON folder (parent_folder_id);

CREATE TABLE wiki_document (
    document_id      BIGSERIAL PRIMARY KEY,
    space_id         BIGINT       NOT NULL REFERENCES space (space_id),
    folder_id        BIGINT       REFERENCES folder (folder_id) ON DELETE SET NULL,   -- NULL = space root
    owner_id         BIGINT       NOT NULL REFERENCES app_user (user_id),
    title            VARCHAR(255) NOT NULL,
    visibility       VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    original_file    VARCHAR(500),                   -- storage path of the uploaded file
    content          TEXT,                           -- extracted text
    date_updated     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_wiki_status CHECK (status IN ('PUBLISHED', 'DELETED'))
);
CREATE INDEX idx_wiki_space_folder ON wiki_document (space_id, folder_id);
