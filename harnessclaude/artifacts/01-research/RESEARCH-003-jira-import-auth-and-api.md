---
id: RESEARCH-003
title: Jira import (UC-20, KAN-45) — credential and REST calls
question: How can the backend call Jira when a Project Manager starts a pull from the web app (outside any Forge invocation), and which Jira Cloud REST calls, limits and edge cases does the pull depend on?
status: complete
date: 2026-11-07
related: [artifacts/03-modules/tracker/spec.md, artifacts/03-modules/project-integration/module.md, artifacts/03-modules/import-ticket-list/spec.md, artifacts/01-research/RESEARCH-001-import-ticket-csv.md]
---

# Jira import (UC-20, KAN-45) — credential and REST calls

## Research Question
1. **Credential:** which credential can the backend use to read Jira issues when the pull starts in the web app
   (a `{site}.tsolve.io.vn` request), where no Forge Invocation Token and no Forge OAuth token are present?
2. **API:** which Jira Cloud REST calls the pull needs (resolved range, at most 100 issues without a Ticket,
   oldest first, comments, description), and which limits and edge cases apply.

## Context
UC-20 (SRS Report3 v3460 §5.1, GBR-15, MSG35/38/50) lets a Project Manager pull resolved Jira issues by date range
and write their Solutions on the Import Preview. Three sources disagree on the credential:

| Source | What it says |
|---|---|
| SRS v3460 UC-14 / Project form | Each Project stores "the project's access token": one secret field, "the API token of a Jira account with Browse projects"; no email field. UC-20 step 3 uses "the Project's token". |
| `tracker` spec (2026-11-01 changelog, constraint 3) | The Forge app system token is used only inside the request that carried it; the tracker never stores it. Import with a Project token is out of scope of `tracker`. |
| KAN-45 worktree, `ticket-repository` spec D2 / design | A Workspace Jira **service account**, for now from env `JIRA_SITE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN`; per-Project token called outdated. |

State of the code (worktree `.claude/worktrees/KAN-45-jira-import`, branch
`feat/KAN-45-ticket-repository-jira-import`, tasks 1–6 committed): `ticket/client/JiraRestIssueSource.java` sends
`Authorization: Basic base64(email:token)` to `JIRA_SITE_URL` + `/rest/api/3/search/jql`. On `main`,
`tracker/client/JiraClient` and `TicketTrackerClient` are empty stubs, and the `project` table has no credential
column. The catalog `tenant_workspace` already holds `jira_site_url` and `jira_cloud_id` (platform design).

Sources: developer.atlassian.com (Forge Remote "Calling Atlassian app APIs from a remote", "Forge Remote
essentials", "Configuring scheduled triggers to invoke a remote backend"; Jira REST v3 Issue search; Jira rate
limiting), support.atlassian.com (API tokens for Atlassian accounts; API tokens and OAuth 2.0 credentials for
service accounts; KB "401 Unauthorized when service account accesses Jira"), Atlassian RFC-61 (enhanced search),
team doc `SEP490_G89/JIRA Connector/Mo ta API cua Jira.docx`. Read on 2026-11-07.

## Findings

### 1. Credential options (facts)

| # | Option | How it authenticates | Who sets it up | Lifetime | Fits T-Solve? |
|---|---|---|---|---|---|
| A | **Classic (unscoped) user API token** | Basic `email:token` against `https://{site}.atlassian.net` | Any Jira user, at id.atlassian.com | 1–365 days (forced since 15 Dec 2024; old tokens expire 14 Mar–12 May 2026) | Works, but needs the email as well as the token, acts with a person's full permissions, and Atlassian marks unscoped tokens as "being deprecated" (no removal date). |
| B | **Scoped user API token** | `Authorization: Bearer <token>` (Basic also accepted) against `https://api.atlassian.com/ex/jira/{cloudId}` | Any Jira user | 1–365 days | Works with **only the token + cloudId** — matches the SRS single "Access token" field. Still tied to a person. |
| C | **Service account + scoped API token** | Same as B: Bearer against `api.atlassian.com/ex/jira/{cloudId}`. Service accounts **can only create scoped tokens**; a scoped token sent to `{site}.atlassian.net` gets **401** (Atlassian KB). | An org admin in admin.atlassian.com (5 service accounts free per org; more need Atlassian Guard) | 1–365 days, scopes fixed at creation | Best fit for "the Workspace's Jira account": not tied to a person, read-only scopes (`read:jira-work`, or granular `read:issue-details:jira` + `read:jql:jira` + `read:comment:jira`), Browse projects on the linked projects. |
| D | **Service account + OAuth 2.0 client credentials (2LO)** | `POST https://auth.atlassian.com/oauth/token` with `grant_type=client_credentials` → Bearer access token, 60 min, against the gateway | Org admin | Client secret has no 365-day token rotation; access token refreshed per hour | Same fit as C, plus a token-exchange step. Worth it only if the yearly token rotation becomes a pain. |
| E | **OAuth 2.0 (3LO) app** | Authorization-code flow, refresh tokens, gateway URL | T-Solve registers an OAuth app; each PM consents | Refresh token | Adds a consent screen, redirect URI and refresh-token storage per user. Overkill for a read-only pull; also subject to the new points-based quota. |
| F | **Forge app system token cached by the backend** | Every Forge Remote call (UI, events, **scheduled trigger**) can carry `x-forge-oauth-system`; TTL ≤ 4 h, ≥ 2 h left when delivered to a remote endpoint. Base URL = FIT claim `app.apiBaseUrl`. An hourly scheduled trigger keeps a valid token at the remote; there is no refresh endpoint. | Nobody beyond installing the Jira App (already required for UC-19) | 2–4 h, rotated by Forge | No customer secret at all, but it **reverses the tracker decision of 2026-11-01** ("the system token is not stored"), needs an hourly trigger per installed site, works only where the Jira App is installed, and reads as the app bot (Atlassian says to never log or share these tokens). |

