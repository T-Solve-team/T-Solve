---
name: ticket-review
repos: [default]
dependencies: []
complexity: large
paths: [backend/src/main/java/vn/tsolve/common/exception/**, backend/src/main/java/vn/tsolve/common/error/**, backend/src/test/java/vn/tsolve/common/exception/**, backend/src/main/java/vn/tsolve/knowledge/Solution.java, backend/src/main/java/vn/tsolve/knowledge/SolutionService.java, backend/src/main/java/vn/tsolve/ticket/**, backend/src/test/java/vn/tsolve/ticket/**, backend/src/main/resources/db/changelog/**, frontend/src/features/review/**, frontend/src/features/tickets/**, frontend/openapi/**, frontend/src/shared/api/generated/**, frontend/src/shared/api/http.ts, frontend/src/shared/api/http.test.ts, frontend/src/shared/messages/**, frontend/src/app/dev/KitPage.tsx, frontend/src/shared/ui/DataTable/DataTable.test.tsx, docs/bug-log.md]
---

# Module: ticket-review

## Description
Ticket-level Approve, Reject and Request changes, implemented sequentially against Report 3 v3460. External identity, Project administration and mail delivery remain integration responsibilities as selected by the user.

## Responsibilities
- Own queue queries, ticket-level review state and three review decisions.
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
| Existing database | PostgreSQL / Liquibase | Forward migration for review state and records; existing changesets are immutable |

## Target Repos
- default: monorepo backend, frontend and bug log; module code branch is feat/ticket-review after spec approval.

## Related Artifacts
- Spec: `artifacts/03-modules/ticket-review/spec.md`
- Discovery: `artifacts/02-discovery/context-brief.md`
- Architecture: `artifacts/03-modules/architecture.md`
