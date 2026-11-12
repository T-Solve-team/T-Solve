--liquibase formatted sql

--changeset workspace:028-workspace-department-name-lower-unique
CREATE UNIQUE INDEX uq_department_name_lower ON department (lower(name));

--rollback DROP INDEX uq_department_name_lower;
