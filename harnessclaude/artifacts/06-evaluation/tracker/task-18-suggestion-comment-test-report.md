---
module: tracker
feature: suggestion-comment
task: 18
attempt: 1
date: 2026-11-07
reviewer: harnessclaude-reviewer
---

# Test Report: tracker — Task 18: suggestion-comment

Commit under test: `5eb385d` (branch `feat/KAN-131-tracker-suggestion-comment`), working tree clean.

## Runs (by the reviewer)

| Command | Result |
|---------|--------|
| `mvn -q -o test -Dtest=JiraClientTest,ConfiguredJiraServiceAccountTest,Msg80Test,SuggestionCommentServiceImplTest,SuggestionJobServiceImplTest,SuggestionJobHandlerTest,InProcessSuggestionJobQueueTest` (TEMP/TMP=D:\tmpj) | 40 tests, 0 failures, 0 errors |
| `mvn -o -q test-compile failsafe:integration-test failsafe:verify -Dit.test=SuggestionResultRepositoryIT` (Testcontainers; applies Liquibase incl. changeset 020) | 8 tests, 0 failures, 0 errors |
| `mvn -o -q spotless:check` | clean (exit 0) |

Per class: JiraClientTest 7, ConfiguredJiraServiceAccountTest 3, Msg80Test 5, SuggestionCommentServiceImplTest 5,
SuggestionJobServiceImplTest 15, SuggestionJobHandlerTest 2, InProcessSuggestionJobQueueTest 3,
SuggestionResultRepositoryIT 8.

Not re-run by the reviewer: the full `mvn verify` (reported passing by the dispatcher) and the manual end-to-end
run against Jira (SAM1-219, comment 13183) — taken as context, not as evidence.

## Observed log lines (from the unit run)

```
WARN SuggestionCommentServiceImpl -- Suggestion comment not posted for ITSUP-1: no Jira service account
WARN SuggestionCommentServiceImpl -- Suggestion comment not posted for ITSUP-1: Jira answered 403
WARN SuggestionJobServiceImpl     -- Suggestion job failed for ITSUP-1: JiraUnavailableException
```

The third line (AC-012.7, Jira 503) carries no HTTP status — see review issue 1.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace tracker STORY-012` → 8/8 acceptance criteria have a test.
`bun harnessclaude/hooks/sdd.ts trace tracker STORY-011` → 9/9 acceptance criteria have a test.

## Coverage

No coverage report produced (no JaCoCo goal run). Every new class has a direct unit test; the repository update
has an integration test.

## Attempt 2 — fix commit `c8c32ea`

| Command | Result |
|---------|--------|
| Same targeted `mvn -q -o test -Dtest=…` set as attempt 1 | 43 tests, 0 failures, 0 errors (JiraClientTest 9, ConfiguredJiraServiceAccountTest 3, Msg80Test 5, SuggestionCommentServiceImplTest 6, SuggestionJobServiceImplTest 15, SuggestionJobHandlerTest 2, InProcessSuggestionJobQueueTest 3) |
| `mvn -o -q spotless:check` | clean (exit 0) |
| `sdd.ts trace tracker STORY-012` / `STORY-011` | 8/8 / 9/9 |

Observed AC-012.7 log line after the fix:

```
WARN SuggestionCommentServiceImpl -- Suggestion comment not posted for ITSUP-1, retried: Jira answered 503 (JiraUnavailableException)
```

`SuggestionResultRepositoryIT` not re-run: the fix changes no repository, entity or changeset (passed 8/8 in attempt 1).
