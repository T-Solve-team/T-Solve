--liquibase formatted sql

--changeset review:019-review-queue-notify splitStatements:false

-- Tells the Review Queue event stream that a Project's queue changed. The payload is only the Project id; NOTIFY is
-- transactional (a rollback sends nothing) and de-duplicates identical payloads inside one transaction.
CREATE FUNCTION notify_review_queue() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.source = 'SOLUTION_FORM' AND NEW.status = 'PENDING_REVIEW' THEN
            PERFORM pg_notify('review_queue', NEW.project_id::text);
        END IF;
        RETURN NULL;
    END IF;

    IF NEW.source = 'SOLUTION_FORM' AND NEW.status = 'PENDING_REVIEW' AND OLD.status IS DISTINCT FROM NEW.status THEN
        PERFORM pg_notify('review_queue', NEW.project_id::text);
    ELSIF OLD.status = 'PENDING_REVIEW' AND NEW.status IS DISTINCT FROM OLD.status THEN
        PERFORM pg_notify('review_queue', OLD.project_id::text);
    ELSIF NEW.source = 'SOLUTION_FORM' AND NEW.status = 'PENDING_REVIEW'
        AND OLD.project_id IS DISTINCT FROM NEW.project_id THEN
        PERFORM pg_notify('review_queue', OLD.project_id::text);
        PERFORM pg_notify('review_queue', NEW.project_id::text);
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER ticket_notify_review_queue
AFTER INSERT OR UPDATE OF status, project_id ON ticket
FOR EACH ROW EXECUTE FUNCTION notify_review_queue();
