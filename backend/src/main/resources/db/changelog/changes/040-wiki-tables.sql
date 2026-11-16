--liquibase formatted sql

--changeset wiki:040-wiki-tables
CREATE TABLE space_permission (
    space_permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id BIGINT NOT NULL REFERENCES space (space_id),
    department_id BIGINT REFERENCES department (department_id),
    user_id UUID REFERENCES app_user (id),
    granted_by UUID NOT NULL REFERENCES app_user (id),
    granted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_space_permission_one_grantee CHECK ((department_id IS NULL) <> (user_id IS NULL))
);
CREATE UNIQUE INDEX uq_space_permission_department ON space_permission (space_id, department_id) WHERE department_id IS NOT NULL;
CREATE UNIQUE INDEX uq_space_permission_user ON space_permission (space_id, user_id) WHERE user_id IS NOT NULL;

CREATE TABLE folder (
    folder_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id BIGINT NOT NULL REFERENCES space (space_id),
    parent_id UUID REFERENCES folder (folder_id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    level SMALLINT NOT NULL CHECK (level BETWEEN 1 AND 3),
    created_by UUID NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_folder_parent_name ON folder (space_id, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(name));

CREATE TABLE wiki_document (
    wiki_document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id BIGINT NOT NULL REFERENCES space (space_id),
    folder_id UUID REFERENCES folder (folder_id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL,
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('DEPARTMENT', 'COMPANY_WIDE')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PUBLISHED', 'DELETED')),
    content TEXT NOT NULL,
    file_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    word_count INTEGER NOT NULL,
    index_status VARCHAR(20) NOT NULL CHECK (index_status IN ('PENDING', 'INDEXED', 'FAILED')),
    owner_id UUID NOT NULL REFERENCES app_user (id),
    deleted_by UUID REFERENCES app_user (id),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    -- A tsvector is capped at 1 MB; distinct ASCII words take ~1.7 bytes per character, Vietnamese up to ~3x that,
    -- so only the first 150 000 characters are keyword-indexed (BUG-291): worst case ~765 KB, well under the cap.
    -- Passage embeddings cover the full text.
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', left(title || ' ' || content, 150000))) STORED
);
CREATE UNIQUE INDEX uq_wiki_document_published_title
    ON wiki_document (space_id, COALESCE(folder_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(title))
    WHERE status = 'PUBLISHED';
CREATE INDEX ix_wiki_document_search ON wiki_document USING GIN (search_vector);
CREATE INDEX ix_wiki_document_space_status ON wiki_document (space_id, status);

CREATE TABLE wiki_chunk (
    wiki_chunk_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wiki_document_id UUID NOT NULL REFERENCES wiki_document (wiki_document_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    text TEXT NOT NULL,
    embedding vector(1024) NOT NULL,
    model VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_wiki_chunk_position UNIQUE (wiki_document_id, position)
);
-- ponytail: exact scans suffice for hundreds of documents; consider HNSW above 50,000 chunks per Workspace.

--rollback DROP TABLE wiki_chunk;
--rollback DROP TABLE wiki_document;
--rollback DROP TABLE folder;
--rollback DROP TABLE space_permission;
