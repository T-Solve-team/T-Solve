# Tasks: workspace — Jira access as the T-Solve Jira App (KAN-349)

Spec: artifacts/03-modules/workspace/spec.md · Design: artifacts/04-plans/workspace/design.md, "Change 2026-11-17"
Branch: `feat/KAN-349-jira-app-identity` (on KAN-345). Jira App part: jira-app Task (app-token-trigger).

---

## Task 12: Receive and hold the app token

**Story:** STORY-012, STORY-008
**Feature:** jira-app-token
**Description:** `workspace/jiraapp/JiraAppTokenStore` (Redis, encrypted, TTL to `exp`, later `exp` wins, undecryptable →
delete + empty), `JiraAppTokenService` + `JiraAppTokenController` (`POST /api/v1/jira-app/app-token`, `@PreAuthorize`
JIRA_APP, header `x-forge-oauth-system`, `apiBaseUrl` = gateway + canonical cloud id, workspace from
`JiraWorkspaceLookup`, 204/400 MSG06/403 MSG08). `WorkspaceJiraServiceAccount.current()` = site from the row + cloud id
and token from the store; empty when none or when the cloud id differs from the row's (AC-008.1, AC-008.4).
**Files:** Create `workspace/jiraapp/*`, controller; modify `WorkspaceJiraServiceAccount`; tests `JiraAppTokenStoreTest`,
`JiraAppTokenServiceImplTest`, `JiraAppTokenControllerTest`, `WorkspaceJiraServiceAccountTest`.
**Estimated size:** medium

## Task 13: Setup and Settings without a service account

**Story:** STORY-005, STORY-006, STORY-007, STORY-011
**Feature:** workspace-without-service-account
**Description:** Setup takes `companyName` + `jiraSiteUrl`, reads the cloud id, links and stores, no Jira check; Update
takes `companyName` only; both ignore `serviceAccountEmail`/`serviceAccountToken`; audits without token fields;
`WorkspaceInfo` gains `jiraAppConnected`, `jiraAppTokenReceivedAt` (AC-006.6). Changeset 043 clears the old credential
columns (AC-011.9). Remove `TicketTrackerClient.checkServiceAccount`. Contract first.
**Files:** `WorkspaceServiceImpl`, DTOs, entity, changeset, `frontend/openapi/tsolve-api.yaml`, tracker client; tests
`WorkspaceServiceImplTest`, `WorkspaceControllerTest`, `WorkspaceOpenApiTest`, `WorkspaceControllerIT`,
`WorkspaceInfoRepositoryIT`.
**Dependencies:** Task 12
**Estimated size:** medium

## Task 14: Screens 2.1 and 2.2

**Story:** STORY-009, STORY-010
**Feature:** workspace-jira-app-status-screen
**Description:** Generated client; 2.1 without the token field (AC-009.2, AC-009.4); 2.2 without the token field, with
the read-only "Jira App" line (AC-010.1, AC-010.3, AC-010.9); MSW mocks and tests.
**Dependencies:** Task 13
**Estimated size:** small
