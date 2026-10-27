---
module: identity
date: 2026-10-25
---

# Design: identity

Spec: artifacts/03-modules/identity/spec.md

## Architecture Overview

Implement only the frontend delivery of KAN-107–110. Keep the existing Admin routes and shell. Pages use feature hooks wrapping generated API hooks; TanStack Query owns server data. MSW implements the proposed contract with synthetic data while the real identity backend remains outside this delivery.

User List starts with search, role/status/Department filters and the Add user action, followed by the shared DataTable. User Detail has creation and editing modes on the existing routes. Account status and role changes use the shared confirmation patterns and the feature-local Hand Over Responsibilities popup when responsibilities must move. Required labels and messages follow the existing SRS guidance; detailed field layout must be checked against the team's source layouts before implementation.

Delivery is sequential: KAN-107, KAN-108, KAN-109, KAN-110. Each gets its issue branch and feature commit with a separate harness commit. Later stories build on previously reviewed code; do not start them before the preceding feature's user-approved commit. No push, merge or PR is authorized.

## Data Model

Use generated request/response models only. The frontend stub is a proposal for the backend owner, not a claim that these endpoints exist in production.

| Contract area | Required information | Existing convention |
|---|---|---|
| User list | User identity, email, roles, Active/Deactivated status and Department | Spring-style paged response; 20 rows by default |
| User detail | User identity, status, roles, Department, managed Spaces and responsibilities relevant to handover | UUID string user identifiers and existing Role enum |
| Creation | Company email, roles and their required assignments | Employee always included; generated request model |
| Role/assignment update | Requested roles, Department, managed Spaces and required handover choices | Validate entire change before changing mock records |
| Status update | Target status and any required handover choices | Retain account/history; prevent self-deactivation and last Active Admin deactivation |
| Lookup data | Workspace domain, Departments, Spaces, eligible replacement managers | API responses, rather than hard-coded business lists in components |
| Failures | Catalog code, parameters and field errors | Existing RFC 9457 Problem Details model |

Reuse the shared API generation pipeline, errors, paging, roles and messages. Exact endpoint names and schema property names must be documented with the contract change before implementing the corresponding hook. Keep lookups scoped to this feature's mock proposal until real backend interfaces are agreed.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Backend availability | Mutable MSW data behind generated hooks | Wait for real backend; static decorative screens | User explicitly selected working MSW screens, prepared for later integration |
| UI foundation | Existing shared UI kit and antd primitives | New components in shared; new UI library | Project rules and Ponytail: reuse existing kit without dependency changes |
| Server state | TanStack Query through generated hooks | Local copies; hand-written fetch calls | Existing frontend contract and invalidation conventions |
| List state | Existing useListSearchParams | Component-only filters | Reload, Back and shareable URLs use the same state |
| Mutation behavior | Wait for response, then invalidate affected keys | Optimistic changes | Existing MVP convention; failed changes never appear successful |
| Handover | Existing D3 popup flow for managed responsibilities | Deactivate first; generic confirmation only | KAN-109/110 and guideline 09 require completed handover |
| Authentication and real security | Existing route guards; mock authorization tests; production enforcement remains backend work | Treat browser checks as security | Scope explicitly excludes production identity endpoints |
| Delivery | One feature and issue branch at a time | Combine all four issues into one feature commit | Team commit/branch conventions take precedence over harness defaults |

## File Structure

```text
frontend/src/features/users/
  pages/UserListPage.tsx
  pages/UserListPage.test.tsx
  pages/UserListPage.module.css             # only if tokens/antd layout do not suffice
  pages/UserDetailPage.tsx
  pages/UserDetailPage.test.tsx
  components/UserListToolbar.tsx
  components/UserListTable.tsx
  components/HandOverResponsibilitiesModal.tsx
  hooks/useUsers.ts
  hooks/useUserDetail.ts
  hooks/useUserMutations.ts
  mocks/data.ts
  mocks/handlers.ts
  mocks/handlers.test.ts
frontend/openapi/tsolve-api.yaml            # only the contract required by each story
frontend/src/shared/api/generated/         # generator output only
```

This is a maximum outline, not a scaffolding requirement: create a file only when it is needed. Routes, menu, central mock registration and shared API exports already exist. Any necessary shared/app changes require FE Lead review; do not silently edit those files.

## External Dependencies

No new dependencies. Use installed React, React Router, antd, TanStack Query, orval, MSW, Vitest and React Testing Library. No live Jira mutations are part of this work.

