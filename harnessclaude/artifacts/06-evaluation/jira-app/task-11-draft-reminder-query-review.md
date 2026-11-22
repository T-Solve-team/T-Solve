---
module: jira-app
feature: draft-reminder-query
task: 11
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 11: Open Drafts for the reminder (draft-reminder-query, KAN-336)

Scope: commit `d498e63d` (repo `default`), reviewed on HEAD `4bbbb60c` of `feat/KAN-336-jira-app-draft-reminder`.
The branch's merge-base with `main` is `main`'s HEAD `a09f7d27`, so KAN-333 and KAN-133 are already in it. The working
tree has one unrelated change (`jira-app/TSolve_JiraApp/package-lock.json`).
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` STORY-011 (this task: AC-011.2, AC-011.3). Edge cases:
`harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md`, "Change — 2026-11-14: Draft reminder email".
Passes run: code-review (spec compliance, then quality). There was no separate security-review: the change is a
read-only query that returns no Solution text.

## Summary

Verdict: **PASS WITH NOTES** (frontmatter `pass`).

- `OpenDraft(creatorId, issueKey, updatedAt)` (`ticket/dto/OpenDraft.java`) carries no Solution text, so nothing
  downstream can leak a Draft's content.
- `SolutionDraftRepository.findOpenDrafts()` / `findOpenDrafts(creatorId)` (`SolutionDraftRepository.java:24-42`) use a
  JPQL constructor projection with `not exists` on a `Ticket` with the same `source_ticket_id`. That column has the
  unique constraint `uq_ticket_ticket_id`, so the anti-join is index-backed. Order: `updatedAt desc, issueKey`.
- `SolutionDraftService` exposes both methods through the interface with `@Transactional(readOnly = true)`
  (`SolutionDraftServiceImpl.java:43-53`). `ticket` reads no `identity` table, and the active-Account check is left
  to the caller, as the interface Javadoc says.
- The IT runs in the bound Workspace (tenant A). It checks newest-first ordering, that a ticket of any source closes
  the Draft, the per-creator filter, and that nothing lands in tenant B.

No blocking issue.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | "Open Draft = a Draft whose issue's ticket does not exist" → `not exists (… t.ticketId = d.issueKey)` (`SolutionDraftRepository.java:29`, `:39`). Newest saved first → `order by d.updatedAt desc` (`:30`, `:40`). Tenant scope comes from the bound Workspace's DataSource; no Workspace column is needed. |
| Completeness | pass | AC-011.2 (the ticket exclusion) and AC-011.3 (ordering, `updatedAt` for "last saved") are tested by `SolutionFormTicketIT.listsOpenDraftsNewestFirstAndLeavesOutIssuesWithATicket_AC_011_2_AC_011_3`. The other parts of AC-011.2 (inactive Account, no open Draft) belong to Task 12 and are tested there. |
| Correctness | pass | Unit run: `SolutionDraftServiceImplTest` 3/3 (no regression). IT: the failsafe report written after the commit shows `SolutionFormTicketIT` 4/4, including the new test. The reviewer did not run the IT; see Assumptions. |
| Integration | pass | The only callers are `DraftReminderWorkspaceHandler` and `DraftReminderEmailHandler` (Task 12), through the service interface and the `dto` record, as `backend/AGENTS.md` §1 requires. The interface gained two methods; its only implementation is `SolutionDraftServiceImpl`, and the test doubles are Mockito mocks. |

### Stage 1 — Spec compliance

| AC | Implementation | Test | Result |
|----|----------------|------|--------|
| AC-011.2 (no email for a Draft whose issue's ticket exists) | `SolutionDraftRepository.java:29`, `:39` | `SolutionFormTicketIT.java:186` (a CSV ticket with the key closes the Draft) | PASS |
| AC-011.3 (newest saved first; last saved date) | `:30`, `:40`; `OpenDraft.updatedAt` = `solution_draft.updated_at` | `SolutionFormTicketIT.java:208-211` | PASS |
| AC-011.3 (no Solution text) | `OpenDraft` has no content field | structural | PASS |

## Traceability

```
  ✓ AC-011.1  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.3  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderMessageTest.java
  ✓ AC-011.4  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.5  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.6  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
  ✓ AC-011.7  backend/src/test/java/vn/tsolve/jiraapp/service/impl/DraftReminderHandlersTest.java
trace jira-app (STORY-011): 7/7 acceptance criteria have a test
```

This task's own test, `backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:186`, is outside the module's
trace paths, so the trace does not list it.

## Issues

### Blocking

None.

### Non-blocking

1. **[Test robustness, Low] The IT asserts on the whole Workspace's Draft list.**
   - `SolutionFormTicketIT.java:208-209` uses `containsExactly(newer, older)` on `findOpenDrafts()`, which returns
     every Draft in tenant A, not only those of the test's `PROJECT`.
   - Tenant A is shared per JVM (`TwoTenantDatabases`), and `@BeforeEach` deletes Drafts of `PROJECT` only.
   - Today no other test writes `solution_draft`, so the test is green. The first other IT that leaves a Draft in
     tenant A will make it fail depending on test order.
   - Fix: filter the result to the test's issue keys (for example `filteredOn(d -> d.issueKey().startsWith(KEY))`),
     or delete all `solution_draft` rows of tenant A in this test.
2. **[Behaviour, Low — observation] The ticket check is by issue key only, not by project.**
   - `SolutionDraftRepository.java:29`, `:39` match any ticket whose `source_ticket_id` equals the Draft's key.
   - Within one Workspace a Jira key is unique per site, and `source_ticket_id` is unique, so this follows "the issue's
     ticket (any source)". It is noted only because a CSV or manual ticket that reuses a Jira key would silently close
     a Draft.
3. **[Docs, Info] The plan and the progress SHA are stale.**
   - The session log (`task-11-draft-reminder-query.md`) records `default@77be7235`, a pre-rebase SHA that no branch
     contains. The feature commit on this branch is `d498e63d`.
   - `tasks.md` Task 11 still lists "an inactive creator" as part of the query test. That check moved to the job
     (Task 12), as the session log explains.

## Assumptions made

- **Project membership is not required for an open Draft.** The spec defines "writer" as the active Account that
  owns an open Draft. I read that as no check that the writer is still a member of the Draft's Project (the design's
  decision). **Judgment call for the user:** should a writer who has left a Project still be reminded of its issue
  keys?
- **The IT counts as passed on the existing failsafe report.** `backend/target/failsafe-reports/vn.tsolve.ticket.SolutionFormTicketIT.txt`
  was written at 16:04, after the feature commit at 15:51. It shows 4/4. I did not run Testcontainers myself, by the
  dispatcher's rule: a full `mvn verify` is already running separately and covers it.

## Test Report

See task-11-draft-reminder-query-test-report.md