Facts that settle the existing code:
- `JiraRestIssueSource` (Basic `email:token` on the site URL) works **only with option A**. With a real service
  account (option C, the decision recorded in D2) every pull fails with 401 → MSG50.
- Option B/C/D need the **cloudId** (already in `tenant_workspace.jira_cloud_id`) and send no email. The "open in
  Jira" link (`/browse/{key}`) still needs the site URL (`tenant_workspace.jira_site_url`).
- `GET https://{site}.atlassian.net/_edge/tenant_info` returns the cloudId of a site without authentication — a
  way to fill `jira_cloud_id` when an Admin saves the site address before the Jira App is installed.

### 2. REST calls for the pull (facts)

| Need | Call | Notes |
|---|---|---|
| Resolved issues of a Jira project in a date range, oldest first | `GET` (or `POST`) `/rest/api/3/search/jql` with `jql=project = "KEY" AND resolved >= "yyyy-MM-dd" AND resolved < "yyyy-MM-dd" ORDER BY resolved ASC, key ASC`, `fields=summary,description,assignee,resolutiondate,comment`, `maxResults=100` | Old `/rest/api/3/search` is removed (deprecation ended 30 Apr / 1 May 2025). Pagination is `nextPageToken` only (sequential, token valid 7 days, `isLast` on the last page); no `startAt`, no `total`. Unbounded JQL is rejected with 400 — the `project =` clause bounds it. Results are eventually consistent (`reconcileIssues` exists for read-after-write; not needed for past issues). |
| How many issues match (MSG35 count) | `POST /rest/api/3/search/approximate-count` `{"jql": "…"}` → `{"count": n}` | An estimate, and it counts **all** matches, not "matches without a Ticket". It can give "about n issues in this range" but not the SRS's exact `{count} more`. Spec D8 (MSG35 without a count) stays valid; this endpoint is the option if the team wants an approximate number back. |
| All comments of an issue | Search returns **at most 20 comments** per issue; then `GET /rest/api/3/issue/{key}/comment?startAt=&maxResults=` | Comment pages still use `startAt`/`total`. The worktree design already pages when `comment.total` > returned. |
| Description and comment text | v3 returns Atlassian Document Format (ADF) JSON | v2 (`/rest/api/2/...`, used in the team doc) returns wiki-markup strings instead. Staying on v3 + an ADF-to-text walker (worktree `AdfText`) is right; `expand=renderedFields` would give HTML instead. |
| Writer | `fields.assignee.displayName` (null when unassigned) | `emailAddress` is often hidden by privacy settings; the accountId is the stable key if Jira ↔ T-Solve account linking arrives later. |
| Connection test (UC-14, MSG47/48) | `GET /rest/api/3/project/{key}` | 404 = project not visible to the credential (missing Browse projects or wrong key), 401 = bad / expired token. |

Time zone: JQL date literals are read in the **credential's profile time zone**. A service account's default time
zone may not be Vietnam time, so the worktree's re-check of `resolutiondate` in Vietnam time (design "Date
boundaries") is needed and should stay.

### 3. Rate limits (facts)
- Points-based hourly quotas, enforced from **2 March 2026**, apply to Forge, Connect and OAuth 2.0 (3LO) apps.
  **API-token traffic is not affected** and keeps only the burst (per-second, per-endpoint) limits. Options A–C
  stay on burst limits; E and F fall under the points quota (1 point per call + 1 per issue returned — a 100-issue
  pull costs about 101 points, far below any quota).
