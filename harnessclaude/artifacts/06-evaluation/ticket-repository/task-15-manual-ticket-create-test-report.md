# Test report: ticket-repository — Tasks 14–16 (manual-ticket-contract, manual-ticket-create, manual-ticket-drafts)

**Date:** 2026-11-14 · **Run by:** harnessclaude reviewer (independent subagent) · **Branch:** feat/KAN-133-ticket-repository-manual-ticket @ 2bd4f203 (range 6a3b0ae7..2bd4f203)

One run covers Tasks 14, 15 and 16 (the same backend code path); this file is shared by the three test reports.

## Command

```
export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"
cd backend
mvn -B verify -Dtest='Manual*,ImportDraft*,ImportPreview*,ImportConfirm*,Jira*' \
  -Dit.test='ManualTicketControllerIT,ImportConfirmIT,ImportDraft*IT,Jira*IT' \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

## Result: BUILD SUCCESS (02:05 min)

| Phase | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| Surefire (unit) | 297 | 0 | 0 | 0 |
| Failsafe (IT, Testcontainers pgvector:pg16) | 29 | 0 | 0 | 0 |
| `spotless:check` | 28 files clean, 0 need changes | | | |

Classes of this change in the run: `ManualTicketServiceImplTest`, `ManualTicketDraftTest`, `ManualTicketControllerIT`,
`ImportConfirmIT` (now on the shared `TicketTestDatabase`), plus the existing `ImportDraft*`, `ImportPreview*`,
`ImportConfirm*`, `Jira*` unit tests and `Jira*IT` / `ImportDraft*IT` (regression of STORY-002..004 and import-ticket-list).
The ERROR log lines in the output are expected-exception logs of negative tests, not failures.

## Traceability (`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-006`)

13/14 — AC-006.1 … AC-006.13 each have a test naming them; AC-006.14 (web app) is Task 17, out of this run.

## Not run

- Frontend checks (`format:check`, `lint`, `typecheck`, `test`, `build`, `generate:api` no-diff) — another agent was
  editing `frontend/` during this review; the developer and orchestrator report them green for Task 14.
- Full `mvn verify` (all 1550 unit + 490 IT) — the developer reports it green; this run is the scoped subset above.
