--liquibase formatted sql

--changeset identity:021-identity-drop-hand-off-code

-- The hand-off from login. to a Workspace site is gone (the Workspace-site callback signs the Account in directly).
-- Throwaway data. IF EXISTS: a database that never ran 007 migrates too.

DROP TABLE IF EXISTS hand_off_code;
