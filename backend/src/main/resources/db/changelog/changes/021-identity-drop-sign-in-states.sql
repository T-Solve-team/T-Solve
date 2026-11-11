--liquibase formatted sql

--changeset identity:020-identity-drop-sign-in-states

-- The custom sign-in is gone (Keycloak keeps the sign-in state in Redis), so the table has no reader. Throwaway data.
-- IF EXISTS: a database that never ran 004 and 005 migrates too.

DROP TABLE IF EXISTS sign_in_states;
