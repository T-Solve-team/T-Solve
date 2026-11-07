---
module: tracker
feature: suggestion-comment
task: 18
verdict: pass
attempt: 2
date: 2026-11-07
reviewer: harnessclaude-reviewer
---

# Review: tracker — Task 18: Post the suggestion as the MSG80 comment (STORY-012, AC-011.7, KAN-131)

## Summary

Reviewed commit `5eb385d` against spec STORY-012 (AC-012.1 … AC-012.8), the reworded AC-011.7 and Non-Functional 4
and 9, with design edge cases 51–57 for context. The flow is sound: the comment is posted after the suggestion is
stored, the posting is idempotent across reruns (the `jira_comment_id` guard plus finding the service account's own
"T-Solve suggestion:" comment), `markCommented` is conditional, the new columns are read-only in the entity,
changeset 020 is forward-only and included, Basic auth is set on a cloned builder, and no credential or suggestion
text reaches an exception message, `toString` or a log line. All targeted tests and the repository IT pass;
traceability is 8/8 and 9/9.

One clause of an acceptance criterion is not met: **AC-012.7 requires the log to carry the HTTP status**, and the
retryable failure path logs only the issue key and the error type (the status is dropped). This is a small fix, but
it is a spec clause with no implementation and no test, so the verdict is **fail** (attempt 1). See "Assumptions
made" — the user may choose to accept it as a note instead.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | fail | AC-012.1–012.6, 012.8 and AC-011.7 met. AC-012.7: retry and "keep the suggestion" met, but the log line omits the HTTP status (issue 1). |
| Completeness | pass | Every AC-012.x and AC-011.7 has a test naming it; all design files of Task 18 present; `.env.example` and `application.yml` updated. |
| Correctness | pass | 40 unit tests + 8 IT pass; spotless clean. Residual risks are Low (issues 2–5). |
| Integration | pass | `TicketTrackerClient` has a single bean (`JiraClient`); `JiraServiceAccount` is a port for `workspace`; triage's read-only entity unaffected by the two added nullable columns. |

### Criterion by criterion

| AC | Verdict | Evidence |
|----|---------|----------|
| AC-012.1 | met | `SuggestionJobServiceImpl.run` posts after `fillSuggestion` returns 1; `Msg80.paragraphs` = prefix, suggestion lines, question; no "Related documents". Basic auth with the service account (`JiraClient.java:80-84`). |
| AC-012.2 | met | Only `suggestedSolution` reaches `Msg80`; sources are never passed to the comment path. |
| AC-012.3 | met | `markCommented(id, commentId, Instant.now())` after the Jira call; a row with a suggestion and no comment id goes straight to `comments.post` without search. |
| AC-012.4 | met | `run` returns early on `hasComment()`; `markCommented` is `… where jiraCommentId is null` (IT proves a second call changes 0 rows). |
| AC-012.5 | met | `findComment` (author = `/myself` accountId, ADF text starts with `T-Solve suggestion:`) runs before every `addComment`; found id is stored, nothing posted. |
| AC-012.6 | met | No `comments.post` when search returns nothing or another run filled the row. |
| AC-012.7 | **partly met** | 5xx/429/I/O/timeout → `JiraUnavailableException` → `SuggestionJobFailedException` → JobRunr retry; suggestion kept. **Log lacks the HTTP status** (issue 1). |
| AC-012.8 | met | No account → warn "no Jira service account", return; 401/403/404 → `JiraRejectedException` caught, warn with key + status, return (no retry, no store). |
| AC-011.7 | met | Row gone → stop; row filled → no search, no `fillSuggestion`; missing comment posted. |
| NF 4 / NF 9 | met | `JiraAccount.toString` omits email/token; Jira exceptions carry only the status and no cause or body; `failed()` logs only the exception simple name; tests assert absence of email, token and suggestion text. |

## Traceability

```
  ✓ AC-012.1  ConfiguredJiraServiceAccountTest, Msg80Test, SuggestionCommentServiceImplTest, SuggestionJobServiceImplTest, JiraClientTest
  ✓ AC-012.2  Msg80Test
  ✓ AC-012.3  SuggestionCommentServiceImplTest, SuggestionJobServiceImplTest, SuggestionResultRepositoryIT
  ✓ AC-012.4  SuggestionJobServiceImplTest, SuggestionResultRepositoryIT
  ✓ AC-012.5  Msg80Test, SuggestionCommentServiceImplTest, JiraClientTest
  ✓ AC-012.6  SuggestionJobServiceImplTest
  ✓ AC-012.7  SuggestionCommentServiceImplTest, SuggestionJobServiceImplTest, JiraClientTest
  ✓ AC-012.8  ConfiguredJiraServiceAccountTest, SuggestionCommentServiceImplTest, JiraClientTest
trace tracker (STORY-012): 8/8 acceptance criteria have a test
  ✓ AC-011.1 … AC-011.9 (AC-011.7: SuggestionJobServiceImplTest, SuggestionResultRepositoryIT)
trace tracker (STORY-011): 9/9 acceptance criteria have a test
```

