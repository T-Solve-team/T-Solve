---
name: ticket-review
owner: Phạm Tùng Dương
repos: [default]
dependencies: [be-foundation, ticket-repository]
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/review/**
  - backend/src/test/java/vn/tsolve/review/**
  - frontend/src/features/review/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - backend/src/main/resources/db/changelog/**
  - docs/bug-log.md
  - frontend/src/shared/messages/catalog.ts
---

# Module: ticket-review

## Description
Ticket-level Approve, Reject and Request changes, implemented sequentially against Report3 with FE Lead D1/D2. External identity, Project administration and mail delivery remain integration responsibilities as selected by the user.

## Responsibilities
- Own queue queries and three review decisions using ticket-repository's canonical ticket/state/history contract.
- Persist decisions, audit records and pending notification handoffs atomically.
- Implement the real API contract and review actions in the existing web frontend.
- Document integration limitations and verify acceptance criteria and logged regressions.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Review Queue and decision endpoints | REST | Project-scoped paging, selection and decisions |
| Ticket review detail | REST | Solution, Evidence references, latest review comment and permitted actions |
| Review service | Java interface / DTO records | Register submitted ticket data and obtain Published review records for integrations |
| Notification handoff | Persisted records / Java interface | Rejection/change-request notification data for a future mail sender |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| Identity / Project integration | Trusted server-side auth and authorization port | Current user, account status, Project management and Department access; missing access fails closed |
| ticket-repository / KAN-31 | Canonical ticket service / DTOs | One ticket/Solution identity and decision history; shared transaction and canonical author identity mapping |
| be-foundation | Shared audit, roles and exception services | record(AuditAction, resourceType, resourceId); preserve legacy message-only callers |
| Existing database | PostgreSQL / Liquibase | Forward migration for review state and records; existing changesets are immutable |

## Target Repos
- default: monorepo backend, frontend and bug log; module code branch is feat/ticket-review after spec approval.

## Related Artifacts
- Spec: `artifacts/03-modules/ticket-review/spec.md`
- Discovery: `artifacts/02-discovery/context-brief.md`
- Architecture: `artifacts/03-modules/architecture.md`
