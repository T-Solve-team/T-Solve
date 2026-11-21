--liquibase formatted sql

--changeset wiki:049-wiki-document-type
ALTER TABLE wiki_document
    ADD COLUMN document_type varchar(20) NOT NULL DEFAULT 'OTHER',
    ADD CONSTRAINT ck_wiki_document_type CHECK (document_type IN
        ('POLICY', 'RUNBOOK', 'GUIDE', 'FAQ', 'REFERENCE', 'SPEC', 'RECORD', 'ANNOUNCEMENT', 'FORM', 'OTHER'));
