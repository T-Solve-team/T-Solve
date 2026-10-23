## Jira issue
KAN-24

## What & why

Implement the Project Manager review flow across the backend and frontend: Project-scoped queue, all-page selection, Approve, Reject with a reason, and Request changes. Decisions validate the entire selection and persist status, per-ticket history/audit and pending notification handoffs in one transaction.

FE Lead D1 / FE-34: Request changes accepts `ticketIds` and one shared comment for one, several selected or all eligible Solution Form tickets. Ticket Detail sends a one-element selection. D2 removes empty-Solution approval handling, MSG20 and empty flags. The approved spec follows the team `Report3/` documents with D1/D2 overrides while those documents are corrected. Catalog edits stay within MSG13–MSG20; other entries remain for the foundation change.

Preserve main BUG-001/002 exactly, restore route test BUG-002, reconcile branch defects consecutively from BUG-006 and record human ownership. The former bulk-change concern is Won't fix under D1.

**Revision status:** FE Lead amendments are committed locally as `61ca98e` and verified; push is pending. This description records the final change and local evidence, not a fresh CI result for those unpushed amendments.

## How to test

1. Backend: Java 21, isolated PostgreSQL 16, set the normal DB/server/CORS environment variables, then run `mvn verify` in `backend/`. Latest local result after main integration: 42 tests pass, including two-ticket Request changes, per-ticket records, invalid-source rejection, rollback for Reject/Request changes and competing decisions. Spotless passes.
2. Frontend: Node 24; run `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` in `frontend/`. Latest default run after main integration: 25 files, 414 tests pass, no unhandled errors. Repeat `npm run generate:api`: generated output must be unchanged.
3. With a Project Manager session and real API enabled, select two pending Solution Form tickets in Review Queue. Request Changes opens one comment form; submit it and verify both leave the queue, retain their due dates and expose the same comment in detail. Repeat with Select all across pages. Tests cover invalid/oversized comments and failed mutations preserving the popup/input.
4. Verify one-ticket Approve needs no bulk confirmation; multiple Approve uses MSG19; Reject requires one reason. Test non-manager/foreign-Project access and an invalid member of a batch: no partial writes.
5. Optional local HTTP harness: launch the test-only `ManualReviewApplication` with profile `manual-review`, then run `manual-smoke.ps1` with its localhost base URL and configured test password. Latest run: 19 checks pass. Browser verification against this real backend confirmed two tickets with the same comment and exactly one decision/audit/pending notification each.

## Limitations

Real identity/Project provisioning and email delivery/retry are external integration responsibilities. Pending notification is not proof of delivery. Historical default-suite flakiness BUG-027 is fixed by importing main BUG-004 remediation (#42). Narrow-viewport Ticket Detail date wrapping is recorded as BUG-028; its layout is unchanged in this revision. Harness Java trace support remains BUG-013 (17/29 reported by the framework); executable backend coverage is verified by Maven. No schema/dependency/security configuration change is added by the D1/D2 revision.

## Checklist
- [ ] CI rerun after the FE Lead amendments are pushed
- [x] Tests added/updated and local required checks passed
- [x] Generated client regenerated through orval; repeat generation unchanged
- [x] No new migration needed for this revision; existing review migrations remain forward-only
- [x] No secrets or ticket payload logging added
