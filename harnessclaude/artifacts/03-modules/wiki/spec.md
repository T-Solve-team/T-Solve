---
module: wiki
date: 2026-11-13
owner: BA
---

# Spec: wiki

Covers UC-38 to UC-50, Wiki Space and Wiki Document Management (Jira epic KAN-91, stories KAN-163 to KAN-177). Source: SRS v3460, folder `Sua_31-10` (UC-38 to UC-50; screens 11.1 to 11.5; GBR-12, GBR-17, GBR-29 to GBR-32, GBR-34 to GBR-36; MSG01, MSG02, MSG04, MSG05, MSG06, MSG07, MSG08, MSG28, MSG84 to MSG90, MSG95, MSG100 to MSG103, MSG106, MSG107, MSG110), `SRS_Delta.md` (CR-09: PDF and DOCX only). Decisions: `artifacts/04-plans/wiki/iteration-1/kan-91-decisions.md` (W1 to W30).

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-13 | STORY-001 to STORY-006 | Initial spec: UC-38 to UC-50 | KAN-91 |
| 2026-11-13 | STORY-002 to STORY-004 | Builder check: AC-002.13 leaves the trash read to AC-005.4; `q` limits as received (AC-002.7, AC-002.9); AC-003.7 covers an unknown `folderId`; AC-004.10 names its code; AC-004.12 in the same transaction | Inline builder check |
| 2026-11-14 | STORY-005 | AC-005.2 excepts the Knowledge Manager's read of AC-005.4; new AC-005.17: a restore that loses its title to a concurrent request answers 409 MSG84; pull request numbers replaced by Jira keys | Review of #223 (findings 1, 4, 8) |

## Requirements

### Functional
1. Every Department has exactly one Space (workspace, GBR-29). Wiki adds folders (at most three levels), Wiki documents, their trash and the View permissions of the Space.
2. A user can view a Space when they are its Knowledge Manager, when one of their Departments has View on it, or when they have their own View permission on it (GBR-30). The Space's own Department always has View; it is shown, not stored, and cannot be removed.
3. A user can view a Wiki document when it is Published and either Company-wide or in a Space the user can view (GBR-31). The Spaces and documents of a Hidden Space or a Deleted Department are not shown to anyone (GBR-17).
4. Only the Knowledge Manager of a Space manages its folders, documents, trash and permissions (GBR-32).
5. Uploaded PDF and DOCX files are stored as the original file plus their extracted text. A document is Published at once and indexed in the background for meaning-based retrieval (GBR-12).
6. Wiki provides the Wiki sources of the Knowledge Chatbot (all documents the user can view) and of AI Solution Suggestion (Company-wide documents only, GBR-35), and answers whether a cited document is still available (MSG110).
7. Every change to a folder, document or permission is recorded in the audit log.

### Non-Functional
1. The view rule (requirement 2 and 3) is evaluated on every request from the database; no cached permission decides access.
2. A document deleted to the trash disappears from lists, search, the Chatbot and AI Solution Suggestion in the same request that deletes it.
3. Uniqueness rules (folder names, document titles) hold in the database: two requests sent at the same moment cannot break them.
4. Indexing runs outside the request, on the worker. An AI failure does not fail an upload; the document stays found by keyword.
5. A Workspace's Wiki lives only in that Workspace's database (GBR-37, ADR-0002).

### Interim limits
These are known limits of the code this module builds on. They are not defects of this module.

1. Original files are stored through `FileStorage` (platform, KAN-96). Until it is merged, wiki's tasks that store files wait for it.
2. The shipped app has one datasource until the tenant layer (KAN-98, KAN-99, platform Task 6) lands. Wiki's ITs use the test database and an in-memory `FileStorage` in wiki's test tree.
3. A real Keycloak sign-in cannot reach these screens until KAN-275 lands; the web app is verified with MSW and the backend with `identity-mock` and MockMvc tests.
4. The web app does not send the CSRF header yet (workspace interim limit 3); browser writes are verified with MSW.
5. A Knowledge Manager is assigned to a Space by identity (UC-05, UC-07, UC-09). Until that is built, tests write `space_manager` rows directly.

## User Stories

