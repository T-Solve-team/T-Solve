--liquibase formatted sql

--changeset search:009-search-ticket-updated-at splitStatements:false
ALTER TABLE ticket ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE ticket SET updated_at = created_at;

CREATE FUNCTION search_touch_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ticket_updated_at BEFORE UPDATE ON ticket
    FOR EACH ROW EXECUTE FUNCTION search_touch_updated_at();

--rollback DROP TRIGGER trg_ticket_updated_at ON ticket;
--rollback DROP FUNCTION search_touch_updated_at();
--rollback ALTER TABLE ticket DROP COLUMN updated_at;
