--liquibase formatted sql

--changeset platform:010-platform-tenant-identity
CREATE TABLE public.tenant_identity (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    tenant_id UUID NOT NULL
);

COMMENT ON TABLE public.tenant_identity IS
    'Provisioning writes one Workspace id; runtime users may SELECT only (platform decision R6).';

-- Development databases only: rollback removes the identity used by the row guard.
--rollback DROP TABLE public.tenant_identity;