### STORY-001: Who can view what (view rule and retrieval ports)
- As the system, I want one view rule for Spaces and Wiki documents so that the Wiki, the Knowledge Chatbot and AI Solution Suggestion show each user only what they may read.
- Acceptance:
  - AC-001.1: WHEN the view rule is evaluated for a user and a Space, THE system SHALL grant View only if the user is Active, the Space is Active, its Department is Active, and one of these holds: the user is the Space's Knowledge Manager (a `space_manager` row and the role `KNOWLEDGE_MANAGER`); the user's own Department is the Space's Department; one of the user's Departments has a permission on the Space; the user has their own permission on the Space.
  - AC-001.2: WHEN the user's Departments are computed, THE system SHALL use the user's own Department, plus, when the user has the role `TICKET_RESOLVER`, the Departments of the Projects in which the user is a `MEMBER`, counting only Active Departments.
  - AC-001.3: IF a user is Deactivated, THEN THE system SHALL apply none of that user's permissions and grant them no View.
  - AC-001.4: WHEN the view rule is evaluated for a user and a Wiki document, THE system SHALL grant it only if the document is Published, its Space and Department are Active, and the document is Company-wide or the user can view its Space.
  - AC-001.5: WHEN the Knowledge Chatbot calls `WikiSources.nearest(user, vector, minSimilarity, limit)`, THE system SHALL return, for each document the user can view whose best passage has a similarity of at least `minSimilarity`, that best passage with the document's id and title, most similar first, at most `limit` items.
  - AC-001.6: WHEN the Knowledge Chatbot calls `WikiSources.unavailable(user, ids)`, THE system SHALL return exactly the given ids that the user cannot view, including unknown, deleted and permanently deleted documents.
  - AC-001.7: WHEN AI Solution Suggestion calls `WikiSourceRetrieval.nearest(vector, minSimilarity, limit)`, THE system SHALL return only Published Company-wide documents of Active Spaces of Active Departments, one best passage per document, most similar first, at most `limit` items.
  - AC-001.8: WHEN triage calls `WikiDocumentAccess.available(userId, ids)`, THE system SHALL return exactly the given ids that the user can view; an id that is not a UUID SHALL be left out.
  - AC-001.9: WHEN identity calls `ManagedSpaceLookup.findManagedSpaces(userId)`, THE system SHALL return the Active Spaces of Active Departments that the user manages, only when the user has the role `KNOWLEDGE_MANAGER`, sorted by name.
  - AC-001.10: WHEN identity calls `ViewableSpaceLookup.findViewableSpaces(userId)`, THE system SHALL return every Space the user can view by AC-001.1, sorted by name.
  - AC-001.11: WHILE a document is not yet indexed, or its indexing failed, THE system SHALL leave it out of AC-001.5 and AC-001.7 and keep it in every list and keyword search.

