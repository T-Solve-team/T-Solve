---
name: import-ticket-list
repos: [default]
dependencies: []
complexity: large
paths: [backend/src/main/java/vn/tsolve/ticket/**, backend/src/test/java/vn/tsolve/ticket/**, backend/src/main/resources/db/changelog/changes/*-ticket-import*.sql, backend/src/main/resources/db/changelog/db.changelog-master.xml, backend/src/main/resources/application.yml, backend/pom.xml, backend/AGENTS.md, backend/src/main/java/vn/tsolve/common/error/**, backend/src/test/java/vn/tsolve/common/error/**, backend/src/main/java/vn/tsolve/common/config/MethodSecurityConfig.java, .env.example, frontend/openapi/tsolve-api.yaml, frontend/src/shared/api/generated/**, frontend/src/features/ticket-import/**]
---

# Module: import-ticket-list

## Description
Jira epic KAN-29. A Project Manager brings in tickets that were resolved before T-Solve existed, or outside Jira. There are three ways: Jira import (UC-20), CSV import (UC-21, Jira task KAN-31) and manual entry (UC-22). Every import goes through a preview that can be saved as a Draft. Imported tickets are saved directly as **Published** with the PM's approve decision (GBR-23). Each import, Completed or Failed, is recorded once in the Import History (UC-23).

## Responsibilities
- The **Ticket** entity for now: Ticket ID (unique), source (Jira import / CSV / Manual), title, description, the five-section Solution (GBR-33), writer, Project, status, link to its Import Batch. The existing `ResolvedTicketSnapshot` and `POST /api/tickets/import` (idempotent upsert) conflict with MSG31. The plan decides whether to extend or replace them.
- Import Batch / Import History: one per import, never edited or deleted.
- Import Drafts (GBR-26): private to their creator, deleted after a successful import.
- CSV parsing and validation (RESEARCH-001): RFC 4180, strict UTF-8 with BOM stripped, ≤ 10 MB, required columns, per-row errors, a server-side preview.
- Jira pull: date range, ≤ 100 issues with no Ticket, oldest first (GBR-15).
- Manual entry with Evidence (GBR-06).
- Screens: Import Tickets (Jira / CSV / Manual tabs), Import Preview, the Import Ticket Preview popup, Import History.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| `POST /api/v1/imports/csv` | REST (multipart) | Upload a CSV for one Project and return the Import Preview (valid and invalid rows) |
| `POST /api/v1/imports/previews/{previewId}/confirm` | REST | Import the remaining valid rows as Published tickets (MSG32 / MSG33 / MSG40) |
| `POST /api/v1/imports/jira/pull` | REST | Pull resolved issues for a date range into an Import Preview |
| `POST /api/v1/tickets` | REST | Manual entry of one ticket |
| Import Drafts CRUD | REST | Save, list, reopen and delete the PM's own import Drafts |
| `GET /api/v1/imports` | REST | Import History of the PM's Projects |
| `TicketService` (read) | in-process | Ticket lookup by Ticket ID, for the Solution Form and Search later |
| Ticket published | in-process event (after commit) | Lets the search module index new Published tickets; no consumer yet |

Exact paths and payloads are fixed in the design and in `frontend/openapi/tsolve-api.yaml`.

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| identity (not decomposed yet) | current user + role | The PM role check (`@PreAuthorize`). Until the identity module exists, tests use `@WithMockUser` |
| workspace (not decomposed yet) | Projects managed by the user, Project Active, Jira token | Behind a service interface with a minimal stub implementation until the workspace module exists |
| Jira Cloud | REST search API | UC-20 only |

`dependencies` is empty because none of these modules are decomposed yet. Add them when they are.

## Target Repos
- default (monorepo):
  - `backend/` — `vn.tsolve.ticket` (controller / service / repository / entity / dto / mapper) and Liquibase changesets `NNN-ticket-import-*.sql`
  - `frontend/` — `src/features/ticket-import/`

## Features (suggested order)
1. **KAN-31 CSV import** (UC-21): Ticket + Import Batch + preview + confirm. It lays the shared base.
2. Import History (UC-23)
3. Import Drafts (GBR-26)
4. Manual entry (UC-22)
5. Jira import (UC-20)

## Related Artifacts
- Spec: `artifacts/03-modules/import-ticket-list/spec.md`
- Context: `artifacts/02-discovery/context-brief.md`
- Research: `artifacts/01-research/RESEARCH-001-import-ticket-csv.md`
