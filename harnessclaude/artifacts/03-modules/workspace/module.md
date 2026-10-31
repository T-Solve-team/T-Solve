---
name: workspace
owner: Đặng Tấn Bình
repos: [default]
dependencies: [identity]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/workspace/**
  - backend/src/test/java/vn/tsolve/workspace/**
  - frontend/src/features/workspace/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: workspace

**Owner:** Đặng Tấn Bình

## Description
Workspace & Department Management (UC-10–15): the company information of the single Workspace (2.1 Workspace Setup, 2.2 Workspace Settings) and creating, updating, deleting and restoring Departments.

## Responsibilities
- Workspace information, and whether the Workspace is set up (`/me` `workspaceReady`).
- Departments: create, update, delete, restore.
- Screens 2.1–2.4.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Departments | Service interface + REST | Department records and their state |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| identity | Users | Admin-only access |

## Target Repos
- default: backend/ (`workspace`), frontend/ (`workspace`).

## Related Artifacts
- Spec: `artifacts/03-modules/workspace/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
