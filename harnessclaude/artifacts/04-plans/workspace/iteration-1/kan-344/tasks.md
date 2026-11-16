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
