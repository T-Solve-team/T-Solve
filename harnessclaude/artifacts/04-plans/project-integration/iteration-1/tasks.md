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

## Task 3: Remove member API (KAN-129)

- **Story:** STORY-003
- **Feature:** `project-member-remove-api`
- **Covers:** AC-003.1–AC-003.5
- **Description:** `DELETE /api/v1/projects/{projectId}/members/{userId}` with the manager check (D3, D4), the one-statement delete of the `MEMBER` row (D14), 409 for a Removed Project (D15), audit `{"removed": [id]}` in the same transaction (D16). An IT proves a ticket by the removed member is unchanged (D17). Branch `feat/KAN-129-project-integration-remove-member`. Tests first; every test name starts with its AC id; E12–E13 have a test.
- **Files:**
  - modify `backend/src/main/java/vn/tsolve/project/controller/ProjectController.java`
  - modify `backend/src/main/java/vn/tsolve/project/service/ProjectMemberService.java`, `service/impl/ProjectMemberServiceImpl.java`
  - modify `backend/src/main/java/vn/tsolve/project/repository/ProjectMemberRepository.java`
  - modify `backend/src/test/java/vn/tsolve/project/ProjectMembersIT.java`, `service/impl/ProjectMemberServiceImplTest.java`
- **Verify:** `mvn verify` in `backend/`.
- **Depends on:** Task 1

## Task 4: Remove button on the Members tab (KAN-129)

- **Story:** STORY-001, STORY-003
- **Feature:** `project-member-remove-tab`
- **Covers:** AC-001.7, AC-003.6–AC-003.10
- **Description:** Add the DELETE to `frontend/openapi/tsolve-api.yaml`, regenerate, a Remove button on `MEMBER` rows while the Project is Active, MSG55 confirm (D18), MSG05 / MSG06, MSW handler. Tests first; every test name starts with its AC id.
- **Files:**
  - modify `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
  - modify `frontend/src/features/jira-integration/components/MembersTable.tsx`, `pages/ProjectDetailPage.tsx` (+ tests)
  - create `frontend/src/features/jira-integration/hooks/useRemoveMemberFromProject.ts`
  - modify `frontend/src/features/jira-integration/mocks/handlers.ts`
- **Verify:** in `frontend/`: `npm run format:check`, `lint`, `typecheck`, `test`, `build`; `npm run generate:api` leaves no diff.
- **Depends on:** Task 3
