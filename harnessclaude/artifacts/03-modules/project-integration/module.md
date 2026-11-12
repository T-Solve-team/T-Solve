---
name: project-integration
owner: Đặng Tấn Bình
repos: [default]
dependencies: [workspace, tracker, identity, worker]
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
Ticket tracker integration (UC-16–18): a Project Manager adds a Project linked to a tracker project (Jira in the MVP), configures it, and adds or removes Ticket Resolvers. The backend package `vn.tsolve.project` exists on `main`, with the Project entity, repository and `ProjectService.findActivePublicIdByJiraProjectKey` lookup. KAN-127 extends it for UC-16; UC-17 and UC-18 follow separately.

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

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| workspace | Departments | A Project belongs to a Department |
| tracker | `TrackerProjectService.findProject` | Browse check, canonical Jira key and project statuses; Project does not call the tracker client directly |
| identity | `TenantScope` and signed-in caller | Bound Workspace and caller id; own Department is read from existing tables through Project's repository |
| worker | Workspace execution boundary | Retained dependency; UC-16 schedules no job |

## Target Repos
- default: backend/ (existing `project` package), frontend/ (`jira-integration`).

## Related Artifacts
- Spec: `artifacts/03-modules/project-integration/spec.md` (UC-16, KAN-127)
- Map: `artifacts/03-modules/module-map.md`
