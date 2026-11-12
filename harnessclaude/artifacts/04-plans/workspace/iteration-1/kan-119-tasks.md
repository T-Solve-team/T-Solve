---
module: workspace
date: 2026-11-12
total_tasks: 8
owner: Dev
---

# Tasks: workspace — iteration 1 (KAN-119)

Spec: artifacts/03-modules/workspace/spec.md
Design: artifacts/04-plans/workspace/iteration-1/design.md (Master: artifacts/04-plans/workspace/design.md)

Every test name starts with the AC id it proves (`AC-001.5 …`). Backend verify: `mvn verify` in `backend/`.
Frontend verify (in `frontend/`): `npm run format:check`, `lint`, `typecheck`, `test`, `build`; `npm run generate:api`
leaves no diff.

## Task 1: Workspace table, entity and token cipher

**Story:** STORY-007, STORY-001
**Feature:** workspace-store
**Description:** Changeset 027 (rename legacy `workspace` → `legacy_workspace`, create `workspace` as design "Data
Model", explicit PK name `pk_workspace_info`, rollback) and its master include. JPA entity `WorkspaceInfo` + repository.
`TokenCipher` (AES-256-GCM, 96-bit random nonce, AAD = workspace id bytes, format `v1:`+Base64), key from
`tsolve.workspace.secret-key: ${WORKSPACE_SECRET_KEY}` with no default; startup fails when missing / not Base64 of
32 bytes, message names the setting only. Test key in test config; `WORKSPACE_SECRET_KEY=` documented in
`.env.example` with how to generate it (`openssl rand -base64 32`). `toString()` of the entity hides the token.
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/027-workspace-info.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `backend/src/main/java/vn/tsolve/workspace/entity/WorkspaceInfo.java`, `repository/WorkspaceInfoRepository.java`, `crypto/TokenCipher.java`
- Modify: `backend/src/main/resources/application.yml`, test `application*.yml`, `.env.example`
- Test: `backend/src/test/java/vn/tsolve/workspace/crypto/TokenCipherTest.java`, `backend/src/test/java/vn/tsolve/workspace/repository/WorkspaceInfoRepositoryIT.java` (singleton constraint, legacy FKs still valid)
**Dependencies:** none
**Estimated size:** medium

---

## Task 2: Service account check in the tracker client

**Story:** STORY-007
**Feature:** tracker-account-check
**Description:** Add `void checkServiceAccount(JiraAccount account)` to `TicketTrackerClient` and `JiraClient`:
`GET /rest/api/3/myself`, then `GET /rest/api/3/mypermissions?permissions=BROWSE_PROJECTS,CREATE_ISSUES,MODIFY_REPORTER`;
throws `JiraRejectedException` / `JiraUnavailableException` as the existing `call()` does, and a new
`JiraPermissionMissingException` (or `JiraRejectedException(403)`) when any of the three has `havePermission` false or is
absent. No body or credential in exceptions or logs. Update any test double of `TicketTrackerClient`.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/tracker/client/TicketTrackerClient.java`, `JiraClient.java`
- Test: `backend/src/test/java/vn/tsolve/tracker/client/JiraClientTest.java` (MockRestServiceServer: pass; 401; 403; 500; one permission false; missing key)
**Dependencies:** none
**Estimated size:** small

---

## Task 3: Jira site rule and interim site linker

**Story:** STORY-007, STORY-001
**Feature:** jira-site-linker
**Description:** `JiraSiteRule.normalise(String) → Optional<String>` per AC-007.3. Port `JiraSiteLinker`
(`String linkJiraSite(String normalisedSiteUrl)` returning the cloudId; throws `JiraSiteLinkRefusedException` with
reason `LINKED_ELSEWHERE` or `UNREADABLE`) — Javadoc: contract of platform AC-004.19..24, to be replaced by
`WorkspaceCatalog.linkJiraSite`. `InterimJiraSiteLinker` (@Component): GET `<site>/_edge/tenant_info` with its own
`SimpleClientHttpRequestFactory` that follows no redirects, timeouts from `tsolve.tracker.jira.*`; non-200 / no
`cloudId` / IO error → `UNREADABLE`, logged without secrets. Mark `[Interim]` in Javadoc.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/workspace/site/JiraSiteRule.java`, `JiraSiteLinker.java`, `InterimJiraSiteLinker.java`, `JiraSiteLinkRefusedException.java`
- Test: `backend/src/test/java/vn/tsolve/workspace/site/JiraSiteRuleTest.java` (edge 2, 3), `InterimJiraSiteLinkerTest.java` (200, 404, 302, no cloudId)
**Dependencies:** none
**Estimated size:** small

---

## Task 4: Setup and read API, status lookup, OpenAPI

**Story:** STORY-001, STORY-002
**Feature:** workspace-setup-api
**Description:** `WorkspaceStatusLookup` interface in `common/lookup` (`boolean isReady()`) and
`WorkspaceStatusLookupImpl`. `WorkspaceService.setup/get` + controller `POST /api/v1/workspace/setup` (201) and
`GET /api/v1/workspace`, Admin only, order and errors exactly as design "Setup order" and AC-001.x / AC-002.x
(`FieldMessageCodeException` / `BusinessRuleException` patterns of identity; 409 code `WORKSPACE_ALREADY_SET_UP`; map
the singleton unique violation to 409). Audit per AC-001.12. Workspace id from `TenantScope.currentWorkspaceId()`.
Add the three operations and schemas `WorkspaceSetupRequest`, `WorkspaceUpdateRequest`, `WorkspaceInfo` to
`frontend/openapi/tsolve-api.yaml` (PUT too, used by Task 5) and run `npm run generate:api` in `frontend/`.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/lookup/WorkspaceStatusLookup.java`
- Create: `backend/src/main/java/vn/tsolve/workspace/controller/WorkspaceController.java`, `dto/*`, `service/WorkspaceService.java`, `service/impl/WorkspaceServiceImpl.java`, `service/impl/WorkspaceStatusLookupImpl.java`
- Modify: `frontend/openapi/tsolve-api.yaml`, `frontend/src/shared/api/generated/**`
- Test: `backend/src/test/java/vn/tsolve/workspace/service/impl/WorkspaceServiceImplTest.java`, `backend/src/test/java/vn/tsolve/workspace/controller/WorkspaceControllerIT.java` (edges 1, 4, 7, 8, 9, 20)
**Dependencies:** Task 1, Task 2, Task 3
**Estimated size:** medium

---

## Task 5: Update API (UC-11)

**Story:** STORY-003
**Feature:** workspace-update-api
**Description:** `PUT /api/v1/workspace` per AC-003.1..10: blank token keeps stored; Jira check only when email or
token changed (with stored values filled in); `jiraSiteUrl` ignored; audit old/new + `tokenChanged`.
**Files:**
- Modify: `backend/src/main/java/vn/tsolve/workspace/controller/WorkspaceController.java`, `service/WorkspaceService.java`, `service/impl/WorkspaceServiceImpl.java`, `dto/WorkspaceUpdateRequest.java`
- Test: `WorkspaceServiceImplTest.java`, `WorkspaceControllerIT.java` (edges 9, 12, 13, 14, 20)
**Dependencies:** Task 4
**Estimated size:** small

---

## Task 6: Service account for tracker, remove the stand-in

**Story:** STORY-004
**Feature:** workspace-service-account
**Description:** `WorkspaceJiraServiceAccount implements JiraServiceAccount`: reads the row in the bound tenant,
decrypts; empty when no row; decrypt failure → error log without value, empty. Delete `ConfiguredJiraServiceAccount`,
its test and the `tsolve.tracker.jira.site-url / service-account-email / service-account-token` keys (`application.yml`,
`.env.example`; leave the developer's local `.env` alone). Fix any test that relied on the stand-in.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/workspace/service/impl/WorkspaceJiraServiceAccount.java`
- Delete: `backend/src/main/java/vn/tsolve/tracker/service/impl/ConfiguredJiraServiceAccount.java`, `backend/src/test/java/vn/tsolve/tracker/service/impl/ConfiguredJiraServiceAccountTest.java`
- Modify: `backend/src/main/java/vn/tsolve/tracker/service/JiraServiceAccount.java` (Javadoc), `backend/src/main/resources/application.yml`, `.env.example`
- Test: `backend/src/test/java/vn/tsolve/workspace/service/impl/WorkspaceJiraServiceAccountTest.java` (edges 15, 16)
**Dependencies:** Task 1
**Estimated size:** small

---

## Task 7: Screen 2.1 Workspace Setup

**Story:** STORY-005
**Feature:** workspace-setup-screen
**Description:** Replace the placeholder page with the form (AC-005.x) using the shared form/message helpers like
`features/users` `CreateUserForm`; hooks over the generated client; on 201 → toast MSG05, invalidate/refetch `/me`, go to
the User List; on 409 → refetch `/me`, go to 2.2. Add `MSG61`, `MSG153`, `MSG156` to the message catalog and refresh
`report3-messages.json` to v3460 (update the "no Retired code" test). MSW handlers for the three endpoints.
**Files:**
- Modify: `frontend/src/features/workspace/pages/WorkspaceSetupPage.tsx`, `frontend/src/features/workspace/mocks/handlers.ts`
- Create: `frontend/src/features/workspace/components/WorkspaceSetupForm.tsx`, `frontend/src/features/workspace/hooks/useWorkspace.ts`
- Modify: `frontend/src/shared/messages/catalog.ts`, `report3-messages.json`, `catalog.test.ts`
- Test: `frontend/src/features/workspace/pages/WorkspaceSetupPage.test.tsx` (edges 17, 18)
**Dependencies:** Task 4
**Estimated size:** medium

---

## Task 8: Screen 2.2 Workspace Settings, company information

**Story:** STORY-006
**Feature:** workspace-settings-screen
**Description:** 2.2 page with the company information section (AC-006.x); the Departments area stays a placeholder
for KAN-120. Token field empty with "leave empty to keep the current token"; PUT omits the token when empty; cancel
restores loaded values; shared error state with retry.
**Files:**
- Modify: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx`, `frontend/src/features/workspace/hooks/useWorkspace.ts`, `frontend/src/features/workspace/mocks/handlers.ts`
- Create: `frontend/src/features/workspace/components/WorkspaceInfoForm.tsx`
- Test: `frontend/src/features/workspace/pages/WorkspaceSettingsPage.test.tsx` (edge 19)
**Dependencies:** Task 5, Task 7
**Estimated size:** medium
