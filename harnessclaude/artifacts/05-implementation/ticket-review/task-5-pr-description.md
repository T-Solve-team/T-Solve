## Jira issue
KAN-24

## What & why

Implement the Project Manager review flow across the backend and frontend: Project-scoped queue, all-page selection, Approve, Reject with a reason, and Request changes. Decisions validate the entire selection and persist status, per-ticket history/audit and pending notification handoffs in one transaction.

FE Lead D1 / FE-34: Request changes accepts `ticketIds` and one shared comment for one, several selected or all eligible Solution Form tickets. Ticket Detail sends a one-element selection. D2 removes empty-Solution approval handling, MSG20 and empty flags. Business requirements follow the team `Report3/` documents with D1/D2 overrides. Main's full Report3 catalog is now integrated; review-specific catalog changes remain within MSG13–MSG20.

Preserve main BUG-001…005 exactly, keep route test BUG-002, reconcile branch defects consecutively from BUG-007 and record human ownership. The former bulk-change concern is Won't fix under D1.

**Revision status:** FE/BE review amendments and main conflict resolution are committed as `19485d9`; this revision is being pushed with its companion evidence commit. CI must rerun on the new revision.

Review code/tests now live in `vn.tsolve.review`; legacy snapshot classes stay in `vn.tsolve.ticket`. Review component decision tests live under `features/review`, with ownership paths narrowed to the module and shared contracts. Main context/map and other modules' tracking are preserved. Unpublished detail is restricted to the managing Project PM or author within trusted Project scope; other readers get only Published content. All decision APIs accept at most 500 IDs; Select all preserves the selection but actions are blocked above 500 with guidance to narrow it. No auto-chunking or partial transactions. Null Evidence normalizes to an empty immutable list; legacy missing resources use MSG04, and shared validation preserves the actual MSG code/params.

## How to test

1. Backend: Java 21, isolated PostgreSQL 16, set the normal DB/server/CORS environment variables, then run `mvn verify` in `backend/`. Latest local result after FE/BE amendments: 64 tests pass, including two-ticket Request changes, per-ticket records, invalid-source rejection, rollback for Reject/Request changes and competing decisions. Spotless passes.
2. Frontend: Node 24; run `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` in `frontend/`. Latest default run after FE/BE amendments: 28 files, 441 tests pass, no unhandled errors. Repeat `npm run generate:api`: generated output must be unchanged.
3. With a Project Manager session and real API enabled, select two pending Solution Form tickets in Review Queue. Request Changes opens one comment form; submit it and verify both leave the queue, retain their due dates and expose the same comment in detail. Repeat with Select all across pages. At 501 selected, verify every action is disabled; narrow to 500 and verify actions become available. Tests cover invalid/oversized comments and failed mutations preserving the popup/input.
4. Verify a non-PM reader cannot see another author's unpublished detail or Review Queue link, but can read Published content within their Project scope. Verify one-ticket Approve needs no bulk confirmation; multiple Approve uses MSG19; Reject requires one reason. Test non-manager/foreign-Project access and an invalid member of a batch: no partial writes.
5. Optional local HTTP harness: launch the test-only `ManualReviewApplication` with profile `manual-review`, then run `manual-smoke.ps1` with its localhost base URL and configured test password. Latest run: 25 HTTP checks pass, including unpublished-reader denial and 501-ID rejection for every action. Prior browser verification: Browser verification against this real backend confirmed two tickets with the same comment and exactly one decision/audit/pending notification each.

## Limitations

**Production review API is not usable until a trusted ReviewAccessService adapter is connected.** Current production main has no adapter; requests fail closed with 403 MSG08. Prerequisites are be-foundation `roles-security` / `me-session`, identity sign-in/account status, and workspace/Project authorization. Those upstream tasks currently have unassigned Jira keys (`KAN-?` in the plan); this PR does not claim an invented delivery issue or a working production sign-in. The manual adapter exists only in test sources.

**Email is an unconnected outbox contract.** Rejection and Request changes save pending records atomically; ReviewNotificationService exposes pending/acknowledgeDelivery for a future trusted consumer. No production consumer, SMTP delivery or retry worker is included. A pending record is not evidence of sending; GBR-27 retry/logging remains the sender integration's responsibility.

`/api/tickets/{id}` is a legacy import snapshot; `/api/v1/tickets/{id}` is a review record. Their numeric IDs are separate namespaces. The ticket-repository module must define a source mapping and migration/consolidation plan before treating them as one entity; this PR preserves the existing URLs and data.

Historical default-suite flakiness BUG-028 is fixed by importing main BUG-004 remediation (#42). Narrow-viewport Ticket Detail date wrapping remains BUG-029. Harness Java trace support remains BUG-014; executable backend coverage is verified by Maven. No new migration or dependency is required by these review amendments.

## Checklist
- [ ] CI rerun after the FE Lead amendments are pushed
- [x] Tests added/updated and local required checks passed
- [x] Generated client regenerated through orval; repeat generation unchanged
- [x] No new migration needed for this revision; existing review migrations remain forward-only
- [x] No secrets or ticket payload logging added

Main 48cb785 (Chatbot) is integrated locally as cb073ef. Combined review/Chatbot OpenAPI regenerates unchanged; standalone frontend suite is 441/441, backend 64/64. A concurrent Graphify/test run timed out once in the initial queue render; its standalone rerun passed and the observation is retained in BUG-028. Main BUG-001…006 are preserved; review references were shifted after that range per the user's numbering decision.
