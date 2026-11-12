---
module: ticket-repository
feature: jira-issue-source
task: 3
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 3: Jira client — JiraIssueSource and JiraRestIssueSource

## Summary
Commit `1b9ea284`, reviewed as it stands at `423b8f17`. Task 11 replaced its Basic auth with the gateway and Bearer
token, as the changed D2 requires; that part is judged in the Task 11 review. The port (`JiraIssueSource`,
`JiraIssue`, `JiraIssuePage`), the search with `nextPageToken`, the extra comment pages, the Vietnam-time re-check,
`AdfText`, and the MSG50 mapping meet AC-001.1, AC-001.2 and the client side of AC-001.7. One untested error path
lets a malformed date escape as a 500 instead of MSG50 (informational).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | JQL `project = "{key}" AND resolved >= "{from}" AND resolved < "{to+1}" ORDER BY resolved ASC, key ASC`, sent as a URI template variable, so it is encoded (`JiraRestIssueSource.java:100-114`). The key is checked against `[A-Z][A-Z0-9_]*`, so it cannot change the query (`:43, :97`). The Vietnam-time re-check keeps both ends inclusive (`:119-123`). The snapshot holds key, summary, ADF→text description, assignee display name or null, resolvedAt, `{site}/browse/{key}` and comments (`:130-142`). When `total` > returned, comments are paged from `/issue/{key}/comment` (`:145-168`). |
| Completeness | pass | AC-001.1 (reading), AC-001.2 (snapshot), AC-001.7 (client failures → `JiraUnavailableException` with status/type only). Design Edges 1, 2, 4, 5 and 16 are covered by tests. |
| Correctness | pass | `JiraRestIssueSourceTest` 20/20, `JiraAdfTextTest` 4/4. A real slow-server test proves the read timeout. The no-token assertions check both the message and the captured log. |
| Integration | pass | `JiraImportServiceImpl` consumes `JiraIssueSource` page by page, and `JiraUnavailableException` maps to 502 MSG50. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-001` → `12/12 acceptance criteria have a test`.
- ✓ AC-001.1 `JiraRestIssueSourceTest` (also `JiraImportServiceImplTest`, `JiraImportControllerTest`)
- ✓ AC-001.2 `JiraRestIssueSourceTest`, `JiraAdfTextTest` (also `JiraImportServiceImplTest`)
- ✓ AC-001.7 `JiraRestIssueSourceTest` (also service and controller tests)

## Issues
No blocking issues.

Informational:
1. `backend/src/main/java/vn/tsolve/ticket/client/JiraRestIssueSource.java:183-191`, called from `:139` and `:177`.
   `time()` falls back to `OffsetDateTime.parse(jiraTime)`, which throws `DateTimeParseException` on a malformed
   `resolutiondate` or comment `created`. Those calls run after `call()` returns: `issue(...)` at `:118` and
   `readComments` at `:148/:161`. The `catch (… DateTimeParseException …)` at `:229` therefore never sees them.
   - Expected (AC-001.7: Jira "returns an error"): MSG50, a Failed Import Batch, no preview.
   - Actual: the raw exception escapes `JiraImportServiceImpl.read`, which catches only `JiraUnavailableException`,
     and the caller gets a generic 500 with no Failed batch.
   - Likelihood: low, because Jira's format is fixed.
   - Fix: wrap the second parse in `time()` and throw `JiraUnavailableException("Jira answered with an unreadable
     date")`, and add a test.
2. `AdfText.java:40-56`. `inlineCard`/`blockCard` (pasted links) have no `content` and no `text`, so a link in a
   description or comment disappears from the plain text. Acceptable under "formatting is dropped". Consider keeping
   `attrs.url`.

Assumptions made:
- The Basic-auth half of the task description is superseded by the spec change of 2026-11-07 (D2, AC-001.12). The
  task is judged against the current spec, so the code at HEAD is what counts.

## Test Report
See `task-3-jira-issue-source-test-report.md`. 632 unit + 48 IT, 0 failures; the client's 24 tests are all green.
