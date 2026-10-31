---
module: dashboard-audit
date: 2026-10-31
total_tasks: 1
owner: Dev
---

# Tasks: dashboard-audit

Spec: artifacts/03-modules/dashboard-audit/spec.md
Master: artifacts/04-plans/dashboard-audit/design.md

## Task 1: View and filter audit log (KAN-153)

**Story:** STORY-001
**Feature:** audit-log
**Description:** Deliver the complete frontend-only screen on MSW in three sequential verification
stages. V1: define the OpenAPI proposal and generate types, then tests-first date/filter helpers and
scoped mock responses. V2: failing page tests, toolbar and query/table implementation. V3: all frontend
checks, deterministic regeneration, browser verification and code review. Cover AC-001.1–AC-001.17
and every High risk in master design rows 1–24. One code commit after user agreement, plus harness commit.
**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml`
- Regenerate: `frontend/src/shared/api/generated/**`
- Modify: `frontend/src/features/audit-log/pages/AuditLogPage.tsx`
- Modify: `frontend/src/features/audit-log/mocks/handlers.ts`
- Create: `frontend/src/features/audit-log/components/AuditLogToolbar.tsx`
- Create: `frontend/src/features/audit-log/components/AuditLogToolbar.module.css`
- Create: `frontend/src/features/audit-log/hooks/useAuditLog.ts`
- Create: `frontend/src/features/audit-log/hooks/audit-filters.ts`
- Create: `frontend/src/features/audit-log/mocks/data.ts`
- Test: `frontend/src/features/audit-log/hooks/audit-filters.test.ts`
- Test: `frontend/src/features/audit-log/mocks/handlers.test.ts`
- Test: `frontend/src/features/audit-log/pages/AuditLogPage.test.tsx`
**Dependencies:** approved spec/design; existing frontend foundations. No real backend is required.
**Estimated size:** medium (one screen, reused components, sequential V1–V3 checkpoints).

**Verification:** `sdd.ts trace dashboard-audit STORY-001`; frontend format:check, lint, typecheck,
test and build; generate:api twice with no second-run differences; real browser checks.
