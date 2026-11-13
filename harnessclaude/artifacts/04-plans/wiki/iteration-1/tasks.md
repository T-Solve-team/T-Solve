---
module: wiki
date: 2026-11-13
total_tasks: 12
owner: Dev
---

# Tasks: wiki

Spec: `artifacts/03-modules/wiki/spec.md` · Design: `artifacts/04-plans/wiki/iteration-1/design.md` · Master: `artifacts/04-plans/wiki/design.md` · Decisions: `kan-91-decisions.md`

Branches (team rule, one per Jira issue, from an up-to-date `main`): Tasks 1 to 3 `feat/KAN-170-wiki-view-documents` (also KAN-171); Tasks 4 and 5 `feat/KAN-166-wiki-folders` (also KAN-172, KAN-174); Tasks 6 to 8 `feat/KAN-167-wiki-upload` (also KAN-168, KAN-169); Tasks 9 and 10 `feat/KAN-173-wiki-trash` (also KAN-175 to KAN-177); Tasks 11 and 12 `feat/KAN-163-wiki-space-permissions` (also KAN-164, KAN-165). Each task is one commit after the user agrees. Test names cite the AC id: backend `..._AC_001_4`, frontend `it('AC-002.14 ...')`. Contract first: every API task edits `frontend/openapi/tsolve-api.yaml` and runs `npm run generate:api` before the Java code (`backend/AGENTS.md` rule 8).

Blockers: Tasks 6 and 9 need `FileStorage` (KAN-96, pull request 193) on `main`. AC-001.7 needs triage's `WikiSourceRetrieval` (pull request 207); if it is not merged when Task 1 starts, AC-001.7 is a second commit on the same branch once it is.

## Task 1: Wiki tables, view rule and retrieval ports

**Story:** STORY-001
**Feature:** wiki-view-rule
**Description:** The changeset of the design (four tables, indexes, generated `search_vector`), the JPA entities, `AccessRepository` with the `AccessSql` fragments, and the port implementations: `WikiSourcesImpl` (AC-001.5, AC-001.6), `WikiSourceRetrievalImpl` (AC-001.7), `WikiDocumentAccessImpl` (AC-001.8), `WikiSpaceLookups` implementing `ManagedSpaceLookup` and the new `ViewableSpaceLookup` (AC-001.9, AC-001.10). Delete `NoWikiSources`, `NoWikiDocumentAccess` and `NoWikiSourcesTest`; `SuggestionResultServiceImplTest` mocks `WikiDocumentAccess` instead. ITs insert `space`, `space_manager`, users, roles, memberships, documents and chunks with SQL.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/0NN-wiki-tables.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `backend/src/main/java/vn/tsolve/wiki/entity/{Folder,WikiDocument,WikiChunk,SpacePermission}.java`
- Create: `backend/src/main/java/vn/tsolve/wiki/repository/AccessRepository.java`
- Create: `backend/src/main/java/vn/tsolve/wiki/repository/{FolderRepository,WikiDocumentRepository,WikiChunkRepository,SpacePermissionRepository}.java`
- Create: `backend/src/main/java/vn/tsolve/wiki/service/impl/{WikiSourcesImpl,WikiSourceRetrievalImpl,WikiDocumentAccessImpl,WikiSpaceLookups}.java`
- Create: `backend/src/main/java/vn/tsolve/common/session/lookup/ViewableSpaceLookup.java`
- Delete: `backend/src/main/java/vn/tsolve/chatbot/source/service/impl/NoWikiSources.java`, `backend/src/test/java/vn/tsolve/chatbot/source/service/impl/NoWikiSourcesTest.java`, `backend/src/main/java/vn/tsolve/triage/service/impl/NoWikiDocumentAccess.java`
- Modify: `backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionResultServiceImplTest.java`
- Test: `backend/src/test/java/vn/tsolve/wiki/WikiAccessIT.java` (Testcontainers pgvector)
**Tests:** AC-001.1 each grant path (manager, own Department, Department permission, own permission, Ticket Resolver project Department) and the negatives (Space Hidden, Department Deleted) · AC-001.2 Ticket Resolver without the role (row 2) · AC-001.3 (row 1) · AC-001.4 Company-wide of a non-viewable Space, Department document of a non-viewable Space, deleted Department (row 3) · AC-001.5 best passage per document, threshold, limit, order · AC-001.6 unknown, trashed, revoked (row 25) · AC-001.7 Department documents never returned (row 26) · AC-001.8 non-UUID id dropped · AC-001.9 role removed (row 5) · AC-001.10 · AC-001.11 `PENDING` and `FAILED` excluded from ports.

## Task 2: Browse, search and read API

