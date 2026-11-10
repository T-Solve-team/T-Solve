---
module: ticket-repository
feature: jira-import-schema
task: 2
verdict: pass
attempt: 1
date: 2026-11-07
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 2: Schema, entities, Draft-aware repositories, Project Jira key

## Summary
Commit `b0a9ee81`. The Liquibase changeset, the entity fields, the Draft-aware repository queries and the Project
Jira key lookup work, and an integration test against real PostgreSQL proves them. AC-004.6 holds: the cleanup query
and the pre-upload/pre-pull delete both filter `draft_saved_at IS NULL`, and the partial unique index lets one open
preview sit beside any number of Drafts. Two deviations from the plan are logged with reasons (migration number 010;
Jira key from `ProjectAccess` instead of `ProjectService`). The second also departs from the spec's "Required from
other modules" line and needs the user's agreement (Assumptions).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `010-ticket-jira-import.sql:8-21`: `file_name` nullable; `resolved_from`/`resolved_to`/`more_available`/`draft_saved_at`; `uq_import_preview_owner` replaced by the partial `uq_import_preview_owner_open … WHERE draft_saved_at IS NULL`; Draft index; row `status VARCHAR(20)`, `source_url`, `comments JSONB DEFAULT '[]'`. `ImportPreviewRepository.deleteByOwner` and `findIdsCreatedBefore` skip Drafts; `findByOwnerAndDraftSavedAtIsNotNullOrderByDraftSavedAtDesc`. The Solution columns of `ImportPreviewRow` became updatable for fill-solution. |
| Completeness | pass | AC-004.6 is covered. The design's Data Model is fully present. |
| Correctness | pass | `ImportDraftRepositoryIT` 6/6 on PostgreSQL. The full suite is green. |
| Integration | pass (note) | Spec "Required from other modules": "`project`: the Project's Jira project key, through `ProjectService`". The implementation uses `ProjectAccess.jiraProjectKey(UUID)`, backed by `StubProjectAccess` and `TSOLVE_STUB_JIRA_KEYS`. The session log's reason: the `project` table still has BIGINT ids while ticket Projects are UUIDs. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-004` → `7/7 acceptance criteria have a test`.
This task's criterion: ✓ AC-004.6 `backend/src/test/java/vn/tsolve/ticket/repository/ImportDraftRepositoryIT.java`.
(STORY-001 12/12; the schema serves AC-001.1/001.2, which Task 4 tests.)

## Issues
No blocking issues.

Informational:
1. `backend/src/main/java/vn/tsolve/ticket/service/ProjectAccess.java:18-19`, `StubProjectAccess.java:44-50`.
   Expected (spec Interface Contracts): the Jira key comes through `ProjectService`. Actual: it comes from a
   stub-only env map. Until project-integration links real Projects, every pull from a Project missing from
   `TSOLVE_STUB_JIRA_KEYS` answers MSG50 "not connected".
   Fix: either get the user's agreement and update the spec line (Change Mode), or add
   `ProjectService.findJiraProjectKey` once Project ids are UUIDs.
2. `010-ticket-jira-import.sql`: the changeset is numbered 010 with no 009 on this branch. The 009 is reserved for
   KAN-31's renumbered import-type changeset (session log). Liquibase orders by the master include, so this works,
   but whoever merges second must keep the include order.

Assumptions made:
- I accepted the `ProjectAccess` deviation as non-blocking because the session log justifies it. The spec still names
  `ProjectService`, so the user must confirm it. Open judgment call.

## Test Report
See `task-2-jira-import-schema-test-report.md`. 632 unit + 48 IT, 0 failures; `ImportDraftRepositoryIT` 6/6.
Spotless clean on the changed files (equivalent check; the in-worktree ratchet cannot run).
