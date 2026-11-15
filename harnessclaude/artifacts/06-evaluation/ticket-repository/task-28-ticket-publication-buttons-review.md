---
module: ticket-repository
feature: ticket-publication-buttons
task: 28
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
---

# Review: ticket-repository — Task 28: ticket-publication-buttons

## Summary
`default` da60828..1315800 adds `TicketPublicationActions` above `ReviewTicketDetail` on Ticket Detail. It shows
Unpublish for a Published ticket and Re-publish for an Unpublished one, only to a Project Manager of the ticket's
Project. It asks MSG21 (danger) or MSG23 through the shared `ConfirmDialog`, sends `{ ticketIds: [id] }` through the
generated hooks, shows MSG22 or MSG24, and on success or error invalidates the detail, the Ticket List and Review
History. AC-009.8, AC-009.9 and AC-009.10 are met, and the 9 tests assert the refetched status on screen, not just
the invalidation. Glossary words, the MSG catalog and the shared UI are used as the rules ask. Nothing under
`generated/` is touched. No CRITICAL findings. The new test file passes 9/9. The full suite had timeouts in other
files under machine load, all of which passed on re-run. Verdict: pass, with one open judgment call (Issue 1).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 table below |
| Completeness | pass | No MISSING criterion. EXTRA: the Review History query is invalidated as well, which does no harm (D35: those rows are not listed yet) |
| Correctness | pass | `TicketPublicationActions.test.tsx` 9/9; format, lint (0 errors), typecheck, build pass; full-suite timeouts are elsewhere and passed on re-run (test report) |
| Integration | pass | Uses the generated `useUnpublishTickets` / `useRepublishTickets` of Task 26 and the same `useGetReviewTicketDetail` key as `ReviewTicketDetail` (one request); `ReviewTicketDetail` is not changed |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-009.8 Unpublish shown for a Published ticket to its PM; MSG21; confirm → request, MSG22, shown Unpublished; cancel sends nothing | PASS | `TicketPublicationActions.tsx:52-61` (status + `hasRole('PROJECT_MANAGER') && isProjectManagerOf(projectId)`), `:73-80` (`ConfirmDialog` MSG21, `isDanger`), `:44-46` (MSG22), `:31-43` (invalidate). Tests: "AC-009.8 unpublishes … shows MSG22" asserts the request body and URL, MSG22, and that the refetched detail now shows Re-publish; "AC-009.8 sends nothing when Unpublish is cancelled" |
| 2 | AC-009.9 the same for Re-publish with MSG23 / MSG24 | PASS | Same lines. Tests: "AC-009.9 re-publishes … shows MSG24" (request, MSG24, Unpublish shown after the refetch); "AC-009.9 sends nothing when Re-publish is cancelled" |
| 3 | AC-009.8 / AC-009.9 "WHILE … Project Manager of": no button otherwise | PASS | Test table: Ticket Resolver, PM of another Project, Pending review, Rejected → no button (P12) |
| 4 | AC-009.10 rejected request → message of the error (MSG06 if none), detail reloaded | PASS | `onSettled` invalidates the detail on error too (`:32-42`); the global mutation handler (`shared/api/query-client.ts:53`) shows the ProblemDetail code, else MSG06. Test: a 409 without a code shows MSG06, no MSG22, and the reloaded detail shows Re-publish (P10) |

### Rules checked
- Glossary (`guidelines/03-naming.md:99`): button labels "Unpublish" / "Re-publish" are exact.
- Messages only from the catalog: `showMessage('MSG22' | 'MSG24')`, `ConfirmDialog code="MSG21" | "MSG23"`; no
  typed text. Danger for Unpublish only, as `09-forms-and-validation.md:104` says.
- UI: antd `Button` / `Flex` plus shared `ConfirmDialog`; no banned raw `Modal` / `message`; no inline style.
- Data: server data only through TanStack Query; generated query keys; no hand-written fetch.
- Loading / error: renders nothing until the detail is loaded or when it failed (`ReviewTicketDetail` shows those
  states). The button is disabled while the detail refetches or the request runs, and `isConfirming` puts OK into
  loading (P11).
