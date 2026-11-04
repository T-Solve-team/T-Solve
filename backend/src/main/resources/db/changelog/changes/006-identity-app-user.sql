--liquibase formatted sql

--changeset identity:006-identity-app-user

-- One row per person's Account in one company database (identity STORY-001). A tenant table: no workspace_id,
-- because each Workspace has its own database. The email is stored lower-cased and is unique inside the database.
-- The sign-in writes no role row (the user_role table comes in Task 10).

CREATE TABLE app_user (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email              VARCHAR(254)  NOT NULL,
    full_name          VARCHAR(100),
    status             VARCHAR(20)   NOT NULL,        -- ACTIVE, DEACTIVATED
    first_sign_in_at   TIMESTAMPTZ,
    last_sign_in_at    TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_app_user_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DEACTIVATED'))
);

CREATE UNIQUE INDEX uq_app_user_email ON app_user (email);
