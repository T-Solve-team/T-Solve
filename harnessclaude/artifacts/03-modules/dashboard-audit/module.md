---
name: dashboard-audit
owner: Trần Hải Anh (UC-34) · Phạm Tùng Dương (UC-35)
repos: [default]
dependencies: [ticket-review, search, triage, chatbot]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/analytics/**
  - backend/src/test/java/vn/tsolve/analytics/**
  - frontend/src/features/knowledge-dashboard/**
  - frontend/src/features/audit-log/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: dashboard-audit

**Owner:** Trần Hải Anh (UC-34) · Phạm Tùng Dương (UC-35)

## Description
Dashboard & Audit (UC-34–35): the knowledge dashboard for a Project Manager's Department or the whole Workspace (last 30 days by default; AI suggestions and their answers; knowledge gaps) and the audit log for the Admin.

## Responsibilities
- Dashboard figures (GBR-18).
- The audit log and audit entry detail (the read side of the audit events).
- Screens 9.1–9.3.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Dashboard | REST | Figures per Department or Workspace |
| Audit log | REST | Audit entries |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| ticket-review, search, triage, chatbot | Records | What the dashboard counts |
| be-foundation | Audit events | What the audit log shows |

## Target Repos
- default: backend/ (`analytics`), frontend/ (`knowledge-dashboard`, `audit-log`).

## Related Artifacts
- Spec: `artifacts/03-modules/dashboard-audit/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