### STORY-002: Browse, search and read Wiki documents
- As an Employee, I want to browse the Spaces I can view, the Company-wide documents of every Department and a search box so that I find and read the right document.
- Acceptance:
  - AC-002.1: WHEN a signed-in user calls `GET /api/v1/wiki/spaces`, THE system SHALL answer 200 with the Spaces the user can view, sorted by name, each with `spaceId`, `name`, `departmentName` and `accessLevel` (`KNOWLEDGE_MANAGER` for the Space's Knowledge Manager, otherwise `VIEW`).
  - AC-002.2: WHEN a signed-in user calls `GET /api/v1/wiki/company-wide`, THE system SHALL answer 200 with every Published Company-wide document of every Active Space of an Active Department, sorted by Space name and then title.
  - AC-002.3: WHEN a user who can view a Space calls `GET /api/v1/wiki/spaces/{spaceId}/folders`, THE system SHALL answer 200 with all folders of the Space as a flat list of `folderId`, `parentId` (null at the top level), `name` and `level` (1 to 3), sorted by name.
  - AC-002.4: WHEN a user who can view a Space calls `GET /api/v1/wiki/spaces/{spaceId}/documents` with an optional `folderId`, THE system SHALL answer 200 with the Published documents directly in that folder, or at the top level when `folderId` is absent, sorted by title.
  - AC-002.5: IF a user who cannot view an Active Space calls an endpoint of AC-002.3 or AC-002.4 for it, THEN THE system SHALL answer 403 with code MSG08.
  - AC-002.6: IF a request names a Space that does not exist, is Hidden or belongs to a Deleted Department, or a `folderId` that is not a folder of that Space, THEN THE system SHALL answer 404 with code MSG04.
  - AC-002.7: WHEN a signed-in user calls `GET /api/v1/wiki/documents/search?q=` that is not blank and has at most 200 characters, THE system SHALL answer 200 with at most 50 documents the user can view that match the words of `q` in the title or text, or whose best passage has a similarity of at least the configured `tsolve.wiki.search.min-similarity`, ordered by keyword rank plus similarity, highest first.
  - AC-002.8: IF the AI module cannot embed the query, THEN THE system SHALL answer AC-002.7 from the keyword match alone.
  - AC-002.9: IF `q` is missing or blank, THEN THE system SHALL answer 400 with code MSG01 on field `q`; IF `q` is not blank and longer than 200 characters as received, THEN THE system SHALL answer 400 with code MSG02 and `max_length` 200 on field `q`.
  - AC-002.10: WHEN a list of AC-002.2, AC-002.4 or AC-002.7 returns a document, THE item SHALL hold `documentId`, `title`, `spaceId`, `spaceName`, `folderId`, `folderName` (null at the top level), `visibility` (`DEPARTMENT` or `COMPANY_WIDE`), `owner` (full name) and `updatedAt`.
  - AC-002.11: WHEN a user who can view a document calls `GET /api/v1/wiki/documents/{documentId}`, THE system SHALL answer 200 with the fields of AC-002.10 plus `content` (the extracted text), `fileName`, `fileSize` and `canDelete` (true only for the Space's Knowledge Manager).
  - AC-002.12: WHEN a user who can view a document calls `GET /api/v1/wiki/documents/{documentId}/file`, THE system SHALL answer 200 with the original file, its content type and `Content-Disposition: attachment` with the original file name.
  - AC-002.13: IF a document does not exist, was deleted permanently, or the user cannot view it, or it is in the trash and the caller is not the Space's Knowledge Manager calling AC-002.11 (AC-005.4), THEN THE endpoints of AC-002.11 and AC-002.12 SHALL answer 404 with code MSG110.
  - AC-002.14: WHEN the user opens 11.1 Wiki Home, THE web app SHALL show the Company-wide view first, grouped by Space name, then the list of viewable Spaces with their access level, a search box, and, for the selected Space, its folder tree and the documents table with the columns Title, Folder, Visibility, Owner and Updated.
  - AC-002.15: WHERE the user can view no Space, THE web app SHALL show only the Company-wide view and the search box.
  - AC-002.16: WHERE a documents table, the Company-wide view or the search result is empty, THE web app SHALL show MSG04 in its place.
  - AC-002.17: WHEN the user selects a Space or a folder, THE web app SHALL keep them in the URL as `?space=` and `?folder=` and show the documents of that folder.
  - AC-002.18: IF the search box holds more than 200 characters, THEN THE web app SHALL show MSG02 below it and send no request.
  - AC-002.19: WHEN the user opens a document, THE web app SHALL show 11.2 Wiki Document with its header (title, Space, folder, visibility, Owner, last update), its content as plain text and a Download button.
  - AC-002.20: IF the document answers 404 MSG110, THEN THE web app SHALL show "This document is no longer available." in place of the document.
  - AC-002.21: WHERE the user is the Knowledge Manager of the selected Space, THE web app SHALL show the buttons "Upload documents", "New folder" and "Space Settings" on 11.1, and folder actions Rename, Move and Delete; for any other user it SHALL show none of them.

### STORY-003: Manage the folders of a Space
- As a Knowledge Manager, I want to create, rename, move and delete folders of my Space so that documents are grouped clearly.
- Acceptance:
  - AC-003.1: WHEN the Space's Knowledge Manager calls `POST /api/v1/wiki/spaces/{spaceId}/folders` with an allowed `name` and an optional `parentId`, THE system SHALL create the folder under that parent, or at the top level, with the name stripped of leading and trailing whitespace, and answer 201 with the folder of AC-002.3.
  - AC-003.2: WHEN the Space's Knowledge Manager calls `PUT /api/v1/wiki/folders/{folderId}` with an allowed `name` and a `parentId` (null for the top level), THE system SHALL save the new name and parent, keep its sub-folders and documents inside it, and answer 200 with the folder.
  - AC-003.3: IF the name is missing, empty or only whitespace, THEN THE system SHALL answer 400 with code MSG01 on field `name`; IF it is longer than 100 characters, THEN THE system SHALL answer 400 with code MSG02 and `max_length` 100 on field `name`.
  - AC-003.4: IF the stripped name equals, ignoring letter case, the name of another folder with the same parent, THEN THE system SHALL answer 409 with code MSG85 on field `name`.
  - AC-003.5: IF the new folder, or the deepest folder of a moved sub-tree, would be deeper than level 3, THEN THE system SHALL answer 400 with code MSG86 on field `parentId`.
  - AC-003.6: IF the new parent is the folder itself or one of its sub-folders, THEN THE system SHALL answer 400 with code MSG86 on field `parentId`.
  - AC-003.7: IF the `folderId` is not a folder, or the `parentId` is not a folder of the same Space, THEN THE system SHALL answer 404 with code MSG04.
  - AC-003.8: WHEN the Space's Knowledge Manager calls `DELETE /api/v1/wiki/folders/{folderId}` for a folder with no sub-folder and no document, Published or in the trash, THE system SHALL delete it and answer 204.
  - AC-003.9: IF the folder holds sub-folders or documents, THEN THE system SHALL answer 409 with code MSG87 and `folder_count` and `document_count`, counting its direct sub-folders and its Published and trashed documents, and delete nothing.
  - AC-003.10: IF anyone other than the Space's Knowledge Manager calls an endpoint of this story, THEN THE system SHALL answer 403 with code MSG08 and change nothing.
  - AC-003.11: WHEN a folder is created, updated or deleted, THE system SHALL write one audit entry in the same transaction with action `FOLDER_CHANGE`, target type `WIKI_FOLDER`, target id the folder id, and details `operation` (`CREATE`, `UPDATE` or `DELETE`), `name`, `spaceId` and `parentId`.
  - AC-003.12: IF two requests at the same moment would give two folders of one parent the same name, THEN THE system SHALL save one and answer the other with 409 MSG85.
  - AC-003.13: WHEN the Knowledge Manager clicks "New folder", Rename or Move, THE web app SHALL open the Folder popup with the field Name (at most 100 characters) and a Parent folder tree selector that offers no folder at level 3, not the folder itself and none of its sub-folders.
  - AC-003.14: IF the Name is blank or longer than 100 characters when Save is clicked, THEN THE web app SHALL show MSG01 or MSG02 below it and send no request.
  - AC-003.15: WHEN the server answers 201 or 200, THE web app SHALL close the popup, show MSG05 and refresh the folder tree; IF it answers MSG85 or MSG86, THEN THE web app SHALL show that message in the popup and keep it open.
  - AC-003.16: WHEN the Knowledge Manager clicks Delete on a folder, THE web app SHALL ask for confirmation, and after the answer show MSG05 and refresh the tree, or show MSG87 with the counts.
  - AC-003.17: WHERE an audit entry has action `FOLDER_CHANGE`, THE Audit Log screen SHALL label that action "Folder change".

### STORY-004: Upload documents
- As a Knowledge Manager, I want to upload up to ten PDF or DOCX files at once into a folder of my Space so that company guides become Wiki documents and Chatbot sources.
- Acceptance:
  - AC-004.1: WHEN the Space's Knowledge Manager calls `POST /api/v1/wiki/spaces/{spaceId}/uploads/extract` with 1 to 10 PDF or DOCX files, THE system SHALL answer 200 with one row per file in the order sent: `fileName`, `title` (the file name without its extension, stripped, cut to 200 characters), `excerpt` (the first 300 characters of the text), `wordCount` and `result` (`READY` or `UNREADABLE`), and store nothing.
  - AC-004.2: IF a file has no readable text (an error while reading it, an encrypted file, or text that is only whitespace), THEN THE system SHALL give its row `result` `UNREADABLE`, an empty `excerpt` and `wordCount` 0.
  - AC-004.3: IF a request holds more than 10 files, THEN THE system SHALL answer 400 with code MSG88; IF it holds no file, THEN THE system SHALL answer 400 with code MSG01 on field `files`.
  - AC-004.4: IF a file's name does not end in `.pdf` or `.docx` (ignoring letter case), or its content does not start like a PDF or a DOCX (ZIP) file, THEN THE system SHALL answer 400 with code MSG89 and `file_name`, and store nothing.
  - AC-004.5: IF a file is larger than 10 MB, THEN THE system SHALL answer 413 with code MSG28 and store nothing.
  - AC-004.6: WHEN the Space's Knowledge Manager calls `POST /api/v1/wiki/spaces/{spaceId}/documents` with the files and a `metadata` part holding an optional `folderId` and, for each file in order, a `title` and a `visibility`, THE system SHALL create one Published document per file with the stripped title, the visibility, the folder, the caller as Owner, the original file, its extracted text and `indexStatus` `PENDING`, and answer 201 with their `documentId` and `title`.
  - AC-004.7: IF a title is blank, THEN THE system SHALL answer 400 with code MSG01 on field `documents[i].title`; IF it is longer than 200 characters, THEN THE system SHALL answer 400 with code MSG02 and `max_length` 200 on that field.
  - AC-004.8: IF a title equals, ignoring letter case, the title of a Published document of the target folder or of another file of the same request, THEN THE system SHALL answer 409 with code MSG84 on field `documents[i].title`.
  - AC-004.9: IF a file of the publish request has no readable text, THEN THE system SHALL answer 400 with code MSG90 and `file_name`.
  - AC-004.10: IF the `metadata` part is missing or its number of rows differs from the number of files, THEN THE system SHALL answer 400 with code MSG01 on field `metadata`; the publish request SHALL also apply AC-004.3 to AC-004.5.
  - AC-004.11: IF any check of the publish request fails, or storing any file or row fails, THEN THE system SHALL create no document and leave no stored file from that request.
  - AC-004.12: WHEN documents are published, THE system SHALL write, in the same transaction, one audit entry per document with action `PUBLISH`, target type `WIKI_DOCUMENT`, target id the document id, and details `operation` `UPLOAD`, `title`, `spaceId`, `folderId` and `visibility`.
  - AC-004.13: WHEN a document is published or restored without an index, THE system SHALL enqueue one indexing job for it after the transaction commits.
  - AC-004.14: WHEN the indexing job runs for a Published document, THE system SHALL split its text into passages of at most 1,000 characters, cut at a paragraph break or whitespace where possible, keep at most the first 200 passages, store each passage with its bge-m3 vector, and set `indexStatus` `INDEXED`.
  - AC-004.15: IF embedding fails, THEN THE indexing job SHALL keep no passage of that run, set `indexStatus` `FAILED`, and leave the document Published.
  - AC-004.16: IF the document is no longer Published when the job runs, THEN THE job SHALL change nothing.
  - AC-004.17: IF anyone other than the Space's Knowledge Manager calls an endpoint of this story, THEN THE system SHALL answer 403 with code MSG08 and store nothing.
  - AC-004.18: WHEN the Knowledge Manager clicks "Upload documents", THE web app SHALL open 11.3 Upload Documents with a Folder tree selector (the selected folder by default), a Visibility choice (Department by default) and a file area.
  - AC-004.19: IF more than 10 files are added in total, THEN THE web app SHALL show MSG88 and add none of the extra files; IF a file is not PDF or DOCX or is larger than 10 MB, THEN THE web app SHALL show MSG89 with its name and not add it.
  - AC-004.20: WHEN files are added, THE web app SHALL send them to the extract endpoint and show one row per file: File name, Visibility (editable), Title (editable), Extracted text, Words, and Result (Ready or MSG90), with a Remove button.
  - AC-004.21: WHILE a row is `UNREADABLE`, has a blank title or a title longer than 200 characters, or no row is left, THE web app SHALL disable Publish.
  - AC-004.22: WHEN Publish succeeds, THE web app SHALL show MSG95 with the count and return to 11.1 on the target folder; IF it answers MSG84, MSG01 or MSG02 on a row, THEN THE web app SHALL show the message on that row and keep every row.
  - AC-004.23: WHEN the user clicks Cancel, THE web app SHALL return to 11.1 and send no publish request.

### STORY-005: Delete, trash, restore and permanently delete
- As a Knowledge Manager, I want to move wrong documents to the trash and then restore them or remove them for good so that mistakes can be undone and outdated content goes away.
- Acceptance:
  - AC-005.1: WHEN the Space's Knowledge Manager calls `DELETE /api/v1/wiki/documents/{documentId}` for a Published document, THE system SHALL set its status `DELETED` with `deletedBy` the caller and `deletedAt` now, and answer 204.
  - AC-005.2: WHEN a document is in the trash, THE system SHALL leave it out of every endpoint of STORY-002, except the Knowledge Manager's read of AC-005.4, and every port of STORY-001 from that request on.
  - AC-005.3: WHEN the Space's Knowledge Manager calls `GET /api/v1/wiki/spaces/{spaceId}/trash`, THE system SHALL answer 200 with the documents in its trash, newest `deletedAt` first, each with `documentId`, `title`, `folderName`, `owner`, `deletedBy` (full name) and `deletedAt`.
  - AC-005.4: WHEN the Space's Knowledge Manager calls `GET /api/v1/wiki/documents/{documentId}` for a document in the trash of their Space, THE system SHALL answer 200 with the document read-only and `canDelete` false.
  - AC-005.5: WHEN the Space's Knowledge Manager calls `POST /api/v1/wiki/documents/{documentId}/restore` for a document in the trash, THE system SHALL set it Published in its folder, clear `deletedBy` and `deletedAt`, and answer 200 with `title` and `renamed`.
  - AC-005.6: IF the restored title is already used, ignoring letter case, by a Published document of the folder, THEN THE system SHALL append " (restored)", or " (restored 2)", " (restored 3)" and so on until the title is free, shorten the original title so that the result has at most 200 characters, and answer `renamed` true.
  - AC-005.7: WHEN a restored document has `indexStatus` other than `INDEXED`, THE system SHALL enqueue its indexing job (AC-004.13); an indexed document SHALL be found again by meaning at once.
  - AC-005.8: WHEN the Space's Knowledge Manager calls `DELETE /api/v1/wiki/documents/{documentId}/permanent` for a document in the trash, THE system SHALL delete the document and its passages, delete its original file from storage after the commit, and answer 204.
  - AC-005.9: IF the document of AC-005.1 is not Published, or the document of AC-005.5 or AC-005.8 is not in the trash, THEN THE system SHALL answer 404 with code MSG110 and change nothing.
  - AC-005.10: IF anyone other than the Space's Knowledge Manager calls an endpoint of this story, THEN THE system SHALL answer 403 with code MSG08 for a document they can view, otherwise 404 with code MSG110, and change nothing.
  - AC-005.11: WHEN a document is moved to the trash, restored or permanently deleted, THE system SHALL write one audit entry in the same transaction with target type `WIKI_DOCUMENT`, target id the document id, action `DELETE` with `operation` `TRASH` or `PERMANENT`, or action `PUBLISH` with `operation` `RESTORE`, and details `title` and `spaceId`.
  - AC-005.12: IF deleting the original file from storage fails after a permanent delete, THEN THE system SHALL keep the delete, log the file key at WARN and answer 204.
  - AC-005.17: IF the free title chosen by AC-005.6 is taken by another request (an upload or a restore) before the restore commits, THEN THE system SHALL answer 409 with code MSG84 and leave the document in the trash.
  - AC-005.13: WHEN the Knowledge Manager clicks Delete on 11.2, THE web app SHALL ask MSG100 with the title; on confirm it SHALL show MSG101 and return to 11.1.
  - AC-005.14: WHEN the Knowledge Manager opens the Trash tab of 11.5, THE web app SHALL show the trash table (Title, Folder, Owner, Deleted by, Deleted on) with Restore and "Delete permanently" on each row, or MSG04 when it is empty.
  - AC-005.15: WHEN Restore succeeds, THE web app SHALL show MSG102, and also MSG84 as information when `renamed` is true, and refresh the trash.
  - AC-005.16: WHEN the Knowledge Manager clicks "Delete permanently", THE web app SHALL ask MSG103 with the title; on confirm it SHALL show MSG05 and refresh the trash.

### STORY-006: Space permissions
- As a Knowledge Manager, I want to see, grant and revoke View on my Space for Departments and users so that only the right people read its Department documents.
- Acceptance:
  - AC-006.1: WHEN the Space's Knowledge Manager calls `GET /api/v1/wiki/spaces/{spaceId}/settings`, THE system SHALL answer 200 with the Space `name`, `departmentName`, the Knowledge Manager's full name, the own Department (always first, `locked` true), the Department permissions (`permissionId`, `departmentId`, `name`, `grantedBy`, `grantedAt`) sorted by name, and the user permissions (`permissionId`, `userId`, `fullName`, `email`, `homeDepartment`, `active`, `grantedBy`, `grantedAt`) sorted by full name.
  - AC-006.2: WHEN the Space's Knowledge Manager calls `GET /api/v1/wiki/spaces/{spaceId}/permission-candidates`, THE system SHALL answer 200 with the Active Departments other than the own Department and the Active users that have no permission on the Space yet, each sorted by name.
  - AC-006.3: WHEN the Space's Knowledge Manager calls `POST /api/v1/wiki/spaces/{spaceId}/permissions` with `departmentIds` and `userIds`, THE system SHALL store one permission per id with the caller as `grantedBy` and now as `grantedAt`, skip ids that already have a permission, and answer 201.
  - AC-006.4: IF both lists are empty, THEN THE system SHALL answer 400 with code MSG01 on field `grantees`.
  - AC-006.5: IF an id is unknown, not Active, or is the own Department, THEN THE system SHALL answer 404 with code MSG04 and store no permission from that request.
  - AC-006.6: WHEN the Space's Knowledge Manager calls `DELETE /api/v1/wiki/permissions/{permissionId}` for a permission of their Space, THE system SHALL delete it and answer 204; a user who still has View through a Department keeps it.
  - AC-006.7: IF the permission does not exist or belongs to another Space, THEN THE system SHALL answer 404 with code MSG04.
  - AC-006.8: IF anyone other than the Space's Knowledge Manager calls an endpoint of this story, THEN THE system SHALL answer 403 with code MSG08 and change nothing.
  - AC-006.9: WHEN a permission is granted or revoked, THE system SHALL write one audit entry per permission in the same transaction with action `PERMISSION_CHANGE`, target type `WIKI_SPACE`, target id the Space id, and details `operation` (`GRANT` or `REVOKE`), `granteeType` (`DEPARTMENT` or `USER`) and `granteeId`.
  - AC-006.10: WHEN a permission is granted or revoked, THE view rule of AC-001.1 SHALL reflect it from the next request on.
  - AC-006.11: WHEN the Knowledge Manager opens 11.5 Space Settings, THE web app SHALL show the Space header and the Permissions tab with a Departments table (Department, Granted by, Date, Remove) whose first row is the own Department without Remove and with the MSG107 tooltip, and a Users table (Full name, Email, Home Department, Granted by, Date, Remove) with deactivated users greyed out, or MSG04 when it has no row.
  - AC-006.12: WHEN the Knowledge Manager clicks "Grant access", THE web app SHALL show a selector of the candidates of AC-006.2, or MSG04 when there is none, and after Save show MSG05 and refresh the tables.
  - AC-006.13: WHEN the Knowledge Manager clicks Remove, THE web app SHALL ask MSG106 with the grantee and Space name; on confirm it SHALL show MSG05 and refresh the tables.
  - AC-006.14: IF a user who is not the Knowledge Manager of the Space opens 11.5, THEN THE web app SHALL show the no-permission page with MSG08.

## Out of Scope
- Editing a document's text, title, visibility or folder after upload; there is no use case for it.
- Moving a document between folders; UC-42 moves whole folders.
- Automatic purge of the trash (GBR-34: none in the MVP).
- Assigning or handing over the Knowledge Manager of a Space (identity UC-05, UC-07, UC-09) and creating, renaming, hiding or restoring Spaces with their Department (workspace).
- File types other than PDF and DOCX (CR-09); text from images (OCR).
- The `GET /api/v1/me/spaces` endpoint itself (identity AC-003.4); wiki provides only its lookup (AC-001.10).
- Signed download URLs (platform Task 17); downloads stream through the backend (W19).

## Interface Contracts

### Exposed by this module

REST, tag `wiki`, added to `frontend/openapi/tsolve-api.yaml` before the code. All ids are UUIDs. Errors are `application/problem+json` with `code`, `params` and, for a field error, `fieldErrors`.

| Method and path | operationId | Who | Answer |
|-----------------|-------------|-----|--------|
| `GET /api/v1/wiki/spaces` | `listWikiSpaces` | signed in | 200 `WikiSpace[]` |
| `GET /api/v1/wiki/company-wide` | `listCompanyWideDocuments` | signed in | 200 `WikiDocumentSummary[]` |
| `GET /api/v1/wiki/spaces/{spaceId}/folders` | `listWikiFolders` | viewer | 200 `WikiFolder[]` |
| `GET /api/v1/wiki/spaces/{spaceId}/documents?folderId=` | `listWikiDocuments` | viewer | 200 `WikiDocumentSummary[]` |
| `GET /api/v1/wiki/documents/search?q=` | `searchWikiDocuments` | signed in | 200 `WikiDocumentSummary[]` |
| `GET /api/v1/wiki/documents/{documentId}` | `getWikiDocument` | viewer, or Knowledge Manager for the trash | 200 `WikiDocumentDetail` |
| `GET /api/v1/wiki/documents/{documentId}/file` | `downloadWikiDocumentFile` | viewer | 200 binary |
| `POST /api/v1/wiki/spaces/{spaceId}/folders` | `createWikiFolder` | Knowledge Manager | 201 `WikiFolder` |
| `PUT /api/v1/wiki/folders/{folderId}` | `updateWikiFolder` | Knowledge Manager | 200 `WikiFolder` |
| `DELETE /api/v1/wiki/folders/{folderId}` | `deleteWikiFolder` | Knowledge Manager | 204 |
| `POST /api/v1/wiki/spaces/{spaceId}/uploads/extract` | `extractWikiUploads` | Knowledge Manager | 200 `WikiUploadPreview[]` |
| `POST /api/v1/wiki/spaces/{spaceId}/documents` | `publishWikiDocuments` | Knowledge Manager | 201 `WikiDocumentRef[]` |
| `DELETE /api/v1/wiki/documents/{documentId}` | `trashWikiDocument` | Knowledge Manager | 204 |
| `GET /api/v1/wiki/spaces/{spaceId}/trash` | `listWikiTrash` | Knowledge Manager | 200 `WikiTrashItem[]` |
| `POST /api/v1/wiki/documents/{documentId}/restore` | `restoreWikiDocument` | Knowledge Manager | 200 `WikiRestoreResult` |
| `DELETE /api/v1/wiki/documents/{documentId}/permanent` | `deleteWikiDocumentPermanently` | Knowledge Manager | 204 |
| `GET /api/v1/wiki/spaces/{spaceId}/settings` | `getWikiSpaceSettings` | Knowledge Manager | 200 `WikiSpaceSettings` |
| `GET /api/v1/wiki/spaces/{spaceId}/permission-candidates` | `listWikiPermissionCandidates` | Knowledge Manager | 200 `WikiPermissionCandidates` |
| `POST /api/v1/wiki/spaces/{spaceId}/permissions` | `grantWikiPermissions` | Knowledge Manager | 201 |
| `DELETE /api/v1/wiki/permissions/{permissionId}` | `revokeWikiPermission` | Knowledge Manager | 204 |

Schemas:

| Schema | Fields |
|--------|--------|
| `WikiSpace` | `spaceId`, `name`, `departmentName`, `accessLevel` (`VIEW`, `KNOWLEDGE_MANAGER`) |
| `WikiFolder` | `folderId`, `parentId` (nullable), `name`, `level` |
| `WikiDocumentSummary` | `documentId`, `title`, `spaceId`, `spaceName`, `folderId` (nullable), `folderName` (nullable), `visibility` (`DEPARTMENT`, `COMPANY_WIDE`), `owner`, `updatedAt` |
| `WikiDocumentDetail` | `WikiDocumentSummary` fields plus `content`, `fileName`, `fileSize`, `canDelete` |
| `WikiUploadPreview` | `fileName`, `title`, `excerpt`, `wordCount`, `result` (`READY`, `UNREADABLE`) |
| `WikiPublishMetadata` (multipart part `metadata`, JSON) | `folderId` (nullable), `documents`: array of `{ title, visibility }` in file order |
| `WikiDocumentRef` | `documentId`, `title` |
| `WikiTrashItem` | `documentId`, `title`, `folderName` (nullable), `owner`, `deletedBy`, `deletedAt` |
| `WikiRestoreResult` | `title`, `renamed` |
| `WikiSpaceSettings` | `spaceId`, `name`, `departmentName`, `knowledgeManager` (nullable), `ownDepartment` `{ departmentId, name, locked: true }`, `departments`: `{ permissionId, departmentId, name, grantedBy, grantedAt }[]`, `users`: `{ permissionId, userId, fullName, email, homeDepartment, active, grantedBy, grantedAt }[]` |
| `WikiPermissionCandidates` | `departments`: `{ departmentId, name }[]`, `users`: `{ userId, fullName, email, homeDepartment }[]` |
| `GrantWikiPermissionsRequest` | `departmentIds`, `userIds` |
| `CreateWikiFolderRequest` | `name`, `parentId` (nullable) |
| `UpdateWikiFolderRequest` | `name`, `parentId` (nullable) |

Error table:

| Status | `code` | `params` | When |
|--------|--------|----------|------|
| 400 | MSG01 | `{field_name}` | Required field blank (`name`, `q`, `files`, `metadata`, `grantees`, `documents[i].title`) |
| 400 | MSG02 | `{field_name, max_length}` | Over 100 (folder name) or 200 (title, `q`) characters |
| 400 | MSG86 | `{}` | Folder too deep, or moved inside itself |
| 400 | MSG88 | `{}` | More than 10 files |
| 400 | MSG89 | `{file_name}` | Not PDF or DOCX |
| 400 | MSG90 | `{file_name}` | No readable text at publish |
| 401 | MSG07 | `{}` | Not signed in |
| 403 | MSG08 | `{}` | Not a viewer of the Space, or not its Knowledge Manager |
| 404 | MSG04 | `{}` | Unknown, Hidden or Deleted-Department Space; unknown folder, permission or grantee |
| 404 | MSG110 | `{}` | Document unknown, not visible, or not in the state the action needs |
| 409 | MSG84 | `{}` | Title used in the folder or the request |
| 409 | MSG85 | `{}` | Folder name used under the parent |
| 409 | MSG87 | `{folder_count, document_count}` | Folder not empty |
| 413 | MSG28 | `{}` | File larger than 10 MB |

`AuditAction` gains `FOLDER_CHANGE`.

Java ports implemented by wiki (their callers declared them):
- `vn.tsolve.chatbot.source.service.WikiSources` (AC-001.5, AC-001.6); `NoWikiSources` is deleted.
- `vn.tsolve.triage.service.WikiSourceRetrieval` (AC-001.7), once triage's KAN-47 is merged.
- `vn.tsolve.triage.service.WikiDocumentAccess` (AC-001.8); `NoWikiDocumentAccess` is deleted.
- `vn.tsolve.common.session.lookup.ManagedSpaceLookup` (AC-001.9).
- `vn.tsolve.common.session.lookup.ViewableSpaceLookup` (AC-001.10), a new interface `List<SpaceRef> findViewableSpaces(UUID userId)` next to `ManagedSpaceLookup`.

### Required from other modules
- workspace and identity: tables `space`, `space_manager`, `department`, `app_user`, `user_role`, `project_membership`, `project` (read only).
- platform: `FileStorage` (KAN-96), `TenantJobService` and `TenantJobHandler` (worker).
- ai: `EmbeddingClient.embed`, 1024 dimensions.
- common: `AuditService.record`, `BusinessRuleException`, `GlobalExceptionHandler` (413 MSG28), `TSolveUser`.
- fe-foundation: route guards, `DataTable`, `FormModal`, confirmation dialog, message catalog, `features/wiki` route stubs.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name cites its AC id (backend suffix `_AC_001_4`, frontend prefix `AC-002.14`)
- [ ] `sdd.ts trace wiki` passes
- [ ] Backend: `mvn verify` passes, including the ITs on real PostgreSQL with pgvector
- [ ] Frontend: `format:check`, `lint`, `typecheck`, `test` and `build` pass, and `generate:api` leaves no diff
- [ ] The changeset applies on an empty database and on one that holds the existing tables
- [ ] Each task is committed on its own after the user agrees
