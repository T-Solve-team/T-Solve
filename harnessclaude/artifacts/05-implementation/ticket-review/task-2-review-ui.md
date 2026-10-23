# Task 2 — Review frontend and live local integration

Date: 2026-10-21. Module: ticket-review. Branch: feat/ticket-review. No subagents.

## Delivered behavior

- Queue uses Project filtering and server pagination/sorting, selects IDs across all matching pages, confirms bulk approval with MSG19, and exposes Approve/Reject only. Pending mutations disable decision buttons.
- Ticket Detail calls the generated getReviewTicket endpoint. Solution sections and saved Evidence metadata are read-only; no fabricated file URL. The Project manager sees the three actions only while Pending review. Empty Solution disables approval with MSG20; source restriction uses MSG18.
- Approve/Reject/Request changes use existing generated mutations and shared modals. Successful mutations invalidate queue, filtered ID lists and affected ticket details. Failed form saves retain user input and the open popup. Comments/reasons are required and limited to 1,000 characters.
- Review messages MSG13–MSG20 follow Report 3 v3460. Legacy message definitions for other features are not a claim of whole-app SRS migration. Directly affected kit empty-state examples now use MSG13.
- Shared fetch transport forwards the decoded XSRF-TOKEN cookie as X-XSRF-TOKEN on unsafe requests. It keeps credentialed requests and normal error handling. This is the session transport required by AC-006.5, not a new identity provider.

## Auth and scope

ManualReviewSessionController exists only under backend/src/test and is imported only by the explicit manual-review test launcher. It supplies /api/v1/me from the authenticated test principal and trusted ReviewAccessService. Manual security supports a local form login and the existing Basic Swagger/HTTP checks; API requests without authentication return 401. The server binds to loopback and requires the isolated review_manual database.

Use the same hostname for backend login and frontend: localhost. Session cookies are host-scoped. Frontend VITE_API_MOCKING=false and an empty API base URL use the existing /api Vite proxy. No production OIDC, SMTP, user/Project administration or permissive production adapter has been introduced.

The real identity integration must supply /me, session cookies and the CSRF cookie/header convention used by the transport (CookieCsrfTokenRepository and matching token request handler). It must revalidate managed Projects server-side. Missing production integration still denies review access.

Evidence retrieval, Jira submission/resubmission/panel delivery, email delivery/retries, search/triage/chatbot consumers and the full Review History UI remain external scope from the approved spec. A pending email handoff is not a delivered email. SRS success text does not establish delivery.

## Evidence

- Message regression tests initially failed because current review codes/types were absent or wrong. CSRF regression initially saw a null POST header; it now verifies decoded unsafe-request forwarding and no header on GET.
- Focused queue/detail/message/transport suite passed 30 tests before the later comment-length case.
- Frontend final full suite: 12 files, 78 tests passed with `npm run test -- --maxWorkers=2`, exit 0, no unhandled errors. Earlier default worker run: 76 passed, one existing ADMIN lazy-route timeout (BUG-002). Kept the route test and CI configuration unchanged; this is an explicit remaining CI timing risk.
- Backend `mvn verify`: 34 passed, zero failures/errors/skipped, BUILD SUCCESS, including Spotless. Test database review_verification is separate from live manual data. Log: backend/target/review-ui-verify.log (ignored temporary output).
- Format, lint, typecheck, production build passed. Regenerating the API client leaves its tracked directory unchanged.
- Real browser: localhost form login -> queue -> detail -> Approve ticket 1 -> Published. Reject ticket 2: blank Reason showed MSG01, valid reason saved Rejected and MSG16. Request changes ticket 3 saved Changes requested and displayed the comment, retaining its original deadline. Empty ticket 4 showed MSG20 and disabled Approve.
- PostgreSQL inspection of tickets 1/2/3 confirmed one decision and one audit record each. Reject/Request changes each have one undelivered notification; approval has none. All three stored deadlines remain 2026-10-23 15:39:58 UTC.
- Reusable real HTTP smoke runner passed all 18 checks, including authorization, atomic batch failure, invalid IDs and final rejection. It left a fresh user fixture batch 9b923d3b, IDs 73–78.
- Screenshot: backend/target/review-live-changes.png (local, ignored).

## Defects and outstanding bookkeeping

- BUG-006: queue bulk changes removed and detail single-ticket action tested. BUG-007: current review messages migrated and tested. BUG-008: Project filtering, URL state and filtered select-all tested. Their original ledger rows still need status updates.
- BUG-022: missing CSRF transport, fixed with named regression and verified by real browser decisions.
- BUG-023: initial rejection MSW fixture incorrectly exposed its reason as reviewComment; aligned fixture/tests with actual backend semantics (reason is saved in history).
- BUG-024: test login hostname inconsistency; standardized localhost and verified browser session end to end.
- BUG-025: privacy hook false-positive on a PowerShell map-property name in optional comment cleanup. That command was not retried or worked around. A subsequent attempt to append the defect ledger was also blocked because its diagnostic text repeated that expression. Formal bug-log additions/status updates remain pending user-side execution; this report does not claim the ledger was updated.
- BUG-026: Select all API failure previously escaped as an unhandled rejection without UI feedback. Named regression first failed with missing MSG06 and an unhandled ApiError; fixed error reporting while preserving the existing selection. Final 78-test run passes without unhandled errors.
- BUG-013 remains open: Harness trace does not discover Java tests/canonical IDs. Do not mark module/feature evaluating or passing by bypassing the trace gate.
- Final `sdd.ts check` and audit pass. Trace reports 18/30 after assigning AC IDs to the existing queue behavior tests; backend Java discovery and documentation evidence remain outside the tool's coverage. Feature/module statuses remain implementing/in-progress.
- Some historical feature comments still cite old message codes; optional comment cleanup was abandoned after the hook rejection. Runtime message calls and regression expectations use current review codes.
- Browser bulk confirmation additionally verified MSG19 for tickets 7 and 8, then both disappeared from the queue and selection reset to zero. Screenshot: backend/target/review-live-bulk-confirm.png.
- Production jar inspection confirms zero classes under vn/tsolve/ticket/manual; manual session/login tooling remains test-classpath only. Final whitespace check passes.

## Review and commit checkpoint

No commit/push/merge performed for Task 2. User agreement remains required for the feature commit. Proposed title: `feat(review): connect ticket review frontend to API (KAN-24)`.

Shared transport/catalog and directly affected kit example/test changes require the normal FE review before merge. The module path list was expanded to make these integration edits visible; the approved acceptance criteria were not changed.

## User-authorized commit and push checkpoint

- User explicitly requested commit and push after reviewing the implementation and its stated limitations.
- Code commit: default@6021e9c (`feat(review): connect ticket review frontend to API (KAN-24)`).
- Staged secret scan and whitespace checks pass. The defect notes and pending formal ledger synchronization remain explicit; blocked commands were not retried.
- Module/feature statuses are unchanged; implementation is not represented as passing the incomplete Harness trace gate.

## FE Lead D1/D2 correction (2026-10-22)

The single-ticket restriction and empty-Solution scenarios described in this historical report are superseded. Current approved spec uses batch Request changes with one shared comment and no empty-Solution review case. Report3 sources and D1/D2 replace the layout-template interpretation. Bug references above use canonical reconciled IDs. See task-5-fe-lead-amendments.md for current verification.
