# KAN-158 Expired Tickets review

Date: 2026-11-01. Module: ticket-review. Feature: expired-tickets. Story: STORY-007.
Branch: feat/KAN-158-review-expired-tickets. Dependency base: a4500fb (KAN-24), explicitly authorized by the user.

## Spec compliance and independent review

Independent reviewer checked the working changes and final tests against AC-007.1–AC-007.8. Verdict: PASS; no confirmed production-code defects or remaining blockers. Parent read the implementation and reran verification rather than relying only on the developer report.

Final parent verification: full standard-host suite exits 0 with 1148/1148 passed and zero failed/pending; format, lint, typecheck, build and deterministic generation pass. Final test-only refinements for BUG-091/092 and equivalent native Queue input/click events were independently reviewed without remaining blockers. See the companion test report for complete evidence and earlier diagnostic failures.

Follow-up independent review of BUG-089/090 test synchronization changes: no issues found. They await real router/session/MSW-backed queries and mutation completion; existing security, confirmation, toast and item-count assertions remain intact. No fake records, fixed sleeps or longer timeouts were introduced. Production code remained unchanged.

| Criterion | Evidence |
|---|---|
| AC-007.1 | Scoped list/metadata, table and inherited detail links; browser detail inspection |
| AC-007.2 | Department URL state, sorting and server-side pagination tests |
| AC-007.3 | Complete filtered eligibility, off-page individual selection, all 24 fixtures, browser Back reset |
| AC-007.4 | Atomic SEND_BACK, per-Project current range, Vietnam calendar rollover, retained Solution/Evidence/history tests |
| AC-007.5 | Invalid/duplicate/oversized/stale batches leave state unchanged; pending and rapid duplicate-send tests |
| AC-007.6 | Shared loading, catalog error/retry and MSG04 empty states |
| AC-007.7 | Required invalidations, failed mutation retains eligible selection, conflict pruning, success followed by failed refetch |
| AC-007.8 | Existing PM route declaration/guard, signed-out/non-PM/foreign/unmanaged mock API rejection |

## Browser evidence

Parent inspected http://localhost:5175/expired-tickets in the real browser: 24 Expired fixtures, 20 rows per page, required columns, disabled empty selection, readonly Expired Ticket Detail, all-filtered selection and successful batch followed by empty state. A separate same-SPA single-ticket send returned EXP-323 to Pending review, exposed Approve/Reject/Request Changes and updated its due date to 06/11/2026 00:00 for the current five-day Project range, retaining content and Evidence. Full reload resets in-memory mock data by design.

At 390 x 844, the inherited shared AppShell sidebar leaves insufficient content width. Logged BUG-088 for separate FE Lead ownership/review; no app/shared changes are included in KAN-158. Viewport override was reset. Browser logs included extension warnings and an HMR dependency-array warning while editing; those are not evidence of a production feature failure.

## Limits

Frontend, generated API proposal and session-only MSW behavior only. No backend endpoint, real expiry job, database transaction, durable audit, email or SSE/WebSocket is delivered. KAN-159 and BUG-087 remain separate tasks. Full frontend verification results are recorded in the companion test report before requesting the feature commit.