- Any limit returns **429** with `Retry-After` (and `RateLimit-Reason`, e.g. `jira-burst-based`, where
  `Retry-After` is always 1). Atlassian's guidance: wait at least `Retry-After`, exponential backoff with jitter,
  cap the retries, retry only idempotent calls; some 503s also carry `Retry-After`.
- One pull is at most 20 search pages + a few comment pages, sequential, so a short bounded retry (for example
  2 retries, honour `Retry-After` up to a few seconds, else MSG50) is enough. The worktree design maps 429 straight
  to MSG50 without a retry.

### 4. Security note (fact)
The team doc `JIRA Connector/Mo ta API cua Jira.docx` contains a real-looking Jira API token and the email it
belongs to inside its cURL examples. A token in a shared document must be treated as leaked.

## Recommendations
1. **Keep D2 (a Workspace-level service account), but fix how it authenticates:** scoped API token, `Authorization:
   Bearer`, base URL `https://api.atlassian.com/ex/jira/{cloudId}`; site URL only for the `/browse/{key}` link.
   In the worktree this changes `JiraRestIssueSource.client(...)` and the env vars to `JIRA_CLOUD_ID` +
   `JIRA_API_TOKEN` (+ `JIRA_SITE_URL` for links), and drops `JIRA_EMAIL`. Optionally accept a classic token
   (Basic on the site URL) behind the same port for local testing with a personal account.
   This is a change to the KAN-45 design, so it goes through the spec/design first (Change Mode), not as a code-only edit.
2. **Store the credential per Workspace, not in env, before a second Workspace exists.** Env vars give every
   Workspace the same Jira site, which breaks database-per-Workspace isolation (ADR-0002, GBR-37). The natural home
   is the Workspace (catalog row next to `jira_site_url` / `jira_cloud_id`, encrypted with `CATALOG_SECRET_KEY`
   like the database password), set by the Admin with the site address (UC-08). That belongs to `workspace` /
   `project-integration`; `JiraIssueSource` keeps its port, so only the credential lookup changes.
3. **Update the SRS:** UC-14's per-Project "Access token" and GBR-14 no longer match the team decision of 01/11.
   Either move the token to the Admin's Jira settings (UC-08, one per Workspace) or, if per-Project tokens are
   kept, specify them as scoped tokens (token only, no email) so the single field works.
4. **Do not adopt option F now.** It removes the customer secret, but it reverses an explicit tracker decision and
   adds an hourly trigger and token storage; revisit it only if asking each customer for a service account proves
   to be a barrier.
5. **Add a bounded 429/503 retry** that honours `Retry-After` in the Jira client, shared later with `tracker`'s
   `JiraClient`.
6. **Revoke the token in `Mo ta API cua Jira.docx`** and replace it with a placeholder.

## Open Questions
- Does the team accept recommendation 1 for KAN-45 now (service account → gateway + Bearer), or ship with a classic
  personal token for the demo and switch later? [NEEDS CLARIFICATION]
- Where does the Workspace credential live — catalog `tenant_workspace` (Admin, UC-08) or each Project (SRS UC-14)?
  Owner: `workspace` / `project-integration` (Đặng Tấn Bình). [NEEDS CLARIFICATION]
- Does a service account need a Jira product licence seat on the team's plan to browse projects? Atlassian says
  service accounts follow the same permission rules as users; licence counting was not verified.
- Should MSG35 show an approximate count from `approximate-count` (counts issues that already have a Ticket too),
  or stay without a count (spec D8)?
- Not verified by a live call in this spike: Bearer with a scoped token on `/rest/api/3/search/jql` against the
  team's Jira site. One cURL run with a fresh service-account token settles it.

## Related Artifacts
- `artifacts/03-modules/tracker/spec.md` — Forge token rule (constraint 3), out-of-scope Project-token import
- `artifacts/03-modules/project-integration/module.md` — Projects, tracker client, Admin's Jira site address
- `artifacts/03-modules/import-ticket-list/spec.md` — CSV import whose preview the Jira import reuses
- `artifacts/01-research/RESEARCH-001-import-ticket-csv.md` — CSV parsing spike (same epic)
- KAN-45 worktree: `harnessclaude/artifacts/03-modules/ticket-repository/spec.md` (D2, D8),
  `harnessclaude/artifacts/04-plans/ticket-repository/design.md`, `backend/.../ticket/client/JiraRestIssueSource.java`
