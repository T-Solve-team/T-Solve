# Test Report: workspace — Task 3 jira-site-linker (attempt 1)

**Commit:** default@7da71b8
**Date:** 2026-11-12
**Run by:** the orchestrator (recorded in `artifacts/05-implementation/workspace/task-3-jira-site-linker.md`). The
reviewer did not re-run Maven (low-RAM machine, per the review brief).

## Suite (recorded)
| Run | Command | Result |
|-----|---------|--------|
| 1 (worktree) | `mvn -B verify` | stopped by low memory — unit 1476 passed, IT cut off (not a test failure) |
| 2 (working tree, with Task 4) | `mvn -B verify` | BUILD SUCCESS — 1534 unit + 454 IT, 0 failures |

Task classes: `JiraSiteRuleTest` 35, `InterimJiraSiteLinkerTest` 32. All passed.

## Traceability (run by the reviewer)
`bun harnessclaude/hooks/sdd.ts trace workspace STORY-007` → 7/7 by name. AC-007.3 → `JiraSiteRuleTest`,
`InterimJiraSiteLinkerTest`; AC-007.4 → `InterimJiraSiteLinkerTest`.

## Coverage gap found by reading the tests
Every `InterimJiraSiteLinkerTest` case builds the linker from a `MockRestServiceServer` builder. The production request
factory, whose `prepareConnection` turns redirects off, is never run. The "follow no redirect" clause of AC-007.4 is
therefore not covered (review Issue 1).
