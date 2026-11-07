--liquibase formatted sql

--changeset tracker:017-tracker-suggestion-job-input

-- Tracker STORY-011 on the worker (KAN-102): a worker job carries only a record UUID and no ticket content, so the
-- issue text lives in the row and job_ref is what the job carries. Rows recorded before this get a job_ref and no
-- text. Triage's read-only view of this table does not map the new columns.
ALTER TABLE suggestion_result ADD COLUMN job_ref UUID;
UPDATE suggestion_result SET job_ref = gen_random_uuid() WHERE job_ref IS NULL;
-- A default too, so writers that do not know the column (triage's test seeds, raw SQL) still insert rows.
ALTER TABLE suggestion_result ALTER COLUMN job_ref SET DEFAULT gen_random_uuid();
ALTER TABLE suggestion_result ALTER COLUMN job_ref SET NOT NULL;
ALTER TABLE suggestion_result ADD CONSTRAINT uq_suggestion_job_ref UNIQUE (job_ref);
ALTER TABLE suggestion_result ADD COLUMN issue_summary TEXT;
ALTER TABLE suggestion_result ADD COLUMN issue_description TEXT;