## Issues

1. **MEDIUM — AC-012.7: the HTTP status is not logged on a retryable Jira failure.**
   `backend/src/main/java/vn/tsolve/tracker/service/impl/SuggestionJobServiceImpl.java:91-94` — `failed()` logs
   `"Suggestion job failed for {}: {}"` with `e.getClass().getSimpleName()` only, so a 503, a 429 and a timeout
   (status 0) all log as `JiraUnavailableException`. `SuggestionCommentServiceImpl` does not log on this path
   either. Observed in the test run: `Suggestion job failed for ITSUP-1: JiraUnavailableException`. The AC says
   "log only the issue key, the HTTP status and the error type". The test
   `SuggestionJobServiceImplTest.java:240` (`failsForARetryWhenJiraIsUnavailable_AC_012_7`) does not assert the
   status.
   Fix: when `e` is a `JiraUnavailableException`, include `getStatus()` in the log line (e.g. catch it in
   `SuggestionCommentServiceImpl.post` and `log.warn("Suggestion comment not posted for {}: Jira unavailable ({})",
   issueKey, e.getStatus())` before rethrowing, or extend `failed()`); add `assertThat(output).contains("503")` to
   the AC-012.7 test.

2. **LOW — empty comment id stored when Jira's 2xx body has no `id`.**
   `backend/src/main/java/vn/tsolve/tracker/client/JiraClient.java:57` returns `""`, and
   `SuggestionCommentServiceImpl.java:49` stores it; `hasComment()` is then true with a blank id, so the row
   claims a comment it cannot reference. Fix: treat a missing id as `JiraUnavailableException(0)` (the rerun's
   `findComment` then recovers the real id), or skip `markCommented` for a blank id.

3. **LOW — a blank `/myself` accountId disables the AC-012.5 safeguard.**
   `JiraClient.java:64-67` returns `Optional.empty()` when the accountId is blank, after which `post` adds a
   comment — a second one if a previous run had already posted. Unlikely with a real account; fix by treating a
   blank accountId as `JiraUnavailableException(0)` rather than "not found".

4. **LOW — find-then-add is not atomic; two concurrent runs for the same row could both post.**
   `SuggestionCommentServiceImpl.java:43-44`. `markCommented` keeps the DB consistent but cannot prevent two Jira
   comments. Mitigated today: one job per issue (AC-011.1) and JobRunr retries are sequential; becomes relevant if
   another caller (e.g. the panel's "search again", AC-007.5) ever triggers posting. No change requested now;
   record as a known risk.

5. **INFO — other observations, no change required.**
   - `findComment` scans only the newest 100 comments (`JiraClient.java:29`); a lost comment buried under more than
     100 newer ones would be posted again. Acceptable for a rerun window.
   - Any 4xx other than 429 (e.g. 400 for an oversized body) is treated as "rejected, no retry"; the spec names only
     401/403/404 but is silent on others — reasonable.
   - `markCommented`'s return value is ignored; "Suggestion comment posted" is logged even when 0 rows changed.
   - `Msg80.MAX_LENGTH` budgets plain text (32,000) and not the ADF JSON overhead; well inside Jira's limit for
     realistic text (edge case 57).
   - Config: `${JIRA_SITE_URL:}`, `${JIRA_SERVICE_ACCOUNT_EMAIL:}`, `${JIRA_SERVICE_ACCOUNT_TOKEN:}`
     (`application.yml:85-87`) use an empty default. AGENTS.md says secrets/hosts/URLs have no default; the intent
     of that rule (no real value committed, nothing environment-specific) is kept — the empty value is an
     "off" sentinel, matching existing precedents `${JOBS_DB_URL:}`, `${JOBS_DB_PASSWORD:}` and
     `${TSOLVE_TRACKER_DEV_WORKSPACE_ID:}`, and the stand-in is to be deleted when `workspace` stores the account.
     Judged acceptable (see Assumptions).
   - AGENTS.md otherwise respected: interface + impl for the services, constructor injection with `private final`,
     no Lombok, no wildcard imports, exceptions extend `common.exception.CodedException`, no outbound HTTP inside a
     transaction (`post` is not `@Transactional`; `markCommented` runs in its own transaction after the call).
   - Changeset `020-tracker-suggestion-comment.sql`: formatted SQL, author `tracker`, two `ADD COLUMN`s of nullable
     columns, forward-only, included last in `db.changelog-master.xml`. Entity maps both with
     `insertable = false, updatable = false`.

## Assumptions made

- **An unmet clause of an AC is a fail.** I read AC-012.7's "log only the issue key, the HTTP status and the error
  type" as requiring the status, not only bounding the content. If the user considers the status optional, issue 1
  becomes a note and the verdict would be pass with notes.
- **Empty `${VAR:}` defaults for the Jira stand-in are acceptable** under AGENTS.md §4 because no real host or
  secret is committed, the empty value means "feature off", and the codebase already uses the same pattern for
  `JOBS_DB_*` and `TSOLVE_TRACKER_DEV_WORKSPACE_ID`. The user may prefer a strict reading (no default, startup
  fails), which would force every developer to set the three variables.
- Treating 4xx codes other than 401/403/404/429 as non-retryable is assumed in line with the spec's intent
  ("do not heal with a retry").

## Test Report

See `task-18-suggestion-comment-test-report.md`: 40 targeted unit tests and 8 repository IT tests pass, spotless
clean, traceability 8/8 (STORY-012) and 9/9 (STORY-011).

## Attempt 2 — 2026-11-07 — Verdict: pass

Re-reviewed fix commit `c8c32ea` (on top of `5eb385d`) on `feat/KAN-131-tracker-suggestion-comment`. The frontmatter
above now carries this attempt's verdict; the attempt 1 sections are kept unchanged above for the record (attempt 1
verdict: fail).

