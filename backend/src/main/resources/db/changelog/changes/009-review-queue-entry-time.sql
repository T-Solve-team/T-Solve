--liquibase formatted sql

--changeset review:009-review-queue-entry-time splitStatements:false

-- Historical entries have no reliable timestamp. Leave them null until a new entry.
ALTER TABLE ticket ADD COLUMN review_queue_entered_at TIMESTAMPTZ;

CREATE FUNCTION record_review_queue_entry() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.source = 'SOLUTION_FORM' AND NEW.status = 'PENDING_REVIEW'
        AND (TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM NEW.status) THEN
        NEW.review_queue_entered_at := statement_timestamp();
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER ticket_record_review_queue_entry
BEFORE INSERT OR UPDATE OF status ON ticket
FOR EACH ROW EXECUTE FUNCTION record_review_queue_entry();
