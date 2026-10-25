---
module: identity
date: 2026-10-25
owner: BA
---

# Spec: identity

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-25 | STORY-001..004 | Initial frontend scope for KAN-107–110, with working MSW data | User requested frontend screens for their four In Progress Jira stories and selected MSW before backend integration |

## Requirements

### Functional
1. Implement the Admin User List (1.3.1), User Detail (1.3.2, creation and editing), and required Hand Over Responsibilities popup (1.3.3).
2. Follow KAN-107, KAN-108, KAN-109 and KAN-110 as read from the signed-in user's Jira board on 2026-10-25. These are the only stories in this delivery; the broader identity module includes other use cases that remain outside this delivery.
3. Use the existing English screen names, shared UI kit, message catalog, role guards, form validation and registered routes.
4. Use generated API hooks with TanStack Query and realistic, mutable MSW handlers. Mock checks demonstrate business rules; they do not establish production security guarantees.

### Non-Functional
1. THE frontend SHALL render these screens at the project's minimum desktop viewport of 1366 × 768 without inaccessible actions or clipped form controls.
2. THE frontend SHALL provide keyboard-accessible controls, visible field labels, pending states, and disabled duplicate submission controls.
3. THE frontend SHALL keep server data in TanStack Query and filters, sorting and pagination in URL search parameters.
4. THE implementation SHALL add no npm dependencies and reuse existing feature, API generation, form and UI patterns.

## User Stories

### STORY-001: View Workspace users (KAN-107 / UC-04)
- As an Admin, I want to search and filter the users of my Workspace so that I can manage their accounts.
- Acceptance:
  - AC-001.1: WHEN an Admin opens `/users`, THE frontend SHALL display a User List containing only accounts returned for the current Workspace.
  - AC-001.2: WHEN an Admin submits a search or changes role, status or Department filters, THE frontend SHALL request and display the matching users using URL-backed filter state.
  - AC-001.3: WHEN an Admin changes the page or sort order, THE frontend SHALL request the corresponding results with 20 rows per page by default and preserve that state in the URL.
  - AC-001.4: WHEN the users request is pending, THE frontend SHALL display the existing loading state.
  - AC-001.5: IF the users request fails, THEN THE frontend SHALL display the catalog error and a retry action.
  - AC-001.6: IF no users match the request, THEN THE frontend SHALL display MSG04.
  - AC-001.7: IF the signed-in user lacks the Admin role, THEN THE existing route guard SHALL prevent access to the User List and User Detail routes.

### STORY-002: Add a user account (KAN-108 / UC-05)
- As an Admin, I want to add an account using a company email so that the employee can sign in.
- Acceptance:
  - AC-002.1: WHEN an Admin chooses to add a user, THE frontend SHALL open the creation mode of User Detail at `/users/new`.
  - AC-002.2: WHEN an Admin submits a new account, THE frontend SHALL validate required inputs, email format and the SRS email limit of 100 characters using catalog messages.
  - AC-002.3: IF an email is outside the Workspace domain or already belongs to an account, THEN THE mock API SHALL reject creation and THE frontend SHALL display the corresponding catalog validation message without losing form input.
  - AC-002.4: WHEN a new account is created, THE mock API SHALL include the Employee role and THE frontend SHALL prevent removing that baseline role.
  - AC-002.5: IF the Project Manager role is selected without a Department, THEN THE frontend SHALL prevent submission and identify the missing field.
  - AC-002.6: WHEN an Admin assigns Knowledge Manager Spaces, THE frontend SHALL use API-provided options and THE mock API SHALL enforce at most one Knowledge Manager per Space, returning MSG117 for conflicts.
  - AC-002.7: WHEN creation succeeds, THE frontend SHALL invalidate affected user queries and display the catalog success feedback.
  - AC-002.8: IF the email belongs to a Deactivated account, THEN THE mock API SHALL reject duplicate creation with MSG111 so the Admin can reactivate the existing account.

### STORY-003: Change account status (KAN-109 / UC-06)
- As an Admin, I want to deactivate departed employees while retaining their account history.
- Acceptance:
  - AC-003.1: WHEN an Admin activates a Deactivated account, THE frontend SHALL submit the change and refresh its detail and affected list queries after success.
  - AC-003.2: WHEN an Admin deactivates an account with no Active Project or managed Space requiring handover, THE frontend SHALL request confirmation using MSG57 before submitting the change.
  - AC-003.3: IF the target is the last Active Admin, THEN THE mock API SHALL reject deactivation and THE frontend SHALL display MSG56.
  - AC-003.4: IF the target is the current Admin, THEN THE frontend SHALL disable self-deactivation with MSG112.
  - AC-003.5: WHEN a Project Manager with Active Projects or a Knowledge Manager with managed Spaces is deactivated, THE frontend SHALL open Hand Over Responsibilities with MSG_NEW_2 and require eligible recipients for every responsibility before completion.
  - AC-003.6: IF no eligible replacement exists, THEN THE frontend SHALL block completion and display MSG59 for Project Managers or MSG118 for Knowledge Managers.
  - AC-003.7: WHEN deactivation succeeds, THE mock API SHALL retain the account and its responsibility/history references rather than delete the user.
  - AC-003.8: IF a status update fails or is cancelled, THEN THE frontend SHALL retain the last confirmed status and allow retry without reporting success.