| Attempt 1 issue | Status | Evidence |
|-----------------|--------|----------|
| 1 — MEDIUM, AC-012.7, no HTTP status in the log (BUG-166) | Fixed | `SuggestionCommentServiceImpl.post` catches `JiraUnavailableException`, logs the issue key, the status ("nothing" for 0) and the error type, then rethrows so the job still fails for a retry. Observed: `Suggestion comment not posted for ITSUP-1, retried: Jira answered 503 (JiraUnavailableException)`. Test `logsTheHttpStatusWhenJiraIsUnavailable_AC_012_7_BUG_166` asserts key, `503`, type, and absence of suggestion text and token. |
| 2 — LOW, AC-012.3, empty comment id stored (BUG-167) | Fixed | `JiraClient.addComment` uses `requiredText(created, "id")`, so a blank or missing id becomes `JiraUnavailableException(0)` → retry; the rerun recovers the posted comment through `findComment` (AC-012.5). Test `treatsACreatedCommentWithoutIdAsUnavailable_AC_012_3_BUG_167`. |
| 3 — LOW, AC-012.5, blank `/myself` accountId (BUG-168) | Fixed | `findComment` uses `requiredText(me, "accountId")` and throws `JiraUnavailableException(0)` for a null comment page instead of answering "not found". Test `treatsAServiceAccountWithoutIdAsUnavailable_AC_012_5_BUG_168`. |
| 4 — LOW, find-then-add not atomic | Accepted as is (coordinator) | Unchanged; remains a known risk. |
| 5 — INFO notes | Accepted as is (coordinator) | Unchanged. |

New observations (INFO, no change required):
- The null comment page branch in `findComment` has no dedicated test (only the blank accountId branch is tested);
  the shared `requiredText` / `JiraUnavailableException(0)` behaviour makes this low risk.
- On a retryable failure two WARN lines are now written (the comment service's line with the status and the job's
  `Suggestion job failed for …: JiraUnavailableException`). Both contain only the key, the status and the error type,
  so Non-Functional 4 and 9 still hold.
- `docs/bug-log.md` rows BUG-166..167 are present, marked Fixed and name their regression tests, as the project rules
  require.

No regression: a 401/403/404 still logs and ends without retry (`Jira answered 403` in the run); the
`JiraRejectedException` catch comes before the new catch and the two exception types are separate.

Assumptions made (unchanged from attempt 1): the empty `${JIRA_*:}` defaults are judged acceptable by the reviewer;
this is still the reviewer's assumption, not a user decision, and the user may overrule it. The other two
assumptions stand as written.

Tests (attempt 2): 43 targeted unit tests pass (JiraClientTest 9, ConfiguredJiraServiceAccountTest 3, Msg80Test 5,
SuggestionCommentServiceImplTest 6, SuggestionJobServiceImplTest 15, SuggestionJobHandlerTest 2,
InProcessSuggestionJobQueueTest 3); `spotless:check` clean; trace STORY-012 8/8, STORY-011 9/9. The repository IT
was not re-run (the fix touches no repository, entity or changeset); the coordinator reports full `mvn verify`
passing.
