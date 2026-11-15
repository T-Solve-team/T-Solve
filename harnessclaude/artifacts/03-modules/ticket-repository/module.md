---
name: ticket-repository
owner: Đặng Nhật Minh
repos: [default]
dependencies: [project-integration]
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/ticket/**
  - backend/src/test/java/vn/tsolve/ticket/**/Jira*
  - backend/src/test/java/vn/tsolve/ticket/**/*Draft*
  - backend/src/test/java/vn/tsolve/ticket/**/ImportPreviewIssue*
  - backend/src/test/java/vn/tsolve/ticket/**/SolutionRules*
  - backend/src/test/java/vn/tsolve/ticket/jira/**
  - backend/src/test/java/vn/tsolve/ticket/**/TicketStatus*
  - backend/src/test/java/vn/tsolve/ticket/**/Manual*
  - backend/src/test/java/vn/tsolve/ticket/**/TicketDecision*
  - backend/src/test/java/vn/tsolve/ticket/**/TicketPublication*
  - backend/src/main/java/vn/tsolve/review/controller/ReviewDecisionController.java
  - backend/src/main/java/vn/tsolve/knowledge/**
  - backend/src/test/java/vn/tsolve/knowledge/**
  - frontend/src/features/ticket-import/*.ts
  - frontend/src/features/ticket-import/pages/*Page.tsx
  - frontend/src/features/ticket-import/pages/Jira*
  - frontend/src/features/ticket-import/components/Jira*
  - frontend/src/features/ticket-import/components/*Draft*
  - frontend/src/features/ticket-import/components/SolutionFields*
  - frontend/src/features/ticket-import/components/Manual*
  - frontend/src/features/ticket-import/api/**
  - frontend/src/features/ticket-import/mocks/**
  - frontend/src/features/tickets/**
  - frontend/src/shared/ui/EvidenceUpload/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
  - backend/src/main/java/vn/tsolve/project/service/**
  - backend/src/main/resources/application.yml
  - .env.example
  - README.md
  - backend/src/test/java/vn/tsolve/ticket/**/ImportHistory*
  - backend/src/test/java/vn/tsolve/ticket/**/TicketDetail*
  - frontend/src/features/ticket-import/pages/ImportHistory*
  - backend/src/main/java/vn/tsolve/review/controller/ReviewReadController.java
  - backend/src/test/java/vn/tsolve/review/ReviewDecisionApiIT.java
  - frontend/src/features/review/components/ReviewTicketContent.tsx
  - frontend/src/features/review/components/ReviewTicketDetail.tsx
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
- Stories: KAN-29 (epic), KAN-31, KAN-45, KAN-132…137 (KAN-137 = STORY-010)
- KAN-135 (STORY-008) extends Ticket Detail code owned by `ticket-review` (spec D29): `ReviewReadController`,
  `ReviewTicketContent`, `ReviewTicketDetail` and `ReviewDecisionApiIT` are listed in `paths` for that reason; the
  `ticket/` files it touches are already covered by `backend/src/main/java/vn/tsolve/ticket/**`.
