# KAN-158 test report

Date: 2026-11-01. Branch: feat/KAN-158-review-expired-tickets. Scope: STORY-007 / AC-007.1–007.8.

**Final result: PASS.** Parent standard-host full run exited 0: 1148/1148 tests passed, 0 failed, 0 pending (152 reported suites, 65 files). Persisted evidence: `frontend/.vitest/kan158-final-verification.json`; elapsed 286383 ms. No source writes, regeneration or other heavy checks overlapped this final run. Typecheck, format, lint and build also pass on the final source; API generation was deterministic. One existing TicketListFilters react-refresh lint warning remains, with zero lint errors. Feature commit is awaiting user agreement.

## Commands and evidence

The package scripts were executed through their local Node CLI equivalents because npm is not on this session's PATH; no script, test setting or assertion was weakened.

- Focused developer run: `node node_modules/vitest/vitest.mjs run src/features/review/pages/ExpiredTicketsPage.test.tsx src/features/review/mocks/expired-handlers.test.ts` — 12/12 passed (8 page + 4 mock).
- Parent format check: `node node_modules/prettier/bin/prettier.cjs --check .` — exit 0, all matched files formatted.
- Parent lint: `node node_modules/eslint/bin/eslint.js .` — exit 0; one pre-existing react-refresh warning in TicketListFilters.tsx, zero errors.
- Parent TypeScript/build: `node node_modules/typescript/bin/tsc -b` and `node node_modules/vite/bin/vite.js build` — exit 0.
- Parent generation: `node node_modules/orval/dist/bin/orval.mjs --config orval.config.ts` — exit 0; before/after SHA hashes of every generated file match. Repeated after the whitespace-only OpenAPI cleanup, again no generated changes.
- Criterion trace: `bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-007` — 8/8 criteria have named tests.
- Full-suite final run: `node node_modules/vitest/vitest.mjs run --reporter=json --outputFile=.vitest/kan158-final-verification.json` — exit 0, 1148 passed, 0 failed/pending.

## Verification incident

First parent full-suite run: 64 files passed, 1 failed to import, 1065 tests passed. Parent mistakenly overlapped Orval regeneration with the test runner; Orval cleans its output directory, so sign-in.test.ts hit ENOENT for generated model/projectMembership.ts. The file exists after generation. This is an orchestration error, not an established product defect. A fresh full-suite run was started after generation completed, with no overlapping source or generated-file writes.

The existing jsdom pseudo-element getComputedStyle warnings remain visible. No test was skipped, deleted or given a larger timeout.

Second independent full run (no generation overlap): 63 files passed, two files had one failed assertion each; 1146/1148 tests passed. Failures were the existing Ticket Detail safe-Evidence-reference initial lookup and Review Queue bulk approval confirmation. Both complete files and the two targeted cases pass unchanged when focused (detail target 787 ms, bulk interaction 3039 ms). All 58 pre-existing Pending review fixtures and the original detail UUID were preserved. Test/request synchronization is being investigated; no production regression or fix is inferred merely from a focused pass.

Follow-up: BUG-089 synchronizes the Evidence safety test with the real router, generated session query and generated detail query inside act before unchanged link/text/security assertions. BUG-090 synchronizes the existing bulk confirmation test with actual eligibility refresh and mutation settlement before unchanged success/count assertions. No fake cache records, production behavior, polling settings, assertions or timeout values were changed. Final focused run of both complete files: 29/29 passed (BUG-089 target 618 ms, BUG-090 complete interaction 11567 ms under concurrent suite load). These are test synchronization repairs; final full-suite confirmation remains required.

BUG-091: a later full run still showed page-one links after active-query refetch (1147/1148 passed). Refetch could target the old active page before the route observer changed. Final setup preloads genuine second-page data through generated ensureQueryData/MSW before navigation; unchanged individual/off-page and Back/reset assertions then exercise cached-server-data navigation. Server-side pagination remains independently covered by the contract test. Final focused page file passes; reviewer confirmed the setup remains meaningful without fake data or timeout inflation.

Restricted-runtime diagnostic with workspace TEMP/TMP and persisted `.vitest/kan158-full.json`: elapsed 320 seconds, 1141 passed, 5 failed, 2 pending due to a suite-hook timeout. Expired Tickets passed. Other failures involved existing Review Queue and User Status interactions and a 10-second hook timeout in date.test.ts. No source/config changes followed this diagnostic. A standard-host run with persisted `.vitest/kan158-full-host.json` was started to verify the unchanged suite in the environment used for the earlier 160–183-second runs. An earlier approval-review deadline was handled by using the restricted runtime; no approval gate was bypassed.

## Runtime and limits

See the companion review report for real-browser all-filtered/single-send, detail and due-date verification. Mobile AppShell limitation logged as BUG-088; production backend and durable expiry/history integration remain outside this frontend/MSW task.

Graphify refreshed to 7049 nodes and 14678 edges. Four SQL files were not indexed because tree_sitter_sql is not installed; the code graph was updated, while documentation semantic indexing requires a separate semantic update. SQL was not changed by this task.

## Final refinements and verification

BUG-092: the failed-mutation test's global zero-fetch assertion could race the intentionally asynchronous invalidations. It now awaits the actual mutation-cache terminal event, checks call-through automatic invalidations of required generated keys before any explicit synchronization, then awaits real keyed list and eligibility requests in separate act turns. Conflict pruning, retained selection, error and disabled-state assertions remain. The independent reviewer initially identified a possible weakened invalidation check; the call-through assertions resolved that concern. Focused KAN-158 result after these refinements: 12/12 passed.

Queue test action-cost evidence: per-character Comment typing took 1643 ms; unchanged focused Request Changes took about 10.4 seconds and timed out at 15073 ms in a full run. Equivalent enabled-guarded native clicks and controlled-input change events retain all validation/cancel/history/message/count assertions while removing unnecessary hover/keystroke overhead. Final focused queue: 10/10 passed; Request Changes 6059 ms, bulk 7829 ms. No production, timeout, worker or configuration changes were made. Independent review found no weakened interaction or outcome checks. The complete final run above confirms all related and unrelated tests pass.

Final Graphify code refresh: 7074 nodes, 14716 edges, 455 communities. SQL/document-indexing limitations above remain unchanged.

User preference recorded on 2026-11-01: future feature verification uses automated tests and required CLI checks; do not perform manual browser experiments unless the user asks. Historical browser evidence in this report predates that preference.