## Edge Cases & Risks

Dimensions analyzed: user types, input extremes, timing, scale, state transitions, environment, error cascades, authorization, data integrity, integration, compliance and business logic. Infrastructure failures, migrations and real session revocation belong to the real backend and are not implemented by this mock delivery.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-004 | Authorization | Employee or signed-out user opens a users URL | High | Existing guard blocks or redirects access | Task 1 |
| 2 | STORY-004 | Data integrity | Fixture contains accounts from another Workspace | High | List response never returns those accounts | Task 1 |
| 3 | STORY-004 | Scale | Empty result; more than 20 matching users; last page | Medium | MSG04, correct totals and page boundaries | Task 1 |
| 4 | STORY-004 | State transitions | Change filter while on page 2; Back after searching | Medium | Filter resets page; URL restores visible state | Task 1 |
| 5 | STORY-004 | Timing | Earlier search response finishes after a newer query | Medium | Generated query keys keep the results separate | Task 1 |
| 6 | STORY-004 | Error cascades | List or lookup request fails | Medium | Catalog error and retry; no misleading empty result | Task 1 |
| 7 | STORY-005 | Input extremes | Blank, malformed, overlong or wrong-domain email | High | Reject with field message; preserve input | Task 2 |
| 8 | STORY-005 | Data integrity | Email belongs to Active or Deactivated account | High | MSG11 or MSG111; create no duplicate | Task 2 |
| 9 | STORY-005 | Business logic | Missing PM Department or occupied KM Space | High | Reject invalid assignments; Employee remains | Task 2 |
| 10 | STORY-005 | Timing | Submit twice while creation is pending | High | Disable submit and create at most one account | Task 2 |
| 11 | STORY-006 | Authorization | Deactivate self or last Active Admin | High | MSG112 or MSG56; retain original status | Task 3 |
| 12 | STORY-006 | Business logic | Managed Projects/Spaces have no replacement | High | MSG59/MSG118; no partial handover | Task 3 |
| 13 | STORY-006 | State transitions | Cancel confirmation or fail status request | High | Status and responsibilities remain unchanged | Task 3 |
| 14 | STORY-006 | Compliance | Deactivate an account with historical references | High | Retain account/history; do not delete records | Task 3 |
| 15 | STORY-007 | Data integrity | Role removal affects responsibilities and memberships | High | Handover and MSG78 where required; atomic update | Task 4 |
| 16 | STORY-007 | Authorization | Remove own Admin role or reuse stale session data | High | MSG113; invalidate affected session queries after success | Task 4 |
| 17 | STORY-007 | Integration | Contract regeneration changes generated exports | High | Regenerate, typecheck and verify no regeneration drift | Tasks 1–4 |
| 18 | STORY-004..007 | Environment | Keyboard operation at 1366 × 768; network failure | Medium | Accessible controls, no clipping, retry without losing edits | Tasks 1–4 |

## Risk Review — 2026-10-25 — Verdict: CAUTION

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| Contract | Generated stub fits current pipeline | Mock contract does not enforce production security | Paged queries limit list payloads | Working data enables usable screens | Static screens cannot prove interactions | Keep contract explicitly provisional and use MSW tests |
| Responsibility changes | Keep mutations in users feature | Reject invalid changes before mutation | Invalidate only affected query keys | Explain missing recipients with existing messages | Do not add a general workflow engine | Feature-local popup and atomic mock updates |
| Shared ownership | No shell/router rewiring is needed | Preserve central guards | Add no dependencies | Reuse familiar kit | Existing components cover this scope | Generated-only shared output; FE Lead review where required |

Agreements: reuse the existing stack and kit; use synthetic data; retain production-security limitations; verify failure flows rather than only happy paths. Security correctness takes precedence over shortcuts in responsibility changes. Contract/backend alignment remains a delivery limitation, mitigated by generated schemas and contract review before real integration. High-severity mitigations map to Tasks 1–4 above.

## Implementation gates

### Task 1 contract and source clarification — 2026-10-25

The SRS v3460 layout reference was located in the existing extracted source at `_ai/cache/Report/T-Solve_Report3_Software_Requirement_Specification_EN_v3460.docx.md` in the supplied SEP490_G89 documents folder, section III.1.3.1 and Table 98 (lines 1512–1519). Use its Full name, Email, Department, Roles, Status and Last sign-in columns, Add user action, 100-character search, and Employee only role filter. Report3/D3 remain authoritative for business rules where the older layout differs.

