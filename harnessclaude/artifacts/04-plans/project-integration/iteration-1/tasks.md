# Tasks: project-integration — iteration 1 (KAN-128)

Spec: `artifacts/03-modules/project-integration/spec.md` · Master design: `artifacts/04-plans/project-integration/design.md`

Branch: `feat/KAN-128-project-integration-add-ticket-resolver`. One commit per task (team rules in the root `CLAUDE.md`).

## Task 1: Project members API

- **Story:** STORY-001, STORY-002
- **Feature:** `project-members-api`
- **Covers:** AC-001.1–AC-001.4, AC-002.1–AC-002.8
- **Description:** The four endpoints of the spec's Interface Contracts, the manager check (design D3, D4), all-or-nothing add with audit in one transaction (D5, D6), candidate search (D8), `AuditAction.PROJECT_MEMBER_CHANGE` + migration 030 (029 is identity's, KAN-310). Tests first; every test name starts with its AC id. Edge cases E1–E9, E11 each have a test.
- **Files:**
  - create `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java`
  - create `backend/src/main/java/vn/tsolve/project/dto/{ProjectSummaryResponse,ProjectMemberResponse,MemberCandidateResponse,AddProjectMembersRequest}.java`
  - create `backend/src/main/java/vn/tsolve/project/service/ProjectMemberService.java`, `service/impl/ProjectMemberServiceImpl.java`
  - create `backend/src/main/java/vn/tsolve/project/repository/ProjectMemberRepository.java`
  - modify `backend/src/main/java/vn/tsolve/common/audit/AuditAction.java`
  - create `backend/src/main/resources/db/changelog/changes/030-project-audit-member-change.sql`; modify the master changelog
  - create tests under `backend/src/test/java/vn/tsolve/project/`
- **Verify:** `mvn verify` in `backend/`.
- **Depends on:** —

## Task 2: Project Detail Members tab

- **Story:** STORY-001, STORY-002
- **Feature:** `project-members-tab`
- **Covers:** AC-001.5–AC-001.7, AC-002.9–AC-002.16
- **Description:** Pull the OpenAPI from Task 1's backend into `frontend/openapi/tsolve-api.yaml`, regenerate the client, replace the 3.2 placeholder with header + Members tab + Add member popup (design D10, D11), MSW handlers, audit-log label for `PROJECT_MEMBER_CHANGE`. Tests first; every test name starts with its AC id.
- **Files:**
  - modify `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
  - modify `frontend/src/features/jira-integration/pages/ProjectDetailPage.tsx`
  - create `frontend/src/features/jira-integration/components/{ProjectHeader,MembersTable,AddMemberModal}.tsx` + tests
  - modify `frontend/src/features/jira-integration/mocks/handlers.ts`; create `mocks/data.ts`
  - modify `frontend/src/features/audit-log/hooks/audit-filters.ts` (and audit-log mocks/tests that list every action)
- **Verify:** in `frontend/`: `npm run format:check`, `lint`, `typecheck`, `test`, `build`; `npm run generate:api` leaves no diff.
- **Depends on:** Task 1
