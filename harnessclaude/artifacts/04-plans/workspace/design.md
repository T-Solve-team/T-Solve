---
module: workspace
date: 2026-11-12
---

# Design: workspace (KAN-119: UC-10, UC-11)

Spec: `artifacts/03-modules/workspace/spec.md`

## Architecture Overview

```
WorkspaceController (/api/v1/workspace, @PreAuthorize ADMIN)
   └─ WorkspaceService (impl: WorkspaceServiceImpl, @Transactional)
        ├─ JiraSiteRule            static: validate + normalise the site (AC-009.3)
        ├─ TicketTrackerClient     tracker: checkServiceAccount(JiraAccount) (AC-009.1/2)
        ├─ JiraSiteLinker (port)   → InterimJiraSiteLinker (reads /_edge/tenant_info) [Interim]
        ├─ TokenCipher             AES-256-GCM, key WORKSPACE_SECRET_KEY, AAD = workspace id
        ├─ WorkspaceInfoRepository JPA, table `workspace` (one row)
        ├─ TenantScope             identity: currentWorkspaceId()
        └─ AuditService            TRACKER_CONFIGURATION_CHANGE / WORKSPACE
WorkspaceStatusLookupImpl  → common/lookup/WorkspaceStatusLookup (for /me)
WorkspaceJiraServiceAccount → tracker/service/JiraServiceAccount (replaces ConfiguredJiraServiceAccount)
```

Setup order (AC-003.2..11): field checks → `JiraSiteRule` (MSG61) → row exists? (409) → `checkServiceAccount`
(MSG156) → in one transaction: `linkJiraSite` (MSG153 / MSG61) → insert row → audit → commit. A unique-key violation
on the singleton constraint at insert (concurrent Setup) is mapped to 409.

## Data Model

Changeset `029-workspace-info.sql` (author `workspace`):
```sql
ALTER TABLE workspace RENAME TO legacy_workspace;          -- changeset 001 table; FKs follow the rename
CREATE TABLE workspace (
    workspace_id              UUID         CONSTRAINT pk_workspace_info PRIMARY KEY,
    company_name              VARCHAR(150) NOT NULL,
    sign_in_provider          VARCHAR(30)  NOT NULL,
    jira_site_address         VARCHAR(255),
    service_account_email     VARCHAR(254),
    service_account_api_token TEXT,
    token_expiry_date         DATE,
    singleton                 BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_workspace_singleton UNIQUE (singleton),
    CONSTRAINT ck_workspace_singleton CHECK (singleton)
);
-- rollback: DROP TABLE workspace; ALTER TABLE legacy_workspace RENAME TO workspace;
```
Also rename the legacy indexes/sequence only if a name clash appears (`workspace_pkey`, `workspace_id_seq`): the new
table's PK constraint is named `pk_workspace_info` explicitly to avoid the clash with the legacy `workspace_pkey`.

Token format in `service_account_api_token`: `v1:` + Base64(nonce[12] ‖ ciphertext‖tag[16]).

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Catalog link (Q1, user) | Port `JiraSiteLinker` + interim adapter | wait for platform Task 18; build the catalog slice here | Unblocks KAN-119; swapping to platform is one bean |
| Token key (Q2, user) | `WORKSPACE_SECRET_KEY`, AES-256-GCM via `javax.crypto` | reuse `CATALOG_SECRET_KEY`; Jasypt/pgcrypto | Keeps the secrets apart, no dependency, ~30 lines |
| Permission check (Q3, user) | `myself` + `mypermissions` (3 permissions, no project) at UC-10 and UC-11 | Browse only; per Project | Meets the Jira AC of KAN-119; UC-13 adds the per-Project check |
| Where the Jira call lives (Q3b, user) | `TicketTrackerClient.checkServiceAccount` in `tracker` | own client in `workspace` | One Jira client; error split already there |
| Company information (Q4, user) | Company name only | + token expiry; + address/logo | SRS 2.1/2.2 |
| Legacy table (Q5, user) | Rename to `legacy_workspace` | new name `workspace_info`; drop | Matches `02_tenant.sql`; FKs survive the rename |
| Clearing the service account (Q6, user) | Required; blank token keeps the stored one | allow clearing | Screen 2.2 marks it required |
| `workspaceReady` (Q7, user) | `WorkspaceStatusLookupImpl` | frontend calls `GET /workspace` | Existing be-foundation contract |
| Second Setup (Q8, user) | 409, web app goes to 2.2 | idempotent replay | Simple; the site can never be overwritten |