**Story:** STORY-002
**Feature:** wiki-browse-api
**Description:** Contract for `listWikiSpaces`, `listCompanyWideDocuments`, `listWikiFolders`, `listWikiDocuments`, `searchWikiDocuments`, `getWikiDocument` and their schemas (tag `wiki`). `WikiBrowseService`, `WikiSearchService` and `WikiSearchRepository` (the query of the design, `tsolve.wiki.search.min-similarity` default 0.55 in `application.yml`). Controllers answer the errors of the spec's table.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**` (regenerated)
- Create: `backend/src/main/java/vn/tsolve/wiki/controller/{WikiBrowseController,WikiDocumentController}.java`
- Create: `backend/src/main/java/vn/tsolve/wiki/service/{WikiBrowseService,WikiSearchService}.java` and their `impl/`
- Create: `backend/src/main/java/vn/tsolve/wiki/repository/WikiSearchRepository.java`
- Create: `backend/src/main/java/vn/tsolve/wiki/dto/*` (records of the spec's schemas)
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/vn/tsolve/wiki/controller/WikiBrowseControllerTest.java`, `WikiDocumentControllerTest.java`
- Test: `backend/src/test/java/vn/tsolve/wiki/WikiBrowseIT.java`, `WikiSearchIT.java`
**Tests:** AC-002.1 to AC-002.11 and AC-002.13 · rows 4, 14, 18 (search with `EmbeddingClient` stubbed to throw).

## Task 3: Wiki Home and Wiki Document screens

**Story:** STORY-002
**Feature:** wiki-home-ui
**Description:** Replace the stubs `WikiHomePage` and `WikiDocumentPage` with 11.1 and 11.2: `CompanyWideView`, Spaces list, `FolderTree` (read-only here), `DocumentsTable`, search box, URL state `?space=&folder=`. Knowledge Manager buttons are shown but only "Space Settings" and "Upload documents" navigate; folder actions come in Task 5. MSW handlers in `features/wiki/mocks/handlers.ts` use the generated factories.
**Files:**
- Modify: `frontend/src/features/wiki/pages/{WikiHomePage,WikiDocumentPage}.tsx`, `frontend/src/features/wiki/mocks/handlers.ts`
- Create: `frontend/src/features/wiki/components/{CompanyWideView,FolderTree,DocumentsTable}.tsx`, `frontend/src/features/wiki/hooks/*`
- Test: `frontend/src/features/wiki/pages/WikiHomePage.test.tsx`, `WikiDocumentPage.test.tsx`
**Tests:** AC-002.14 to AC-002.21.

## Task 4: Folder API

**Story:** STORY-003
**Feature:** wiki-folder-api
**Description:** Contract for `createWikiFolder`, `updateWikiFolder`, `deleteWikiFolder`; `FOLDER_CHANGE` in `AuditAction` (contract, enum, changeset like `027-…`), audit label "Folder change". `WikiFolderService`: strip, depth of the moved sub-tree (recursive CTE), cycle check, unique index violation → MSG85, MSG87 counts.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`, `frontend/src/features/audit-log/hooks/audit-filters.ts`
- Create: `backend/src/main/resources/db/changelog/changes/0NN-wiki-audit-folder-change.sql`; Modify: `db.changelog-master.xml`
- Modify: `backend/src/main/java/vn/tsolve/common/audit/AuditAction.java`
- Create: `backend/src/main/java/vn/tsolve/wiki/controller/WikiFolderController.java`, `service/WikiFolderService.java`, `service/impl/WikiFolderServiceImpl.java`, dto records
- Test: `WikiFolderControllerTest.java`, `WikiFolderServiceImplTest.java`, `WikiFolderIT.java`, `frontend/src/features/audit-log/hooks/audit-filters.test.ts`
**Tests:** AC-003.1 to AC-003.12, AC-003.17 · rows 6, 15, 16, 17.

## Task 5: Folder popup and actions

**Story:** STORY-003
**Feature:** wiki-folder-ui
**Description:** `FolderModal` (create, rename, move) with a parent tree selector that hides level 3, the folder and its sub-tree; Delete with confirmation; refresh of the folder query.
**Files:**
- Create: `frontend/src/features/wiki/components/FolderModal.tsx`
- Modify: `frontend/src/features/wiki/components/FolderTree.tsx`, `pages/WikiHomePage.tsx`, `mocks/handlers.ts`
- Test: `frontend/src/features/wiki/components/FolderModal.test.tsx`, `WikiHomePage.test.tsx`
**Tests:** AC-003.13 to AC-003.16.

## Task 6: Upload API and download

**Story:** STORY-004, STORY-002
**Feature:** wiki-upload-api
**Description:** Add `pdfbox` 3.0.x and `poi-ooxml` 5.3.x to `pom.xml`; `max-request-size` 101 MB. Contract for `extractWikiUploads`, `publishWikiDocuments`, `downloadWikiDocumentFile`. `TextExtractor` (magic bytes, PDFBox, `XWPFWordExtractor`), `WikiUploadService` (checks, store files, one transaction, delete stored keys on failure, `afterCommit` enqueue of `wiki-index`). Download streams `FileStorage.read` with try-with-resources. Tests use a small in-memory `FileStorage` in the test tree and fixture files (a PDF, a DOCX, an encrypted PDF, an image-only PDF, a `.pdf` holding text).
**Files:**
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.yml`
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
- Create: `backend/src/main/java/vn/tsolve/wiki/controller/WikiUploadController.java`, `service/{WikiUploadService,TextExtractor}.java`, `service/impl/WikiUploadServiceImpl.java`, dto records
- Modify: `backend/src/main/java/vn/tsolve/wiki/controller/WikiDocumentController.java`
- Create: `backend/src/test/resources/wiki/*` (fixture files), `backend/src/test/java/vn/tsolve/wiki/InMemoryFileStorage.java`
- Test: `TextExtractorTest.java`, `WikiUploadControllerTest.java`, `WikiUploadIT.java`
**Tests:** AC-004.1 to AC-004.12, AC-004.17, AC-002.12 · rows 7, 10 to 13, 20, 27.

## Task 7: Indexing job

**Story:** STORY-004
**Feature:** wiki-indexing
**Description:** `PassageSplitter` (≤ 1,000 characters, paragraph or whitespace cut, ≤ 200, `ponytail:` comment) and `WikiIndexJobHandler` (`handlerType` `wiki-index`), batches of 16, `INDEXED` or `FAILED`.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/wiki/service/PassageSplitter.java`, `service/impl/WikiIndexJobHandler.java`
- Test: `PassageSplitterTest.java`, `WikiIndexJobHandlerTest.java`, `WikiIndexIT.java`
**Tests:** AC-004.13 to AC-004.16 · rows 8, 19, 28.

## Task 8: Upload Documents screen

**Story:** STORY-004
**Feature:** wiki-upload-ui
**Description:** 11.3: folder selector, visibility default, AntD `Upload` with `beforeUpload` checks (count, type, size), extract call, editable rows, Publish rules, MSG95, Cancel.
**Files:**
- Modify: `frontend/src/features/wiki/pages/UploadDocumentsPage.tsx`, `mocks/handlers.ts`
- Test: `frontend/src/features/wiki/pages/UploadDocumentsPage.test.tsx`
**Tests:** AC-004.18 to AC-004.23.

## Task 9: Trash API

**Story:** STORY-005
**Feature:** wiki-trash-api
**Description:** Contract for `trashWikiDocument`, `listWikiTrash`, `restoreWikiDocument`, `deleteWikiDocumentPermanently`. `WikiTrashService`: status changes, restore title with " (restored n)", enqueue when not `INDEXED`, permanent delete with `afterCommit` file delete; `getWikiDocument` allows the Knowledge Manager to read a trashed document (AC-005.4).
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
- Create: `backend/src/main/java/vn/tsolve/wiki/controller/WikiTrashController.java`, `service/WikiTrashService.java`, `service/impl/WikiTrashServiceImpl.java`
- Modify: `backend/src/main/java/vn/tsolve/wiki/service/impl/WikiBrowseServiceImpl.java`
- Test: `WikiTrashControllerTest.java`, `WikiTrashServiceImplTest.java`, `WikiTrashIT.java`
**Tests:** AC-005.1 to AC-005.12 · rows 21 to 24.

## Task 10: Delete button and Trash tab

**Story:** STORY-005
**Feature:** wiki-trash-ui
**Description:** Delete on 11.2 with MSG100 and MSG101; the Trash tab of `SpaceSettingsPage` (tab shell built here, Permissions tab content in Task 12) with Restore and "Delete permanently".
**Files:**
- Modify: `frontend/src/features/wiki/pages/{WikiDocumentPage,SpaceSettingsPage}.tsx`, `mocks/handlers.ts`
- Create: `frontend/src/features/wiki/components/TrashTable.tsx`
- Test: `WikiDocumentPage.test.tsx`, `frontend/src/features/wiki/components/TrashTable.test.tsx`
**Tests:** AC-005.13 to AC-005.16.

## Task 11: Space permissions API

**Story:** STORY-006
**Feature:** wiki-permissions-api
**Description:** Contract for `getWikiSpaceSettings`, `listWikiPermissionCandidates`, `grantWikiPermissions`, `revokeWikiPermission`. `WikiPermissionService`: validate all ids first, insert with `ON CONFLICT DO NOTHING`, one audit entry per stored permission.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
- Create: `backend/src/main/java/vn/tsolve/wiki/controller/WikiSpaceSettingsController.java`, `service/WikiPermissionService.java`, `service/impl/WikiPermissionServiceImpl.java`
- Test: `WikiSpaceSettingsControllerTest.java`, `WikiPermissionIT.java`
**Tests:** AC-006.1 to AC-006.10 · row 9.

## Task 12: Permissions tab

**Story:** STORY-006
**Feature:** wiki-permissions-ui
**Description:** Space header, Departments and Users tables, locked own Department with the MSG107 tooltip, `GrantAccessModal`, Remove with MSG106, no-permission page for non-managers.
**Files:**
- Modify: `frontend/src/features/wiki/pages/SpaceSettingsPage.tsx`, `mocks/handlers.ts`
- Create: `frontend/src/features/wiki/components/GrantAccessModal.tsx`
- Test: `frontend/src/features/wiki/pages/SpaceSettingsPage.test.tsx`
**Tests:** AC-006.11 to AC-006.14.