The user selected case-insensitive substring matching on name/email, Full name ascending by default, and sorting by Full name, Email and Last sign-in. This clarifies STORY-004 without changing its acceptance criteria. Missing names/sign-in timestamps stay empty; UUID user id breaks ties lexicographically in every mock sort.

Proposed stub endpoints: `GET /api/v1/users` (`getUsers`) accepts `q`, `role`, `status`, `departmentId`, `page`, `size`, `sort`. `role=EMPLOYEE` means Employee only; other roles mean membership. `GET /api/v1/users/departments` (`getUserDepartments`) returns selectable DepartmentRef records for this Workspace. List rows use a generated UserAccount with a UUID string id, nullable fullName, email, roles, DepartmentRef or null, status and nullable lastSignInAt; UserAccountPage reuses PageMetadata. The fixture Workspace is id 1, scoped inside mock resolvers before search/filter/paging; a foreign fixture proves isolation. Existing dev identities admin/admin-tr are mock Admins; non-Admins receive 403 and signed-out requests receive 401. These choices are mock contracts, not new shared session fields.

Builder check: source-table evidence and search/sort decisions are resolved. Reuse existing UI components, route guards and central mock registration. No new dependencies or direct shared/app edits are needed beyond generator output.

The user approved the spec and requested all four tasks in chat on 2026-10-25, then completed the user-run approval command. Approval is now present in approvals.json. Normal status transitions advanced identity through planned to implementing. The team issue branch `feat/KAN-107-identity-user-list` takes precedence over the harness default `feat/identity`; do not rewrite tracking statuses manually to change that default.

## KAN-107 review repair — 2026-10-26
Main's sign-in, logout and My Profile keep STORY-001–003. This frontend plan covers STORY-004–007. AC-004.8 explicitly requires the 100-character trimmed search limit: display MSG02, retain the input, and do not change the query, URL or displayed results on overlong submission. The mock rejects overlong q with 400 MSG02. UserAccount.id is a UUID string (OpenAPI format uuid), and the UUID breaks sorting ties lexicographically.

## KAN-108 PR #77 integration — 2026-10-26
Main32ce8cb includes KAN-205 UUID shared references and KAN-206 site/session behavior. Merge that history into the existing published KAN-108 branch. CreateUserRequest.departmentId is now nullable string(format uuid), spaceIds is string[](format uuid), and creation options/fixtures reuse the shared DepartmentRef/SpaceRef UUID shape. String required validation replaces the obsolete numeric Department rule. Keep both event streams and all incoming site/session behavior. Preserve main's BUG-017 and renumber the branch-local creation UUID defect to BUG-020; BUG-021 records the integration regression. No spec acceptance criterion changes.
## Approved mock-session bridge — KAN-109/110
The user explicitly allowed a small integration in frontend/src/mocks/session.ts for KAN-109/110 so status and role changes reach the mock /me response, with FE Lead review called out. Keep the central file change to a small adapter call; put mutable user-management fixture logic in feature-local mocks. Preserve untouched central MOCK_USERS fixtures, because their first-eight account roles/assignments do not fully match the list dataset. Only changed accounts should override session data. UserAccount ids are UUIDs; existing shared session/Department/Space references keep their current contracts. Re-read account status/roles on subsequent users mock requests, and reject deactivated actors. Production enforcement remains backend scope.

## Task 3 contract proposal — KAN-109

Before adding generated hooks, extend the mock proposal with `GET /api/v1/users/{id}` (`getUserDetail`), returning UserDetail: account fields, `isCurrentUser`, Projects and managed Spaces. `isCurrentUser` is computed by the API, without requiring the page to know development fixture mappings. `GET /api/v1/users/{id}/handover-options` (`getUserHandoverOptions`) returns eligible recipients per responsibility. `PUT /api/v1/users/{id}/status` (`updateUserStatus`) accepts the requested status and `projectHandovers` / `spaceHandovers`, and returns confirmed UserDetail. All account, Project, Department and Space references are UUID strings after main's KAN-205 migration.

Eligibility is Active, same Workspace, different user, with Project Manager in the Project's Department or Knowledge Manager for a Space. Validate all handovers and guards before any account/ownership mutation. Preserve history references separately from current responsibility ownership. The mock rechecks actor access, target scope, last Active Admin, self-deactivation and current recipient eligibility at submission. No eligible replacement produces MSG59 / MSG118; missing choices keep the popup open. No production backend guarantee is inferred from these mocks.
