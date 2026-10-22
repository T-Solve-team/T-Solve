---
name: project-integration
owner: Đặng Tấn Bình
repos: [default]
dependencies: [workspace]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/project/**
  - backend/src/test/java/vn/tsolve/project/**
  - frontend/src/features/jira-integration/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: project-integration

**Owner:** Đặng Tấn Bình

## Description
Ticket tracker integration (UC-16–18): a Project Manager adds a Project linked to a tracker project (Jira in the MVP), configures it, and adds or removes Ticket Resolvers. The backend package `project` is proposed; the backend lead confirms it when the module is planned.

## Responsibilities
- Projects: link and test the connection, field mapping, Done statuses, review range.
- Project members (Ticket Resolvers) and the Project's Project Manager.
- The Admin's Jira site address.
- Screens 3.1, 3.2.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Projects | Service interface + REST | Projects, members, roles in a Project |
| Tracker client | Client interface | Calls to the tracker's API |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| workspace | Departments | A Project belongs to a Department |

## Target Repos
- default: backend/ (`project`, proposed), frontend/ (`jira-integration`).

## Related Artifacts
- Spec: `artifacts/03-modules/project-integration/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
