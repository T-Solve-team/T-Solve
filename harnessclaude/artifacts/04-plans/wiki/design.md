---
module: wiki
date: 2026-11-13
owner: Dev
---

# Design: wiki

Spec: `artifacts/03-modules/wiki/spec.md` · Decisions: `artifacts/04-plans/wiki/iteration-1/kan-91-decisions.md` (W1 to W30) · Architecture: `artifacts/03-modules/architecture.md`

## Architecture Overview

Backend package `vn.tsolve.wiki`, layered as `backend/AGENTS.md` requires (controller, service + impl, repository, entity, dto). Wiki writes only its own four tables. It reads the identity and workspace tables with native read-only SQL, the precedent of `identity/repository/ScopeAccessRepository`, `review/repository/ProjectMembershipRepository` and `search/retrieval/repository/SolutionRetrievalRepository` (W10).

```
web app (features/wiki) ──REST──> wiki controllers ──> wiki services ──> wiki repositories ──> tenant DB
                                                    │                    └─ AccessRepository (read only: space, space_manager,
                                                    │                       department, app_user, user_role, project_membership, project)
                                                    ├─> TextExtractor (PDFBox, POI)
                                                    ├─> FileStorage (platform, KAN-96)
                                                    ├─> AuditService (common)
                                                    ├─> EmbeddingClient (ai) ─ search query only
                                                    └─> TenantJobService (worker) ─ after commit
worker ──> WikiIndexJobHandler ──> EmbeddingClient ──> wiki_chunk
chatbot ──> WikiSources (impl in wiki)       triage ──> WikiSourceRetrieval, WikiDocumentAccess (impl in wiki)
identity/common session ──> ManagedSpaceLookup, ViewableSpaceLookup (impl in wiki)
```

### The view rule in one SQL fragment

Every read goes through one fragment, `AccessSql.VIEWABLE_SPACE` (a `static final String` in `AccessRepository`), so the rule exists once (Non-Functional 1). Parameters: `:userId`.

```sql
-- space s is viewable by :userId
s.status = 'ACTIVE'
AND EXISTS (SELECT 1 FROM department d WHERE d.department_id = s.department_id AND d.status = 'ACTIVE')
AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = :userId AND u.status = 'ACTIVE' AND (
      EXISTS (SELECT 1 FROM space_manager sm JOIN user_role r ON r.user_id = sm.user_id AND r.role = 'KNOWLEDGE_MANAGER'
              WHERE sm.space_id = s.space_id AND sm.user_id = u.id)
   OR s.department_id = u.department_id
   OR EXISTS (SELECT 1 FROM space_permission p WHERE p.space_id = s.space_id AND p.user_id = u.id)
   OR EXISTS (SELECT 1 FROM space_permission p WHERE p.space_id = s.space_id AND p.department_id IN (
          SELECT u.department_id
          UNION
          SELECT pr.department_id FROM project_membership m JOIN project pr ON pr.public_id = m.project_id
          WHERE m.user_id = u.id AND m.role_in_project = 'MEMBER'
            AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'TICKET_RESOLVER')))))
```

A document `w` is viewable when `w.status = 'PUBLISHED'` and its Space is Active with an Active Department and (`w.visibility = 'COMPANY_WIDE'` or the Space is viewable). `AccessSql.VIEWABLE_DOCUMENT` wraps this. The user's Departments count only when Active (AC-001.2): the implementation joins the `IN` list to `department` with `status = 'ACTIVE'`, so a Deleted Department's permission grants nothing.

Knowledge Manager check (`AccessSql.MANAGES_SPACE`): the `space_manager` row with the role and the user Active, the Space Active and its Department Active.

### Request flows

