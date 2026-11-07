# Session Log: Task 10 — Trace and end-to-end check

**Module:** ticket-repository
**Assignee:** Đặng Nhật Minh
**Iteration:** 1
**Status:** evaluating

## Session 2026-11-08
### Working on: jira-import-verify
Run on `feat/KAN-45-ticket-repository-jira-import` at `9a701fc6` (main merged in at `e7c6f050`). No code or test
changed: `trace` found no gap to fill.

- `bun harnessclaude/hooks/sdd.ts trace ticket-repository`: 40/40 acceptance criteria have a test.
- Backend, `mvn -B -Dapi.version=1.44 -Dspotless.check.skip=true verify` (IntelliJ jbr, Docker 29.1.3): 981 unit
  tests and 220 integration tests, 0 failures, 0 errors, BUILD SUCCESS. A first run with Docker Desktop stopped
  gave 49 integration-test errors (no Docker API); the rerun with Docker running passed.
- Spotless: it cannot run in this worktree (`.git` is a file, `ratchetFrom origin/main` needs a repository). On a
  fresh `git clone` of HEAD with `origin/main` fetched, `mvn spotless:check` exits 0.
- Frontend (Node 24.22.2): `format:check`, `lint` (0 errors), `typecheck` and `build` pass; `test` 83 files,
  1480/1480; `generate:api` leaves no diff in `src/shared/api/generated`.

### Not done: manual run against a real Jira site
Task 10 asks for one run against a real Jira site with a service account scoped token (pull a small range, write a
Solution, Finish, a Draft round trip). It was **not done**: no `.env` with `JIRA_SITE_URL`, `JIRA_CLOUD_ID` and
`JIRA_API_TOKEN` exists on this machine, and the user chose on 2026-11-08 to skip it. The Jira client
(`JiraRestIssueSource`, gateway URL, scoped token, 429/503 retry) is covered only by tests against a mock server.
Do it before the module is completed or before KAN-45 is demoed.

### Done later the same day: manual run against the real Jira site
Run on `d6e9b5e9` against `https://t-solve.atlassian.net` (cloud id `b6045441-…`), Jira project `KAN`, through the
REST API (`curl` with the XSRF cookie and header, as the SPA sends them). The web screens were not driven.

Setup (local only, nothing committed): backend `mvn spring-boot:run` with profile `identity-mock`
(`TSOLVE_IDENTITY_MOCK_ACCOUNT_EMAIL=manager@acme.test`, a Project Manager), its own PostgreSQL and Redis containers
(`kan45-db` :5433, `kan45-redis` :6380), `TSOLVE_STUB_PROJECTS=<manager's app_user id>:<stub project>` and
`TSOLVE_STUB_JIRA_KEYS=<stub project>:KAN`. `.env` needs `OLLAMA_BASE_URL` and `OLLAMA_CHAT_MODEL` set (any value)
or the backend does not start.

Credential findings:
- A **personal** Atlassian API token (classic, `ATATT…`) sent as Bearer through the gateway gets **401**; the same
  token works only as Basic `email:token`. The pull then answers 502 MSG50 "Jira answered HTTP 401" and records a
  Failed Import Batch, as AC-001.7 says. RESEARCH-003's "Basic also accepted / Bearer works" for user tokens does
  not hold for this token: only a service account's scoped token (`ATSTT…`) works with the code as built (D2).
- The service account must belong to the organization that owns the site. One created in another organization
  authenticates but sees no project (`/project/search` total 0, `/project/KAN` 404).
- It also needs Jira app access on the site and a role in the space (Space settings → Access).
- With `read:jira-work` only, `/rest/api/3/myself` answers 401 "scope does not match"; the pull does not need it.

Results with the service account's scoped token:
| Step | Result |
|---|---|
| Pull 2026-11-01..03 | 200 in 0.96 s; 4 rows (KAN-237, KAN-238, KAN-74, KAN-160), oldest first, all Needs Solution; description as text, writer = assignee, link `https://t-solve.atlassian.net/browse/<key>` |
| Fill solution rows 1–2 | 200; 2 Valid, 2 Needs Solution |
| Save as draft | 200 MSG41; listed in `GET /imports/drafts` |
| Pull 2026-11-04..05 while the Draft exists | 200 (KAN-231, KAN-235); the Draft is kept |
| Reopen the Draft (`GET /previews/1`) | Solutions of rows 1–2 kept, `draftSavedAt` set |
| Finish with Needs Solution rows | 409 `MSG_NEW_4` count 2, nothing imported |
| Remove row 4, fill row 3, Finish | 200 MSG32 created 3; Import Batch COMPLETED published 3 removed 1; 3 Published tickets of source JIRA_IMPORT; 3 `ticket_import_decision` rows; the Draft is gone |
| Pull 2026-11-01..03 again | only KAN-160 (the removed row comes back, AC-002.7) |
| Pull 2026-01-01..11-08 (NFR 1) | 200 in **1.08 s** for 95 issues with comments (NFR 1: 100 issues within 60 s) |
| Row KAN-107 | 6 comments with author, time and text |

Not covered by this run: MSG35 (the project has fewer than 100 resolved issues without a Ticket), the 429/503 retry,
and the web screens.

### Open judgment calls (from the task 7 and task 9 reviews)
- MSG35 still shows a literal `{count}`: spec D8 drops the count, the catalog waits for the Report3 change.
- Edits on a reopened Draft (Fill solution, Remove) are saved at once, so Cancel and MSG37 cannot undo them
  (AC-002.11, AC-004.3).

### Next up
Evaluate `jira-import-verify`; then the two judgment calls before completing the module. Write the token scopes and
"service account of the site's organization" in `.env.example` (quick change).
