--liquibase formatted sql

--changeset identity:026-identity-app-user-keycloak-sub
ALTER TABLE app_user ADD COLUMN keycloak_sub VARCHAR(64) NULL;

CREATE UNIQUE INDEX uq_app_user_keycloak_sub ON app_user (keycloak_sub);

--rollback DROP INDEX uq_app_user_keycloak_sub;
--rollback ALTER TABLE app_user DROP COLUMN keycloak_sub;
