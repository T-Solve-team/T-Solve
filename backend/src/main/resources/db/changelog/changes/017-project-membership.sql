--liquibase formatted sql

--changeset review:017-project-membership

-- Who manages or works on a Project (database/02_tenant.sql). References are UUIDs because app_user.id and
-- project.public_id are; identity administers the rows (UC-05, UC-16), Review reads them.
CREATE TABLE project_membership (
    user_id          UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    project_id       UUID        NOT NULL REFERENCES project (public_id),
    role_in_project  VARCHAR(30) NOT NULL,
    date_added       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, project_id),
    CONSTRAINT ck_membership_role CHECK (role_in_project IN ('PROJECT_MANAGER', 'MEMBER'))
);
CREATE INDEX idx_membership_project ON project_membership (project_id);

--rollback DROP TABLE project_membership;