## Decisions made without the user (KAN-119)

The user delegated the remaining calls on 12/11 ("tự quyết và note lại"). Each is safe to revisit.

| # | Question | Chosen | Alternatives | Rationale |
|---|----------|--------|--------------|-----------|
| D1 | Message for an invalid site address | `MSG61` (System_Messages v3460) | platform's `MSG156` (AC-004.21) | v3460 gives `MSG156` to the service account; change request to platform listed in the spec |
| D2 | Frontend catalog marks `MSG61` Retired and lacks `MSG153`/`MSG156` | Add them as v3460 gives them; drop `MSG61` from the Retired test | keep the old catalog | `report3-messages.json` predates v3460; `frontend/src/shared/messages/**` added to this module's paths |
| D3 | Message for "already set up" | 409 with technical code `WORKSPACE_ALREADY_SET_UP`, no user text | invent `MSG158` | No such message in v3460; the web app redirects to 2.2, the user never sees an error |
| D4 | Over-length fields | `MSG02` with `max_length` | `MSG01` | `MSG02` is the baseline length message |
| D5 | Email format | `MSG03` | none | Baseline email message |
| D6 | SSRF rule | https, no port/user info/query/fragment/path, host `<one label>.atlassian.net`, no redirects in the tenant-info read | allow any https host; allowlist by DNS | Jira Cloud sites are `<name>.atlassian.net` (SRS example); blocks internal addresses |
| D7 | Tenant info unreadable | `MSG61` on `jiraSiteUrl` | 502 / `MSG06` | A site that does not answer is, to the Admin, not a valid site |
| D8 | Field of `MSG156` | `serviceAccountToken` | `serviceAccountEmail` | The token is what is usually wrong |
| D9 | Which failures are `MSG156` | 401, 403, any error status, timeout, unreachable, missing permission | Jira down → `MSG06` | SRS: "could not connect … or lacks the required permissions" covers both |
| D10 | UC-11 Jira call when only the name changes | No Jira call | always re-check | A Jira outage must not block renaming the company |
| D11 | Audit action | `TRACKER_CONFIGURATION_CHANGE`, target type `WORKSPACE` | add `WORKSPACE_UPDATE` to `AuditAction` | The action list is fixed by the dashboard-audit spec and the OpenAPI enum |
| D12 | `sign_in_provider` value | `GOOGLE` | null / config | NOT NULL in the DDL; sign-in is Keycloak with Google (ADR-0003) |
| D13 | Link and insert in one transaction | Link first, then insert, same transaction | insert then link after commit | A refused link stores no row; the left-over risk (commit fails after the link) is a platform change request (AC-004.23 idempotent) |
| D14 | `WorkspaceStatusLookup` interface does not exist yet | Create it in `common/lookup` with `boolean isReady()` (be-foundation design.md:182 names this file) | wait for be-foundation | Lets `/me` wire it later; path added to `module.md` |
| D15 | Stand-in `ConfiguredJiraServiceAccount` | Delete it, its test and its 3 settings (`application.yml`, `.env.example`) | keep under a profile | Its own Javadoc says to delete it; one bean only (AC-006.2) |
| D16 | Decrypt failure | Log error, return empty | throw | No Jira call with a broken token; tracker already handles "no account" (AC-012.8) |
| D17 | Request `jiraSiteUrl` on PUT | Ignored | 400 | Read-only field; a client that echoes the form must not fail |
| D18 | Missing Jira timeouts | Reuse `tsolve.tracker.jira.connect-timeout` 5 s / `read-timeout` 10 s; interim linker uses the same values | new settings | NFR 3, no new knobs |
| D19 | After Setup, landing page | User List (`paths.users`) | Workspace Settings | SRS UC-10 step 7 |
| D20 | Spec depth / risk review | Deep; risk-review done inline below | five-persona subagent | Planner agent was stopped by the user; findings folded into the edge cases |
| D21 | "Token differs" on UC-11 (builder check) | Any non-blank token counts as changed: it is checked with Jira and `tokenChanged` = true; the stored token is never decrypted to compare | decrypt and compare | Simpler; never handles the old secret in clear for a comparison |
| D23 | `JIRA_SITE_URL` after removing the stand-in (Task 6) | Keep it in `.env.example` under the Jira import section; only the stand-in's email and token variables go | delete it everywhere | `ticket/client/JiraRestIssueSource` (ticket-repository) still reads it for browse links |
| D24 | No side menu on 2.1 (AC-007.1, Task 7) | Spec change: "with no menu" removed from AC-007.1; change request to `fe-foundation` (`AppShell`) | edit `AppShell` here | `AppShell` is `fe-foundation` code, locked while its spec is changed since approval |
| D25 | Detecting the 409 on 2.1 (Task 7) | Status 409 with no field error on `setupWorkspace` (its only non-field conflict) | read the technical code | The shared transport normalises unknown codes to `MSG06` and drops `WORKSPACE_ALREADY_SET_UP` |
| D26 | fe-foundation's route-table test still expects the 2.1 placeholder (Task 7) | Remove the 2.1 row, as its comment asks the building module to do; path added to `module.md` | leave the test failing | Same convention chatbot (12.1) and triage (10.1) followed |
| D27 | Developer for Task 8 after Codex hit its usage limit (until 14/11) | Claude `harnessclaude-developer` subagent with the same brief and rules | wait for Codex | Keeps KAN-119 moving; the Reviewer stays independent of the writer |
| D28 | Which Workspace `JiraServiceAccount.current()` reads in a worker job (evaluation, Task 6 CRITICAL) | `WorkerTenantService.currentWorkspaceId()` when present (optional bean), else `TenantScope`; AC-006.1 reworded | make `worker`'s `runInTenant` also bind `TenantScope` | `worker` is `completed`; the fix stays inside this module; a later platform tenant binding can replace both |
| D29 | Email-only update while the stored token cannot be decrypted (evaluation, Task 5) | 400 `MSG156` on `serviceAccountToken`, workspace id logged only; AC-005.5 extended | generic 500 | The Admin must re-enter the token after a key loss; MSG156 tells them which field |
| D30 | Jira check inside the `setup` transaction (evaluation, Task 4, backend/AGENTS.md §4) | Check before the transaction; link, persist and audit in a `TransactionTemplate`, like `update` | keep as is | No remote call while a DB transaction is open |
| D22 | Where the interim linker puts the `cloudId` (builder check) | Returned to the service and only logged (host, not secrets); nothing persists it until the catalog exists | add a `jira_cloud_id` column to the tenant table | The catalog owns `jira_cloud_id` (platform AC-001.15); a tenant column would duplicate it |

