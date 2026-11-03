---
name: ticket-repository
owner: Đặng Nhật Minh
repos: [default]
dependencies: [project-integration]
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/ticket/**
  - backend/src/test/java/vn/tsolve/ticket/**
  - backend/src/main/java/vn/tsolve/knowledge/**
  - backend/src/test/java/vn/tsolve/knowledge/**
  - frontend/src/features/ticket-import/**
  - frontend/src/features/tickets/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: ticket-repository

**Owner:** Đặng Nhật Minh

## Description
Ticket Import & Repository (UC-20–25), plus the backend of Solution Authoring (UC-19): tickets with their Solution, import from the tracker, CSV or manual entry (Published at once, GBR-23), drafts, import history, ticket detail, unpublish and re-publish.

## Responsibilities
- Tickets, Solutions in five sections, Evidence, drafts (GBR-26).
- Import from the tracker, CSV and manual entry; import history.
- Ticket Detail (UC-24); unpublish / re-publish (UC-25).
- The Solution Form submission endpoint used by the Jira App (UC-19).
- Screens 5.1–5.5.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Tickets | Service interface + REST | Ticket and Solution records |
| Submission | REST | Solution Form submission from the Jira App |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| project-integration | Projects | A ticket belongs to a Project |

## Target Repos
- default: backend/ (`ticket`, `knowledge`), frontend/ (`ticket-import`, `tickets`).

## Related Artifacts
- Spec: `artifacts/03-modules/ticket-repository/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-29 (epic), KAN-31, KAN-45, KAN-132…137