- **Upload**: `extract` reads each multipart file into memory once (10 MB cap), checks name and magic bytes (`%PDF-`; `PK\x03\x04` for DOCX), extracts text and answers. `publish` repeats the checks and extraction, validates titles, then stores each file with `FileStorage.store`, then opens one transaction for the rows and audit entries. If the transaction fails, the service deletes the stored keys (`FileStorage.delete`, errors logged) and rethrows (AC-004.11). After commit, one `TenantJobService.enqueue("wiki-index", documentId)` per document (`TransactionSynchronization.afterCommit`).
- **Indexing** (`WikiIndexJobHandler`, handler type `wiki-index`): load the document; not Published → return (AC-004.16). Split text (`PassageSplitter`), embed in batches of 16 with `EmbeddingClient.embed`, then in one transaction delete old chunks, insert the new ones, set `index_status = 'INDEXED'`. Any `RuntimeException` from the embedding → set `FAILED` in its own transaction, keep no new chunk (AC-004.15). No retry beyond what the worker does.
- **Search** (AC-002.7): embed `q` (catch failure → keyword only, AC-002.8). One query: viewable documents left-joined to the best chunk similarity (`MAX(1 - (c.embedding <=> :vector))` per document), filtered by `search_vector @@ websearch_to_tsquery('simple', :q)` OR similarity ≥ `:minSimilarity`, ordered by `ts_rank_cd(search_vector, query) + COALESCE(similarity, 0)` desc, then `title`, `LIMIT 50`.
- **Permanent delete**: delete row (chunks cascade) and audit in one transaction; `afterCommit` deletes the file; a failure is logged at WARN with the key (AC-005.12).

## Data Model

One changeset `0NN-wiki-tables.sql` (the next free number when the task starts) plus `0NN-wiki-audit-folder-change.sql` adding `FOLDER_CHANGE` to `ck_audit_log_entry_action` the way `027-workspace-audit-department-change.sql` does. Keys follow W12.

```sql
CREATE TABLE space_permission (
    space_permission_id UUID PRIMARY KEY,
    space_id            BIGINT NOT NULL REFERENCES space (space_id),
    department_id       BIGINT REFERENCES department (department_id),
    user_id             UUID REFERENCES app_user (id),
    granted_by          UUID NOT NULL REFERENCES app_user (id),
    granted_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_space_permission_one_grantee CHECK ((department_id IS NULL) <> (user_id IS NULL))
);
CREATE UNIQUE INDEX uq_space_permission_department ON space_permission (space_id, department_id) WHERE department_id IS NOT NULL;
CREATE UNIQUE INDEX uq_space_permission_user ON space_permission (space_id, user_id) WHERE user_id IS NOT NULL;

CREATE TABLE folder (
    folder_id  UUID PRIMARY KEY,
    space_id   BIGINT NOT NULL REFERENCES space (space_id),
    parent_id  UUID REFERENCES folder (folder_id) ON DELETE RESTRICT,
    name       VARCHAR(100) NOT NULL,
    level      SMALLINT NOT NULL CHECK (level BETWEEN 1 AND 3),
    created_by UUID NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
-- one name per parent, ignoring case; the top level uses a sentinel so NULL parents collide too
CREATE UNIQUE INDEX uq_folder_parent_name ON folder (space_id, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(name));

CREATE TABLE wiki_document (
    wiki_document_id UUID PRIMARY KEY,
    space_id         BIGINT NOT NULL REFERENCES space (space_id),
    folder_id        UUID REFERENCES folder (folder_id) ON DELETE RESTRICT,
    title            VARCHAR(200) NOT NULL,
    visibility       VARCHAR(20) NOT NULL CHECK (visibility IN ('DEPARTMENT', 'COMPANY_WIDE')),
    status           VARCHAR(20) NOT NULL CHECK (status IN ('PUBLISHED', 'DELETED')),
    content          TEXT NOT NULL,
    file_key         VARCHAR(255) NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    file_size        BIGINT NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    word_count       INTEGER NOT NULL,
    index_status     VARCHAR(20) NOT NULL CHECK (index_status IN ('PENDING', 'INDEXED', 'FAILED')),
    owner_id         UUID NOT NULL REFERENCES app_user (id),
    deleted_by       UUID REFERENCES app_user (id),
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    search_vector    TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', title || ' ' || content)) STORED
);
CREATE UNIQUE INDEX uq_wiki_document_published_title
    ON wiki_document (space_id, COALESCE(folder_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(title))
    WHERE status = 'PUBLISHED';
CREATE INDEX ix_wiki_document_search ON wiki_document USING GIN (search_vector);
CREATE INDEX ix_wiki_document_space_status ON wiki_document (space_id, status);

CREATE TABLE wiki_chunk (
    wiki_chunk_id    UUID PRIMARY KEY,
    wiki_document_id UUID NOT NULL REFERENCES wiki_document (wiki_document_id) ON DELETE CASCADE,
    position         INTEGER NOT NULL,
    text             TEXT NOT NULL,
    embedding        vector(1024) NOT NULL,
    model            VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_wiki_chunk_position UNIQUE (wiki_document_id, position)
);
```