## File Structure
```
backend/src/main/java/vn/tsolve/workspace/
  controller/WorkspaceController.java
  dto/WorkspaceSetupRequest.java  WorkspaceUpdateRequest.java  WorkspaceInfoResponse.java
  entity/WorkspaceInfo.java                    (table workspace)
  repository/WorkspaceInfoRepository.java
  service/WorkspaceService.java  service/impl/WorkspaceServiceImpl.java
  service/impl/WorkspaceStatusLookupImpl.java  WorkspaceJiraServiceAccount.java
  site/JiraSiteRule.java  JiraSiteLinker.java  InterimJiraSiteLinker.java  JiraSiteLinkRefusedException.java
  crypto/TokenCipher.java
backend/src/main/java/vn/tsolve/common/lookup/WorkspaceStatusLookup.java
backend/src/main/java/vn/tsolve/tracker/client/TicketTrackerClient.java, JiraClient.java   (+ checkServiceAccount)
backend/src/main/resources/db/changelog/changes/029-workspace-info.sql (+ master include)
backend/src/main/resources/application.yml  (tsolve.workspace.secret-key: ${WORKSPACE_SECRET_KEY}; remove stand-in keys)
backend/src/test/resources/application*.yml (test key)
.env.example (WORKSPACE_SECRET_KEY)
frontend/openapi/tsolve-api.yaml  → npm run generate:api
frontend/src/features/workspace/{api,hooks,components,pages,mocks}/…
frontend/src/shared/messages/{catalog.ts,report3-messages.json,catalog.test.ts}
```

