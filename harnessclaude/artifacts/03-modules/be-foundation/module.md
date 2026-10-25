---
name: be-foundation
owner: Anh Tran Hai
repos: [default]
dependencies: [platform]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/common/config/**
  - backend/src/main/java/vn/tsolve/common/error/**
  - backend/src/main/java/vn/tsolve/common/exception/**
  - backend/src/main/java/vn/tsolve/common/security/**
  - backend/src/main/java/vn/tsolve/common/session/**
  - backend/src/main/java/vn/tsolve/common/audit/**
  - backend/src/main/java/vn/tsolve/common/util/**
  - backend/src/test/java/vn/tsolve/common/config/**
  - backend/src/test/java/vn/tsolve/common/error/**
  - backend/src/test/java/vn/tsolve/common/exception/**
  - backend/src/test/java/vn/tsolve/common/security/**
  - backend/src/test/java/vn/tsolve/common/session/**
  - backend/src/test/java/vn/tsolve/common/audit/**
  - backend/src/test/java/vn/tsolve/common/util/**
  - backend/src/main/java/vn/tsolve/TSolveApplication.java
  - backend/src/main/resources/application*.yml
  - backend/src/main/resources/db/changelog/**
  - backend/pom.xml
  - backend/AGENTS.md
  - backend/src/main/java/vn/tsolve/knowledge/Solution.java
  - backend/src/main/java/vn/tsolve/knowledge/SolutionService.java
  - backend/src/main/java/vn/tsolve/ticket/TicketService.java
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: be-foundation

**Owner:** Anh Tran Hai

## Description
The backend base every module uses: the five Report3 roles and several roles per user in Spring Security, the `/me` session endpoint, RFC 9457 `ProblemDetail` errors with a Report3 MSG `code` (BUG-001), and the audit event writer for the listed actions.

## Responsibilities
- Role constants and `@PreAuthorize` support for Employee, Ticket Resolver, Project Manager, Knowledge Manager, Admin (GBR-28).
- `GET /api/v1/me`: roles, Department, Departments, Projects, managed Spaces, and whether the Workspace is set up.
- Base exceptions and `GlobalExceptionHandler` → `ProblemDetail` with `code`, `params`, `fieldErrors`.
- Audit events: append-only, written in the same transaction as the action.
- The Liquibase master changelog.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Session | REST `/api/v1/me` | Who is signed in and with which roles |
| Roles | Java constants / `@PreAuthorize` | Role checks for every endpoint |
| Errors | Base exceptions | Module exceptions extend them; mapped to `ProblemDetail` |
| Audit | Service interface | Record an audit event for a listed action |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| identity | User and role data | The roles the session reports |
| platform | TenantContext (`common/tenant`) | The Workspace of the request; audit events and `/me` belong to it |

## Target Repos
- default: backend/ (`common/` except `common/tenant/`, which `platform` owns since 25/10, KAN-195; configuration,
  the changelog master).

## Related Artifacts
- Spec: `artifacts/03-modules/be-foundation/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