No vector index: the Wiki of one Workspace holds hundreds of documents; an exact scan is fast (`ponytail:` comment, add HNSW when a Workspace passes ~50,000 chunks). Folder depth is stored (`level`) and recomputed for the moved sub-tree in the same transaction as the move.

Audit target types: `WIKI_FOLDER`, `WIKI_DOCUMENT`, `WIKI_SPACE` (the column is free text, 50 characters).

## Key Decisions

Every decision is in the decisions log with options and reasons. The ones that shape the code:

| Topic | Chosen | Alternatives | Rationale |
|-------|--------|--------------|-----------|
| File types, extraction | PDF and DOCX; PDFBox 3 + `poi-ooxml` (W2, W3) | Tika | Two formats, two direct libraries |
| Upload | Stateless extract, then publish with the files again (W4) | Temporary upload id | No temp storage to clean |
| Indexing | Background job per document, passages ≤ 1,000 chars, ≤ 200 per document, best passage per document (W5 to W7) | Inline; whole document | AI failures do not fail uploads (GBR-12) |
| Search | Full-text `simple` plus similarity, keyword-only fallback, top 50 (W8, W27) | One of them | Works without AI |
| View rule | One SQL fragment, read-only joins to identity tables; own Department computed (W9 to W11) | Lookup ports in identity | No identity change; one rule |
| Errors | 404 MSG110 for documents, 403 MSG08 for Spaces, 404 MSG04 for unknown Space or folder (W17, W18) | | Hide documents; same as identity's scope checks |
| Download | Stream through the backend (W19) | Signed URL | Permission checked per download |
| Size limit | Keep the global 413 MSG28; `max-request-size` 101 MB (W20, W21) | New handler | No change in `common/exception` |
| Ports | Implemented in wiki, stand-ins deleted (W22, W26) | | Callers declared them for wiki |
| Trash | Passages kept, filtered by status; restore re-indexes only if needed (W28) | Rebuild | No AI call on restore |

Decided in planning: the extraction runs in the request thread (at most 10 files of 10 MB); a PDF page count is not limited (`ponytail:` comment, add a page cap if a large PDF times out).

## File Structure

```
backend/src/main/java/vn/tsolve/wiki/
  controller/  WikiBrowseController, WikiDocumentController, WikiFolderController, WikiUploadController,
               WikiTrashController, WikiSpaceSettingsController
  service/     WikiBrowseService, WikiSearchService, WikiFolderService, WikiUploadService, WikiTrashService,
               WikiPermissionService, TextExtractor, PassageSplitter (package-private helpers allowed)
  service/impl/ one *Impl per service interface; WikiIndexJobHandler (TenantJobHandler);
               WikiSourcesImpl (chatbot), WikiSourceRetrievalImpl (triage), WikiDocumentAccessImpl (triage),
               WikiSpaceLookups (ManagedSpaceLookup + ViewableSpaceLookup)
  repository/  AccessRepository (native, read only, holds AccessSql), FolderRepository, WikiDocumentRepository,
               WikiChunkRepository, SpacePermissionRepository, WikiSearchRepository (native)
  entity/      Folder, WikiDocument, WikiChunk, SpacePermission
  dto/         records matching the spec's schemas
backend/src/main/java/vn/tsolve/common/session/lookup/ViewableSpaceLookup.java   (new)
backend/src/main/resources/db/changelog/changes/0NN-wiki-*.sql
frontend/src/features/wiki/  pages (WikiHomePage, WikiDocumentPage, UploadDocumentsPage, SpaceSettingsPage),
               components (FolderTree, FolderModal, DocumentsTable, CompanyWideView, GrantAccessModal, TrashTable),
               hooks, mocks/handlers.ts
```

`TextExtractor` and `PassageSplitter` are concrete classes (one implementation each; no interface).

## External Dependencies

- New Maven dependencies (named for the reviewer, `backend/AGENTS.md`): `org.apache.pdfbox:pdfbox` 3.0.x and `org.apache.poi:poi-ooxml` 5.3.x. Versions pinned in `backend/pom.xml`.
- Existing: pgvector (`vector(1024)`), Ollama bge-m3 via `EmbeddingClient`, JobRunr via `TenantJobService`, Garage via `FileStorage`.

