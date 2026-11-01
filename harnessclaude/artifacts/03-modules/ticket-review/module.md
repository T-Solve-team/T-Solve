---
name: ticket-review
owner: Phạm Tùng Dương
repos: [default]
dependencies: [be-foundation, identity, project-integration, ticket-repository, worker]
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/review/**
  - backend/src/test/java/vn/tsolve/review/**
  - frontend/src/features/review/**
  - frontend/src/features/tickets/pages/TicketDetailPage.tsx
  - frontend/src/features/tickets/pages/TicketDetailPage.module.css
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: ticket-review

**Owner:** Phạm Tùng Dương

## Description
Review & Approval Workflow (UC-29–33): the Project Manager of a Project reviews the Solutions that Ticket Resolvers
submit from the Jira App. The Review Queue is per Project and updated in real time; a decision is Approve (the ticket
becomes Published), Reject or Request changes (an email goes to the writer, who resubmits from Jira). A ticket not
reviewed within the Project's review range (default 3 days) is set to Expired by JOB-02 at 00:00 and can be sent back
to the Review Queue. Imported tickets never enter the Review Queue (GBR-23).

PR #100 follows the user-approved KAN-24 frontend/MSW spec in this folder, using Department filtering and UUID contracts. Its current iteration depends on fe-foundation only; backend responsibilities below are the full-module roadmap and remain future integration work. Ticket Detail is the second planned feature. PR #26 remains separate; its older backend spec is not imported or approved by this delivery. The backend package `review` remains proposed.

## Responsibilities
- UC-29 Review Queue per Project, real time (KAN-154).
- UC-30 Approve, Reject, Request changes with a comment (KAN-24); email to the writer on Reject / Request changes,
  sent as a `worker` job (KAN-155); send an Expired ticket back to the Review Queue (KAN-156).
- UC-31 View Expired tickets (KAN-158).
- UC-32 View review history of a ticket (KAN-159).
- UC-33 JOB-02: a recurring job per Workspace at 00:00 marks overdue tickets Expired (KAN-157).
- Audit events for every decision, on `be-foundation`'s audit writer.
- Screens: Review Queue, Review Detail, Expired tickets, Review History.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Review Queue and decisions | REST | Per-Project queue, Approve / Reject / Request changes, Expired list, history |
| Review events | In-process event | Ticket Published / Changes requested, for `search` and `jira-app` |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| ticket-repository | Tickets | Ticket, Solution and status records |
| project-integration | Projects | The Project Manager of a Project, its review range |
| identity | Accounts | The current user and their Projects |
| worker | Job queue, email | JOB-02 and the emails to the writer |
| be-foundation | Roles, errors, audit | `@PreAuthorize`, `ProblemDetail`, audit events |

## Target Repos
- default: backend/ (`review`, proposed), frontend/ (`review`).

## Related Artifacts
- Spec: `artifacts/03-modules/ticket-review/spec.md` (approved frontend/MSW slice; backend roadmap remains future scope)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-24, KAN-74, KAN-154, KAN-155, KAN-156, KAN-157, KAN-158, KAN-159
