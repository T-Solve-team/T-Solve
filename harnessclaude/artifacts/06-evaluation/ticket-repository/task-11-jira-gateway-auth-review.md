---
module: ticket-repository
feature: jira-gateway-auth
task: 11
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 11: Jira client — scoped token through the API gateway, and 429 / 503 retry

## Summary
Commit `423b8f17`. `JiraRestIssueSource` now calls `https://api.atlassian.com/ex/jira/{JIRA_CLOUD_ID}` with
`Authorization: Bearer {JIRA_API_TOKEN}` and builds links from `JIRA_SITE_URL`. The site must be https; a trailing
slash is trimmed. Any empty setting means "not configured", which answers MSG50. No `JIRA_EMAIL` remains in code or
`.env.example`. Retries follow AC-001.11:
- 429, or 503 with `Retry-After` of at most 5 s: wait that long and resend, at most 2 times.
- 429 without `Retry-After`: wait 1 s.
- `Retry-After` above 5 s, or a 503 without it: MSG50 at once.

The sleep goes through an injected `Sleeper`. AC-001.7, AC-001.11 and AC-001.12 are met.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.12: gateway base URL + Bearer header (`JiraRestIssueSource.java:61-72`); link `siteUrl + "/browse/" + key` (`:140`). AC-001.11: `call()` retries per request, so search and each comment page retry independently (`:204-235`). `retrySeconds` (`:237-252`): 429/503 only; a missing header on 429 counts as 1, on 503 as -1 (no retry); a non-numeric value counts as -1. AC-001.7: still applies after the retries, with status-only reasons and no cause chained. |
| Completeness | pass | `.env.example` lists `JIRA_SITE_URL`, `JIRA_CLOUD_ID` (with the `_edge/tenant_info` hint) and `JIRA_API_TOKEN`, all empty (DoD item). |
| Correctness | pass | `JiraRestIssueSourceTest` 20/20, including exactly 3 requests on repeated 429s, waits recorded, no token in the message or the captured log. |
| Integration | pass | The `JiraIssueSource` port is unchanged; `JiraImportServiceImpl` needed no change. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-001` → `12/12 acceptance criteria have a test`.
- ✓ AC-001.7 `JiraRestIssueSourceTest` (also `JiraImportServiceImplTest`, `JiraImportControllerTest`)
- ✓ AC-001.11 `JiraRestIssueSourceTest`
- ✓ AC-001.12 `JiraRestIssueSourceTest`

## Issues
No blocking issues.

Informational (Low, also in `security-review.md`):
1. `JiraRestIssueSource.java:66`. `cloudId.strip()` is concatenated into the base URL unchecked. A value holding
   `/`, `?` or `#` would change the request path on the fixed host `api.atlassian.com`. The value comes from the
   operator's environment, so this is low risk. Fix: accept only a UUID (`[0-9a-fA-F-]{36}`) and treat anything else
   as "not configured".
2. `JiraRestIssueSource.java:67`. `apiToken` is not stripped, although the other two settings are. A trailing space
   or CR from a hand-edited `.env` sends a broken header: Jira answers 401 (MSG50), or the HTTP client rejects a
   CR/LF header with an `IllegalArgumentException` that escapes as a 500. Fix: `apiToken.strip()`.
3. `JiraRestIssueSource.java:247-251`. A `Retry-After` given as an HTTP date is treated as "do not retry". The spec
   counts seconds, so this is accepted.

Assumptions made:
- The worst-case added wait of 2 × 5 s per call can repeat on every page and comment page. NFR 1 is judged in the
  Task 4 review (informational).

## Test Report
See `task-11-jira-gateway-auth-test-report.md`. 632 unit + 48 IT, 0 failures.