## Edge Cases & Risks

| # | Dimension | Case | Handling | Test target | Severity |
|---|-----------|------|----------|-------------|----------|
| 1 | Access | Deactivated user with an individual permission | Rule requires `u.status = 'ACTIVE'` | AC-001.3 IT | Critical |
| 2 | Access | Ticket Resolver loses the role but keeps a membership | Rule requires the role | AC-001.2 IT | High |
| 3 | Access | Department deleted (GBR-17): its Company-wide documents | Excluded everywhere (Space and Department Active) | AC-001.4, AC-002.2 IT | High |
| 4 | Access | Document id guessed by a non-viewer | 404 MSG110, same as unknown | AC-002.13 | Critical |
| 5 | Access | Knowledge Manager role removed but `space_manager` row kept | Not a manager, only a viewer through other rules | AC-001.9, AC-003.10 | High |
| 6 | Concurrency | Two folders with one name at once | Unique index, `DataIntegrityViolation` mapped to MSG85 | AC-003.12 IT | High |
| 7 | Concurrency | Two uploads of one title at once | Partial unique index; mapped to MSG84; stored files removed | AC-004.8, AC-004.11 IT | High |
| 8 | Concurrency | Index job runs after the document went to the trash | Job checks status first; chunks still filtered by status | AC-004.16 | Medium |
| 9 | Concurrency | Two grants of the same Department at once | Partial unique index; the loser skips (`ON CONFLICT DO NOTHING`) | AC-006.3 IT | Medium |
| 10 | Input | File named `.pdf` holding something else | Magic-byte check MSG89 | AC-004.4 | High |
| 11 | Input | Encrypted or scanned PDF | `UNREADABLE`, MSG90 at publish | AC-004.2, AC-004.9 | Medium |
| 12 | Input | 11th file, 0 files, 10.1 MB file | MSG88, MSG01, 413 MSG28 | AC-004.3, AC-004.5 | Medium |
| 13 | Input | Metadata rows ≠ files | 400 (AC-004.10) | AC-004.10 | Medium |
| 14 | Input | Search with only spaces, 201 characters, SQL-like text | MSG01, MSG02; `websearch_to_tsquery` takes any text | AC-002.9 | Medium |
| 15 | Tree | Move a folder under its own child | MSG86 | AC-003.6 | High |
| 16 | Tree | Move a level-1 folder with a level-3 grandchild under another folder | MSG86 (sub-tree depth) | AC-003.5 | High |
| 17 | Tree | Delete a folder whose only document is in the trash | MSG87 counts it | AC-003.9 | Medium |
| 18 | Failure | AI down while searching | Keyword only | AC-002.8 | High |
| 19 | Failure | AI down while indexing | `FAILED`, document Published, keyword search works | AC-004.15 | High |
| 20 | Failure | Object storage down during publish | No row, already stored files deleted | AC-004.11 | High |
| 21 | Failure | Object storage delete fails after permanent delete | Row gone, WARN log | AC-005.12 | Low |
| 22 | State | Restore when the title is taken twice | " (restored 2)" | AC-005.6 | Medium |
| 23 | State | Restore with a 200-character title | Base shortened | AC-005.6 | Low |
| 24 | State | Trash action on a Published document, or delete on a trashed one | 404 MSG110 | AC-005.9 | Medium |
| 25 | Privacy | Citation of a document that was revoked | `unavailable` returns it (MSG110 in Chatbot) | AC-001.6 | High |
| 26 | Privacy | AI Solution Suggestion must never see Department documents | Retrieval filters `COMPANY_WIDE` | AC-001.7 | Critical |
| 27 | Performance | 10 × 10 MB upload sent twice | `max-request-size` 101 MB; memory bounded by the request | W21 | Low |
| 28 | Performance | 200 passages × bge-m3 on CPU | Background job; batches of 16 | AC-004.14 | Medium |
| 29 | Tenancy | Request bound to Workspace A | Only A's database (platform tenant layer) | Non-Functional 5, after Task 6 | Medium |

Risk: two blockers outside wiki, `FileStorage` (KAN-96) and `TenantContext` (platform Task 6). Tasks 1 and 2 do not need them; tasks that store files start once KAN-96 is merged and use a test fake of `FileStorage` until Task 6 binds a tenant.
