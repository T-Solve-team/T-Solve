---
module: dashboard-audit
date: 2026-10-31
total_tasks: 3
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

---

## Task 2: Record audit entries (KAN-152)

**Story:** STORY-002
**Feature:** audit-writer
**Description:** Changeset 018 (drop `audit_event`, create append-only `audit_log_entry`); `common/audit`
`AuditAction`, `AuditService`, `impl/AuditServiceImpl` (JdbcTemplate, caller's transaction, actor from
`TSolveUser`); `TicketDecisionServiceImpl.decide` and `ImportConfirmServiceImpl.importRows` call `record` on success;
existing tests that build those services get an `AuditService`. Tests first.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/018-audit-log-entry.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `backend/src/main/java/vn/tsolve/common/audit/AuditAction.java`, `AuditService.java`, `impl/AuditServiceImpl.java`
- Modify: `backend/src/main/java/vn/tsolve/ticket/service/impl/TicketDecisionServiceImpl.java`, `ImportConfirmServiceImpl.java`
- Modify (constructor/imports only): tests that build those two services
- Test: `backend/src/test/java/vn/tsolve/analytics/AuditLogEntryIT.java` (AC-002.1–AC-002.6, AC-002.11),
  `backend/src/test/java/vn/tsolve/analytics/AuditRecordingIT.java` (AC-002.7–AC-002.10)
**Covers:** AC-002.1–AC-002.11; edge cases 28–33
**Dependencies:** approved spec
**Estimated size:** medium

---

## Task 3: Serve the audit log (KAN-152, for KAN-153)

**Story:** STORY-003
**Feature:** audit-log-api
**Description:** `analytics` read side of `GET /api/v1/audit-events` per the contract: entity, repository with
Specifications, service with filter parsing (MSG39 / MSG06), MapStruct mapper, controller (Admin). Contract
description updated (no longer "MSW only"); `npm run generate:api` rerun and checked.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/analytics/{entity/AuditLogEntry, repository/AuditLogEntryRepository,
  service/AuditLogService, service/impl/AuditLogServiceImpl, dto/AuditEntryResponse, mapper/AuditEntryMapper,
  exception/InvalidAuditFilterException, controller/AuditLogController}.java`
- Modify: `frontend/openapi/tsolve-api.yaml` (description only); regenerate `frontend/src/shared/api/generated/**`
- Test: `backend/src/test/java/vn/tsolve/analytics/AuditLogServiceImplTest.java` (AC-003.2, 003.6, 003.7),
  `AuditLogControllerTest.java` (AC-003.6–003.8 rendering, 401/403), `AuditLogIT.java` (AC-003.1–003.5)
**Covers:** AC-003.1–AC-003.8; edge cases 34–38
**Dependencies:** Task 2 (table)
**Estimated size:** medium
