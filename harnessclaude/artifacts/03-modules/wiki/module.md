---
name: wiki
owner: Đặng Nhật Minh
repos: [default]
dependencies: [workspace, ai]
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/wiki/**
  - backend/src/test/java/vn/tsolve/wiki/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: wiki

**Owner:** Đặng Nhật Minh

## Description
Wiki Space and Wiki Document Management (UC-38–50): the Knowledge Manager manages Space access, folders (at most 3 levels) and the trash, and uploads and deletes Wiki documents; every user views the folders and documents they may see. The backend package `wiki` is proposed. The web folder `frontend/src/features/wiki/` moves from `fe-foundation` into these paths when the module is planned.

## Responsibilities
- Spaces and their access (View), folders, the trash.
- Wiki documents: upload (PDF, DOCX, MD, TXT; D4), view, delete; Company-wide documents.
- Indexing documents for retrieval.
- Screens 11.1, 11.2, 11.4, 11.5, 11.7.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Wiki documents | Service interface + REST | Documents a user may see, for retrieval |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| workspace | Departments | A Space belongs to a Department |
| identity | Knowledge Managers | Who manages a Space |

## Target Repos
- default: backend/ (`wiki`, proposed), frontend/ (`wiki`).

## Related Artifacts
- Spec: `artifacts/03-modules/wiki/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
