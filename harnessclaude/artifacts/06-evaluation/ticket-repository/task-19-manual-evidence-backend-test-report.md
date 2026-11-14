# Test report: ticket-repository — Task 19 (manual-evidence-backend), commit 4aebbca2

Run by the reviewer on 2026-11-14, backend, JBR 21:
`mvn -B test -Dtest='ManualTicket*,ImportDraftServiceImplTest,CsvImportServiceImplTest,ImportControllerTest,GlobalExceptionHandlerTest'`

| Class | Tests | Result |
|-------|-------|--------|
| ManualTicketEvidenceTest | 19 | pass |
| ManualTicketServiceImplTest | 13 | pass |
| ManualTicketDraftTest | 11 | pass |
| ManualTicketControllerIT (Testcontainers; matched the pattern) | 19 | pass |
| ImportDraftServiceImplTest | 8 | pass |
| CsvImportServiceImplTest (MSG28) | 16 | pass |
| ImportControllerTest (MSG28) | 22 | pass |
| GlobalExceptionHandlerTest (MSG28) | 29 | pass |
| **Total** | **137** | **0 failures, 0 errors** — BUILD SUCCESS |

The ERROR log lines in the run come from tests that make `audit.record` / commit fail on purpose. They log the class name only.

Not re-run here (from the session log): full unit set 267/267, ImportConfirmIT 12, `spotless:apply`, the frontend checks, and `generate:api` with no diff.

Traceability: `sdd.ts trace ticket-repository STORY-006` → 21/22. AC-006.22 is open and belongs to Task 20.