### STORY-004: Update roles, Department and Spaces (KAN-110 / UC-07)
- As an Admin, I want to update a user's roles and assignments so that their responsibilities match their work.
- Acceptance:
  - AC-004.1: WHEN an Admin opens an existing User Detail, THE frontend SHALL load the selected user's roles, status, Department and managed Spaces from the API.
  - AC-004.2: WHEN an Admin saves role and assignment changes, THE mock API SHALL retain Employee, require a Department for Project Managers and enforce one Knowledge Manager per Space.
  - AC-004.3: WHEN a role or assignment change removes managed responsibilities, THE frontend SHALL require the existing D3 handover flow before submitting the completed change.
  - AC-004.4: IF an Admin attempts to remove their own Admin role, THEN THE frontend SHALL disable that action with MSG113.
  - AC-004.5: WHEN removing a Ticket Resolver role affects Project membership, THE frontend SHALL request confirmation with MSG78 before submitting the change.
  - AC-004.6: WHEN a role update succeeds, THE frontend SHALL invalidate affected list, detail and session queries so subsequent mock requests use the updated permissions.
  - AC-004.7: IF updating roles or assignments fails, THEN THE frontend SHALL preserve the unsaved form and display the API's catalog error without committing partial changes.

## Out of Scope
- Implementing real backend identity endpoints, authentication, session invalidation, audit persistence or production authorization. The backend must enforce those rules when connected; mock behavior is not evidence of production enforcement.
- UC-01–03 sign-in, logout and My Profile; standalone UC-08–09 functionality beyond handover required by the four selected stories.
- Workspace or Department administration, Jira integration, Wiki management, new shared components, dependency changes or redesigning the application shell.
- Real user data or a browser-persistent mock database; the MSW dataset resets with the mock runtime.

## Interface Contracts

### Exposed by this module
- Existing Admin routes: `/users`, `/users/new`, `/users/:userId` from `users/routes.ts`.
- An API contract stub in `frontend/openapi/tsolve-api.yaml`, generated through orval, for user list/detail and mutations plus the lookup data required by these screens. Endpoint and schema details belong in the design and are frontend mock proposals until agreed with the backend owner.
- Pagination and errors follow guideline 06: `{ content, page: { size, number, totalElements, totalPages } }`; RFC 9457 Problem Details with catalog `code`, `params` and optional `fieldErrors`.
- Mutable MSW handlers live in `frontend/src/features/users/mocks/handlers.ts`, using synthetic Workspace-scoped fixtures and validating rejected operations before changing mock records.

### Required from other modules
- `fe-foundation`: Admin route guards, `useSession`, shared UI kit, form rules, message catalog, generated API infrastructure and pre-registered feature mocks.
- `workspace`: Workspace email domain, Department choices and Space references, supplied by mock contracts for this delivery.
- `be-foundation`: real authorization and session/error enforcement for future backend integration.

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id; backend-only guarantees are identified as unverified in production.
- [ ] Each Jira story is delivered on its own issue branch and reviewed with the user before its feature commit and before starting the next story.
- [ ] UI verification covers the existing Admin shell at 1366 × 768, keyboard operation, loading, empty, failure and mutation feedback.
- [ ] Frontend format, lint, typecheck, test and build checks pass.
- [ ] API regeneration leaves no diff in generated files after the committed generation output.
- [ ] Shared/API ownership review requirements are preserved; generated files are never hand-edited.

## Sources and decisions
- Jira: KAN-107, KAN-108, KAN-109, KAN-110, read from the current user's filtered board on 2026-10-25.
- User decision on 2026-10-25: working frontend with MSW data, prepared for later API integration.
- `frontend/AGENTS.md`; guidelines 01, 02, 06, 07, 08 and 09; existing message catalog including MSG56, MSG57, MSG59, MSG78, MSG111–113, MSG117–118 and MSG_NEW_2.
- `module.md`, `../architecture.md` and `../../02-discovery/context-brief.md`.
