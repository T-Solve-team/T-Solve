--liquibase formatted sql

--changeset identity:005-identity-sign-in-state-binding

-- Binds a sign-in attempt to the browser that started it (identity AC-001.44). authorize sets both columns once, in
-- one conditional UPDATE; the callback compares the hash with the __Host-TSOLVE_SIGNIN cookie it receives.
-- browser_binding_hash is the SHA-256 of the cookie value; the cookie value itself is never stored.

ALTER TABLE sign_in_states
    ADD COLUMN authorized_at         TIMESTAMPTZ,
    ADD COLUMN browser_binding_hash  BYTEA,
    ADD CONSTRAINT ck_sign_in_states_binding CHECK (
        (authorized_at IS NULL AND browser_binding_hash IS NULL)
        OR (authorized_at IS NOT NULL AND browser_binding_hash IS NOT NULL)
    );
