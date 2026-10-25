---
module: identity
date: 2026-10-25
total_tasks: 4
owner: Dev
---

# Tasks: identity

Spec: artifacts/03-modules/identity/spec.md
Master: artifacts/04-plans/identity/design.md

## Task 1: Workspace User List — KAN-107

**Story:** STORY-001
**Feature:** user-list
**Description:** Replace the User List placeholder with search, role/status/Department filtering, pagination and sorting. Preserve URL state, use the registered Admin guard, and handle loading, retry and MSG04. Add only the list and lookup contract needed by this screen; generate its hooks and implement Workspace-scoped MSW data.
**Files:**
- Modify: `frontend/src/features/users/pages/UserListPage.tsx`
- Create as needed: `frontend/src/features/users/components/UserListToolbar.tsx`, `frontend/src/features/users/components/UserListTable.tsx`, `frontend/src/features/users/hooks/useUsers.ts`, `frontend/src/features/users/mocks/data.ts`
- Modify: `frontend/src/features/users/mocks/handlers.ts`, `frontend/openapi/tsolve-api.yaml`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/users/pages/UserListPage.test.tsx`, `frontend/src/features/users/mocks/handlers.test.ts`
**Dependencies:** User-run spec approval, plan validation and normal module status transitions.
**Estimated size:** medium
**Verification:** Tests name AC-001.1–AC-001.7 and cover master risk rows 1–6, 17 and 18. Run all required frontend checks and regeneration drift check. Inspect browser rendering at 1366 × 768. Stop for user approval of `feat(identity): add workspace user list (KAN-107)` before committing and before Task 2.

## Task 2: Add user — KAN-108

**Story:** STORY-002
**Feature:** add-user
**Description:** Implement User Detail creation mode, email validation, required role assignments and creation feedback using generated types and mutable MSW handlers. Preserve form input on failed requests. Include duplicate and Deactivated-account email errors.
**Files:**
- Modify: `frontend/src/features/users/pages/UserDetailPage.tsx`, `frontend/src/features/users/mocks/handlers.ts`, `frontend/src/features/users/mocks/data.ts`, `frontend/openapi/tsolve-api.yaml`
- Create as needed: `frontend/src/features/users/hooks/useUserMutations.ts`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/users/pages/UserDetailPage.test.tsx`, `frontend/src/features/users/mocks/handlers.test.ts`
**Dependencies:** Task 1 verified and committed with user approval; next issue branch follows team conventions.
**Estimated size:** medium
**Verification:** AC-002.1–AC-002.8; master risks 7–10, 17 and 18; all frontend checks and browser inspection. Stop for approval of `feat(identity): add workspace user accounts (KAN-108)` before committing and before Task 3.

## Task 3: Account status and required handover — KAN-109

**Story:** STORY-003
**Feature:** account-status
**Description:** Add existing-user loading and status controls, confirmations, self/last-Admin protection, and required atomic handover for managed Projects and Spaces. Retain history and unchanged state on failure/cancel.
**Files:**
- Modify: `frontend/src/features/users/pages/UserDetailPage.tsx`, `frontend/src/features/users/hooks/useUserMutations.ts`, `frontend/src/features/users/mocks/handlers.ts`, `frontend/src/features/users/mocks/data.ts`, `frontend/openapi/tsolve-api.yaml`
- Create as needed: `frontend/src/features/users/hooks/useUserDetail.ts`, `frontend/src/features/users/components/HandOverResponsibilitiesModal.tsx`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/users/pages/UserDetailPage.test.tsx`, `frontend/src/features/users/mocks/handlers.test.ts`
**Dependencies:** Task 2 verified and committed with user approval; next issue branch follows team conventions.
**Estimated size:** medium
**Verification:** AC-003.1–AC-003.8; master risks 11–14, 17 and 18; all frontend checks and browser inspection. Stop for approval of `feat(identity): manage account status and handover (KAN-109)` before committing and before Task 4.

## Task 4: Edit roles and assignments — KAN-110

**Story:** STORY-004
**Feature:** user-roles
**Description:** Complete editing mode for roles, Department and managed Spaces; reuse handover, protect own Admin role, confirm Ticket Resolver removal, validate assignments atomically and invalidate affected session/list/detail queries.
**Files:**
- Modify: `frontend/src/features/users/pages/UserDetailPage.tsx`, `frontend/src/features/users/hooks/useUserDetail.ts`, `frontend/src/features/users/hooks/useUserMutations.ts`, `frontend/src/features/users/components/HandOverResponsibilitiesModal.tsx`, `frontend/src/features/users/mocks/handlers.ts`, `frontend/src/features/users/mocks/data.ts`, `frontend/openapi/tsolve-api.yaml`
- Generate only: `frontend/src/shared/api/generated/**`
- Test: `frontend/src/features/users/pages/UserDetailPage.test.tsx`, `frontend/src/features/users/mocks/handlers.test.ts`
**Dependencies:** Task 3 verified and committed with user approval; next issue branch follows team conventions.
**Estimated size:** medium
**Verification:** AC-004.1–AC-004.7; master risks 15–18; all frontend checks and browser inspection. Stop for approval of `feat(identity): update user roles and assignments (KAN-110)` before committing. Record production backend enforcement as unverified.
