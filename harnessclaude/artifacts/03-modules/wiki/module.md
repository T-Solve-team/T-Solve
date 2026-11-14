---
name: wiki
owner: Đặng Nhật Minh
repos: [default]
# wiki also implements ports declared by chatbot (WikiSources) and triage (WikiSourceRetrieval, WikiDocumentAccess);
# they are not listed here because chatbot and triage already depend on wiki (no cycle).
dependencies: [workspace, identity, ai, platform, worker]
complexity: large
paths:
  - backend/src/main/java/vn/tsolve/wiki/**
  - backend/src/test/java/vn/tsolve/wiki/**
  - backend/src/main/resources/db/changelog/**
  - backend/src/main/resources/application.yml
  - backend/pom.xml
  - backend/src/main/java/vn/tsolve/common/audit/AuditAction.java
  - backend/src/main/java/vn/tsolve/common/session/lookup/ViewableSpaceLookup.java
  - backend/src/main/java/vn/tsolve/chatbot/source/service/impl/NoWikiSources.java
  - backend/src/test/java/vn/tsolve/chatbot/source/service/impl/NoWikiSourcesTest.java
  - backend/src/main/java/vn/tsolve/triage/service/impl/NoWikiDocumentAccess.java
  - backend/src/test/java/vn/tsolve/triage/service/impl/SuggestionResultServiceImplTest.java
  - backend/src/test/resources/wiki/**
  - frontend/openapi/**
  - frontend/src/features/wiki/**
  - frontend/src/features/audit-log/hooks/audit-filters.ts
  - frontend/src/features/audit-log/hooks/audit-filters.test.ts
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: wiki

**Owner:** Đặng Nhật Minh

## Description
Wiki Space and Wiki Document Management (UC-38–50): the Knowledge Manager manages Space access, folders (at most 3 levels) and the trash, and uploads and deletes Wiki documents; every user views the folders and documents they may see. Backend package `wiki`; web folder `frontend/src/features/wiki/` (moved from `fe-foundation` on 2026-11-13).

## Responsibilities
- Spaces and their access (View), folders, the trash.
- Wiki documents: upload (PDF and DOCX; CR-09, decision W2), view, delete; Company-wide documents.
- Indexing documents for retrieval.
- Screens 11.1 to 11.5.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Wiki REST | REST, tag `wiki` | Spaces, folders, documents, upload, trash, permissions |
| `WikiSources`, `WikiSourceRetrieval`, `WikiDocumentAccess` | Java ports declared by chatbot and triage | Documents a user may see, for retrieval and citations |
| `ManagedSpaceLookup`, `ViewableSpaceLookup` | Java ports in common | Spaces a user manages or can view |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| workspace | Departments | A Space belongs to a Department |
| identity | Knowledge Managers, users, roles, memberships | Who manages and who can view a Space (read-only SQL) |
| platform | `FileStorage` (KAN-96) | Original files |
| ai | `EmbeddingClient` | Passage and query vectors |
| worker | `TenantJobService` | Background indexing |

## Target Repos
- default: backend/ (`wiki`, proposed), frontend/ (`wiki`).

## Related Artifacts
- Spec: `artifacts/03-modules/wiki/spec.md`
- Plan: `artifacts/04-plans/wiki/design.md`, `artifacts/04-plans/wiki/iteration-1/tasks.md`
- Map: `artifacts/03-modules/module-map.md`