## External Dependencies
- None new. `javax.crypto` (JDK), Spring `RestClient`, existing Jira REST API v3 endpoints `myself`, `mypermissions`,
  Atlassian `/_edge/tenant_info`.

## Risk review (inline, Deep spec)

Verdict: **CAUTION** — go, with the mitigations below folded into the tasks.
- Secret leak (token in logs/toString/audit/errors) → AC-009.7 tests per type; `JiraAccount.toString` already hides it.
- SSRF via site address → AC-009.3 rule before any call; no redirects in the interim linker; tests with
  `http://`, `https://localhost`, `https://169.254.169.254`, `https://a.atlassian.net.evil.com`, `https://x@a.atlassian.net`,
  `https://a.atlassian.net:8443`.
- Key loss = all tokens undecryptable → documented in `.env.example`; decrypt failure returns empty (D16); the Admin
  re-enters the token in 2.2.
- Cross-tenant copy of ciphertext → AAD = workspace id (AC-009.5).
- Half-done Setup (catalog linked, tenant commit failed) → D13 + platform change request.
- MSG153 untestable end-to-end until the catalog exists → [Interim] in spec; unit test with a refusing double.

## Edge Cases & Risks

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-003 | Concurrency | Two Admins submit Setup at once | High | One row; the other gets 409 (unique singleton) | Task 4 |
| 2 | STORY-003 | Security | Site `https://169.254.169.254` / `localhost` / evil suffix | Critical | MSG61, no outbound call | Task 3 |
| 3 | STORY-003 | Input | Site with trailing `/`, upper case, spaces | Medium | Normalised to `https://<host>` | Task 3 |
| 4 | STORY-003 | External | Jira 401 / 403 / 500 / timeout | High | MSG156, nothing stored, no body logged | Task 2, Task 4 |
| 5 | STORY-003 | External | Account authenticates but lacks MODIFY_REPORTER | High | MSG156 | Task 2 |
| 6 | STORY-003 | External | tenant_info 404 or no cloudId / redirect | High | MSG61, nothing stored | Task 3 |
| 7 | STORY-003 | State | Linker refuses (another Workspace) | High | MSG153, no row | Task 4 |
| 8 | STORY-003 | State | Setup when already set up | High | 409, site unchanged | Task 4 |
| 9 | STORY-009 | Security | Token in logs / audit / response | Critical | Never present | Task 1, Task 4, Task 5 |
| 10 | STORY-009 | Security | Ciphertext copied to another Workspace id | High | Decrypt fails | Task 1 |
| 11 | STORY-009 | Config | Key missing / wrong length | High | Startup fails, names setting only | Task 1 |
| 12 | STORY-005 | Input | Blank token on update | High | Stored token kept; check uses it if the email changed | Task 5 |
| 13 | STORY-005 | External | Only company name changed while Jira is down | Medium | Saved, no Jira call | Task 5 |
| 14 | STORY-005 | Input | Body carries `jiraSiteUrl` | Medium | Ignored | Task 5 |
| 15 | STORY-006 | State | Not set up | High | `current()` empty | Task 6 |
| 16 | STORY-006 | Security | Tampered ciphertext | High | Error log without value, empty | Task 6 |
| 17 | STORY-007 | UX | Double click Save | Medium | One request | Task 7 |
| 18 | STORY-007 | UX | 409 response | Medium | No error, go to 2.2 | Task 7 |
| 19 | STORY-008 | UX | Token never shown, empty means keep | High | Field empty; PUT omits token | Task 8 |
| 20 | STORY-004 | Access | Non-Admin calls any endpoint | High | 403 | Task 4, Task 5 |
