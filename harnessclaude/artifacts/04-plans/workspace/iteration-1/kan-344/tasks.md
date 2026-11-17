# Tasks: workspace — Jira service account through the gateway (KAN-344)

Spec: artifacts/03-modules/workspace/spec.md · Design: artifacts/04-plans/workspace/design.md, "Change 2026-11-16"
Branch: `feat/KAN-344-workspace-jira-bearer` (on top of `feat/KAN-119-workspace-token-hint`). Tracker part: tracker
Task 20, same PR.

---

## Task 10: Cloud id and Bearer check in the backend, no email

**Story:** STORY-005, STORY-007, STORY-008, STORY-011
**Feature:** jira-cloud-id
**Description:** Changeset `jira_cloud_id VARCHAR(36)` on `workspace`. `WorkspaceInfo` gains `jiraCloudId`, stops writing
the email. `JiraSiteLinker.readCloudId(site)` (interim adapter: the `/_edge/tenant_info` read, canonical UUID only,
AC-011.8). Setup: fields `companyName`, `jiraSiteUrl`, `serviceAccountToken` (AC-005.1, AC-005.3), read the cloud id
(AC-005.14 → MSG61), check with `JiraAccount(site, cloudId, token)` (AC-005.6), store, audit without email
(AC-005.12). Update: `companyName` + optional token, email ignored (AC-007.1, AC-007.3, AC-007.7); a new token is checked
with the stored cloud id (AC-007.4), read first when the row has none (AC-007.11 → MSG156); audit without email
(AC-007.8). `WorkspaceJiraServiceAccount` returns site, cloud id, token; empty without a cloud id (AC-008.1, AC-008.4).
Contract first: `frontend/openapi/tsolve-api.yaml` drops `serviceAccountEmail` from the three schemas (AC-006.2).
**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/NNN-workspace-jira-cloud-id.sql` (+ master include)
- Modify: `workspace/entity/WorkspaceInfo.java`, `workspace/service/impl/WorkspaceServiceImpl.java`,
  `workspace/service/impl/WorkspaceJiraServiceAccount.java`, `workspace/site/JiraSiteLinker.java`,
  `workspace/site/InterimJiraSiteLinker.java`, `workspace/dto/*` (setup/update request, info response, mapper),
  `frontend/openapi/tsolve-api.yaml`
- Test: `WorkspaceServiceImplTest`, `WorkspaceJiraServiceAccountTest`, `InterimJiraSiteLinkerTest`,
  `WorkspaceControllerIT` (AC-005.1, AC-005.14, AC-007.11, AC-008.4)
**Dependencies:** tracker Task 20 (`JiraAccount` shape) in the same commit series
**Estimated size:** medium

---

## Task 11: Setup and Settings forms without the email field

**Story:** STORY-009, STORY-010
**Feature:** workspace-form-no-email
**Description:** `npm run generate:api` after Task 10's contract. 2.1 Setup has Company name, Jira site address and the
API token with the scoped-token hint, no email (AC-009.2); field errors without MSG03 (AC-009.4). 2.2 Settings shows the
company name and the read-only site, no email (AC-010.1). MSW handlers and fixtures drop the email.
**Files:**
- Modify: `frontend/src/shared/api/generated/**` (generated), `frontend/src/features/workspace/components/WorkspaceInfoForm.tsx`,
  `frontend/src/features/workspace/pages/WorkspaceSetupPage.tsx`, `frontend/src/features/workspace/mocks/handlers.ts`
- Test: `WorkspaceSetupPage.test.tsx` (AC-009.2, AC-009.4), `WorkspaceSettingsPage.test.tsx` (AC-010.1)
**Dependencies:** Task 10
**Estimated size:** small

---

## Task 12: MSG156 reason and WARN log in the backend

**Story:** STORY-005, STORY-007, STORY-011
**Feature:** jira-check-reason
**Description:** Branch `feat/KAN-344-workspace-jira-check-reason`. `JiraClient#checkServiceAccount` throws
`JiraPermissionMissingException` (a `JiraRejectedException` with status 403) naming the keys whose `havePermission` is
not true, in the order `BROWSE_PROJECTS`, `CREATE_ISSUES`, `MODIFY_REPORTER` (AC-011.1). `WorkspaceServiceImpl` maps a
failed check to `MSG156` params `tracker_name`, `reason` and, for `MISSING_PERMISSIONS`, `missing_permissions`
(AC-011.9: permission missing → `MISSING_PERMISSIONS`; other `JiraRejectedException` → `TOKEN_REJECTED`;
`JiraUnavailableException`, 5xx, or an unreadable cloud id on Update → `JIRA_UNREACHABLE`) and logs one WARN line with
step, reason, status and missing keys (AC-011.10). An undecryptable stored token keeps `MSG156` without a reason
(AC-007.5).
**Files:**
- Create: `backend/src/main/java/vn/tsolve/tracker/exception/JiraPermissionMissingException.java`
- Modify: `tracker/client/JiraClient.java`, `tracker/client/TicketTrackerClient.java` (javadoc),
  `workspace/service/impl/WorkspaceServiceImpl.java`
- Test: `JiraClientServiceAccountCheckTest` (or the existing check test), `WorkspaceServiceImplTest`
**Dependencies:** Task 10
**Estimated size:** small

---

## Task 13: Reason line under the token field

**Story:** STORY-009, STORY-010
**Feature:** jira-check-reason-screen
**Description:** A workspace helper turns the `MSG156` params into the line of AC-009.8; 2.1 Setup and 2.2 Settings
show it under the token field below the `MSG156` text (AC-009.8, AC-010.9). Unknown or absent reason → no line.
**Files:**
- Create: `frontend/src/features/workspace/components/serviceAccountReason.ts` (+ test)
- Modify: `frontend/src/features/workspace/components/WorkspaceInfoForm.tsx`,
  `frontend/src/features/workspace/components/WorkspaceSetupForm.tsx`
- Test: `WorkspaceSetupPage.test.tsx` (AC-009.8), `WorkspaceSettingsCompanyInfo.test.tsx` (AC-010.9)
**Dependencies:** Task 12 (params shape)
**Estimated size:** small