- Every changed file is under `frontend/src/features/tickets/**`, which is in the module's `paths`.

## Traceability
```
  ✓ AC-009.1  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.2  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.3  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.4  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.5  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.6  backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.7  backend/src/test/java/vn/tsolve/ticket/service/impl/TicketPublicationTest.java, backend/src/test/java/vn/tsolve/ticket/controller/TicketPublicationIT.java
  ✓ AC-009.8  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
  ✓ AC-009.9  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
  ✓ AC-009.10  frontend/src/features/tickets/components/TicketPublicationActions.test.tsx
trace ticket-repository (STORY-009): 10/10 acceptance criteria have a test
```

## Issues

CRITICAL (blocks pass): none.

Informational (does not block):

1. **Judgment call, feature ownership.** `frontend/src/features/tickets/components/TicketPublicationActions.tsx:44-49`
   puts the Unpublish / Re-publish mutations in `features/tickets`. `frontend/guidelines/01-project-structure.md:181-183`
   says "All review decisions live in `review`: Approve, Reject, Request changes, Unpublish, Re-publish, Send back to
   queue … Ticket Detail will show them through `TicketDecisionActions` exported by `review`."
   Expected (guideline): the actions come from `review`. Actual: they live in `tickets`, as the approved design chose
   ("Buttons" row: keep `ticket-review` code untouched).
   Fix: either keep it and get the FE Lead's agreement, or later move the component to `review` and export it as
   `TicketDecisionActions`. I did not fail the task on this because the spec does not say which folder, and the
   design decided it with the user.
2. `TicketPublicationActions.tsx:31-43` — the invalidation differs from `guidelines/06-data-layer.md:115`
   (Unpublish / Re-publish → Ticket Detail, Ticket List, Expired Tickets, Review Queue). It invalidates Ticket
   Detail, every `/api/v1/tickets…` query and Review History, but not the Review Queue or the Expired Tickets
   queries. No stale data follows today, because neither list can hold a Published or Unpublished ticket.
   Fix: add the two generated keys to follow the table, or update the table.
3. `TicketPublicationActions.test.tsx:124-160` — AC-009.10 is proven only for the fallback (409 without a code →
   MSG06). The case "show the message of the error" with a code, for example the 403 MSG08 of D37, is left to the
   global handler and has no test here.
   Fix: add one case answering `problem(403, 'MSG08')` and assert MSG08 and the reload.
4. `TicketPublicationActions.tsx:16` — its own `UUID_PATTERN` is stricter (version `[1-8]`, variant `[89ab]`) than
   the one of `ReviewTicketDetail.tsx:14`. For an id with version nibble 0 (for example the zero-padded legacy ids
   the backend builds with `lpad(to_hex(…))`), the detail would load but the buttons would never show.
   Fix: use the same pattern as `ReviewTicketDetail`, or simply `enabled: ticketId !== ''`.
5. P11 (double click on OK) has no test. It is covered by `isConfirming` → `confirmLoading` and the disabled button.

## Assumptions made
- I treated the feature-folder rule of `guidelines/01` as informational because the design, decided with the user,
  chose `features/tickets`. **Open for the user / FE Lead:** keep it there, or move it to `review`?
- I treated the full-suite timeouts as caused by machine load: every failed file passed on re-run and none is in
  this range.

## Test Report
See `task-28-ticket-publication-buttons-test-report.md`. `format:check` pass · `lint` 0 errors, 1 warning
(another file) · `typecheck` exit 0 · `build` exit 0 · `generate:api` no diff. `TicketPublicationActions.test.tsx`:
Tests 9 passed (9). Full `npm run test`: Tests 5 failed | 1475 passed | 323 skipped, all timeouts in other files,
which passed when re-run (re-run 2: Test Files 7 passed (7), Tests 115 passed (115)). Coverage not measured.
