# FE/BE review response — KAN-24, 23 October 2026

Baseline: feature `fa5e79c`, main `d6e42f1` (chatbot plan #50). Main conflicts are resolved locally; final implementation and fresh verification are recorded below. Revised review spec has 32 criteria and passed `sdd.ts check` with the existing STORY-002 failure-path warning. Spec approval was blocked by the confirmation hook; the user must run the approval command in their terminal.

## Findings and disposition

| Review item | Evidence / decision | Action |
|---|---|---|
| BE 1: no production access adapter | Confirmed. Existing approved AC-006.2 explicitly fails closed; identity and session provisioning were excluded by the user. | Keep the safe port. PR description now explicitly says API is unusable in production until be-foundation roles-security/me-session, identity and Project rights adapter are wired. Current upstream plans have no assigned Jira numbers; do not invent KAN-xx. |
| BE 2: unpublished detail | Confirmed Project-level check exposes every state. User chose PM managing Project or author only for unpublished content; other readers Published only. | AC-005.7 added; BUG-033. Keep getPublished as the downstream integration contract and use its restriction for ordinary readers. Tests must cover each unpublished status, author, PM, other reader and foreign Project. |
| BE 3: two ticket routes | Confirmed distinct entities/ID namespaces. Renaming the agreed API would break the existing consumer without solving the missing source mapping. | Documented existing routes and planned source-ticket mapping/consolidation by ticket-repository in spec/design/PR description. No alias or migration guessed. |
| BE 4: unbounded batch | Confirmed, stable sorted lock order exists but no cap. User explicitly chose max 500, preserve all selected IDs, block decision actions over limit. | AC-005.8 added; BUG-034. Contract first, all three DTOs plus internal service validation before locks; FE guidance and blocked actions; test 500 accepted / 501 rejected with no mutation. No auto-chunking or transaction weakening. |
| BE 5: notification consumer missing | Confirmed, expressly outside original scope (AC-006.3/006.4). | PR description explicitly calls this an unconnected outbox with no production consumer/delivery/retry; pending is not delivered. |
| BE 6: null Evidence | Confirmed unconditional List.copyOf(null) throws. | BUG-035; normalize only absent list to immutable empty list; preserve defensive copy and five-file validation. |
| BE 7: five Evidence max only in Java | Constructor is the sole supported writer and enforces the maximum. | Accept per review; preserve applied migrations. No speculative database trigger. |
| BE 8: legacy not-found codes | Confirmed legacy services use MSG06; review uses MSG04, matching catalog No data found. | BUG-037; normalize legacy not-found codes with scoped owner-aware changes and tests, outside review trace ownership. |
| BE 9: ProblemDetail compatibility | Shared handler change is deliberate under backend rules and earlier user approval. Source search found no frontend/Jira-app caller of legacy /api/tickets or /api/solutions and no error.body.message dependency. | Keep standard ProblemDetail; document external legacy clients, if any, must consume status/code. Source inspection cannot prove absence of clients outside this repo. |
| FE 10: Project labels | Current session ProjectMembership has jiraProjectKey but no display name; queue/detail ProjectRef has name. | Keep key as the stable filter label and name in detail/table; do not fabricate a name or change the shared session contract merely for typography. Note for FE Lead if uniform display is mandatory. |
| FE 11: forbidden queue link | Confirmed unconditional link on TicketDetailPage. | AC-006.7, BUG-036; role-gated queue navigation with a non-PM regression. |
| FE 12: import order | Swapped sibling imports in ReviewQueuePage, no runtime effect. | Apply established import order while editing and verify ESLint, without changing lint configuration. |
| Module 1: review package | Review subpackages currently share vn.tsolve.ticket with legacy snapshot classes. | Move only review code/tests/manual harness to vn.tsolve.review; retain legacy root ticket classes and every applied schema identifier. Check all Java imports and launch commands. |
| Module 2: trace ownership | Old paths included several other modules' tests and implementation. | module.md narrowed exactly to review code/tests, review feature and named shared contract paths. Move decision tests to TicketDecisionActions under review; retain pure page display/error tests with their owner and avoid claiming their AC IDs. |
| Module 3: context/map | Previous branch appended review-specific content to team context/map. | Restored both files exactly from current main; no review-specific replacement or deletion of team documents. |
| Module 4: state/approval conflicts | Main has no ticket-review entry; branch approval history must not be hand-merged. | Restored progress.json and approvals.json exactly from main, then registered ticket-review decomposed via sdd.ts. Await new spec approval, then replay planned/implementing and feature registration via commands. Other modules' state/approvals remain main's. Event history preserves exact records from both branches. |

## Implementation gate and verification plan

Changes to AC-005.7, AC-005.8 and AC-006.7 require fresh spec approval. The hook message explicitly requires human terminal execution and forbids retry/workaround; no module source files have been edited under the new spec.

After approval: transition module through planned/implementing, register lead-review-fixes, write named red regressions, implement the minimal changes above, then run PostgreSQL Maven verify/Spotless and full frontend format/lint/typecheck/test/build plus unchanged API regeneration. Refresh Graphify and check both conflict markers and main tracking preservation. Log every new defect even if fixed.

Last completed verification remains the prior revision: 42 backend tests and 414 frontend tests; it is not evidence for the pending amendments. No commit/push or GitHub PR-body edit performed in this amendment pass. The user previously chose to paste the prepared PR description themselves.

## Implementation update after human approval

The user approved spec version 1231e5d2 and opened the five-file cross-module quick change. Module status and feature registration were replayed through sdd.ts (planned, implementing; lead-review-fixes in-progress). No approval/progress file was hand-merged.

Moved review main/test/manual packages to vn.tsolve.review; legacy root ticket snapshot classes remain. Applied AC-005.7, batch 500 DTO/service/contract/FE checks, null Evidence normalization, legacy MSG04 and PM-only queue navigation. Moved the original review-decision integration tests into review/components/TicketDecisionActions.test.tsx; TicketDetailPage tests now cover its display/error states without claiming review AC IDs. Removed fe-foundation's temporary review-folder claim on handoff (BUG-038); trace reports no ambiguous review IDs.

Red evidence: backend targeted run 39 tests, 10 failed and 1 errored for the new findings; frontend 501 test and navigation test failed before fixes. The initial navigation fixture used Employee, which is already denied the detail route; corrected it to the permitted non-PM Ticket Resolver before verifying the actual forbidden-link assertion. A Maven invocation initially lacked SERVER_PORT and was rerun with full test configuration; a sandbox Maven launch was interrupted and rerun outside the sandbox. Ignored logs live in backend/target/lead-review-*.log.

Green evidence so far: frontend format/lint/typecheck/build, focused 22 tests and full 26 files/418 tests pass. Maven full run has 58 tests, 3 failures, no errors/skips; all three failures are the new BUG-039: shared validation hard-codes root MSG01 while ticketIds fieldErrors correctly contain MSG06. Service checks reject 501 before locks, and all new access/Evidence/legacy regressions pass.

The BUG-039 shared handler fix and a dedicated shared test require a replacement two-file quick change; the confirmation hook blocked the command and instructed human terminal execution. Those two files have not yet been edited. No workaround or duplicated review-only exception handler was added. Full green backend, updated HTTP smoke and final commit gate remain pending that scope. No new feature commit or push has been made.

Scope/risk review: no new dependency/migration, no edited applied changeset, no auth fallback, no notification delivery claim and no automatic splitting of all-selected transactions. Missing production auth/mail remains explicit in the PR description. The Project key filter/name display remains intentional under the current session contract, which contains no display name.

## Final verification after BUG-039 scope approval

The human opened the replacement quick change for GlobalExceptionHandler and ValidationProblemDetailTest. Five dedicated regressions failed before the fix. The shared handler now normalizes field errors once, uses the first field's validated code/params in the root ProblemDetail, preserves every field error and falls back to MSG06 with no field errors. No module-specific handler or validation bypass was introduced.

- Backend: `mvn -o spotless:apply verify -B` on Java 21/PostgreSQL 16, **63 tests, 0 failures/errors/skips, BUILD SUCCESS**. Evidence: `backend/target/lead-review-verify.log` (ignored).
- Frontend: format:check, lint, typecheck, build pass; **26 files, 418 tests pass**, no unhandled errors. Focused review/page suite: 22 tests pass. Existing jsdom pseudo-element warnings remain visible.
- API generation repeated after tests: every generated file SHA-256 unchanged. OpenAPI maxItems=500 is declared on all three mutation schemas; generated output is produced only by orval.
- Live backend HTTP smoke: **25/25 passed**, including unauthorized unpublished reads, owning PM reads, Published reader access, oversized Approve/Reject/Request Changes returning 400 MSG06, CSRF, all review mutations, foreign Project denial, rejected-state finality, and rollback of a mixed unauthorized batch. Relocated manual launcher starts successfully at loopback port 8080; fixtures remain for manual walkthrough. Evidence: `backend/target/lead-review-http-smoke.json` (ignored).
- Local conflict list empty; diff whitespace check and sdd.ts audit pass. Main context/map are byte-equivalent after newline normalization; all other modules' progress and approvals match main. Review changes were replayed through commands and human approval.
- Trace: 19/32, **no ambiguous ids**. The remaining framework Java discovery/AC-name limitation is BUG-015; documentation criteria also remain review evidence rather than invented code tests. Module is not marked completed and no trace gate was bypassed.
- Graphify refreshed: 1,895 nodes / 4,261 edges. Two SQL files remain unindexed because tree_sitter_sql is missing; SQL source was directly inspected. Graph output stays local.

BUG-033…037 and BUG-039 are Fixed with named regressions; BUG-038 ownership ambiguity is Fixed by the documented folder handoff and trace check. Production ReviewAccessService adapter and mail consumer remain intentionally unimplemented integration prerequisites and are explicit in the prepared PR description. Both routes retain distinct ID namespaces. No changed applied migration or new dependency. No fresh browser interaction is claimed for this amendment; frontend behavior is covered by real-route integration tests and backend behavior by live HTTP smoke.

Ready for the user's commit checkpoint. Proposed code/merge message: `fix(review): address FE and BE review findings (KAN-24)`, followed by a separate harness evidence commit. No commit or push has been performed for this pass. GitHub conflicts remain visible until those commits are pushed.

## Commit checkpoint and final contract correction

User requested commit and push. Code/merge commit: `default@19485d9` (`fix(review): address FE and BE review findings (KAN-24)`), through Gitleaks, lint-staged and commitlint. It includes main d6e42f1 conflict resolution; no code hook was bypassed. Closed the committed two-file quick change through sdd.ts; retained module implementation state and the documented trace limitation.

Pre-commit inventory discovered BUG-040: the YAML maxItems change previously described above had not been applied because the text replacement missed indentation. Corrected all three request schemas and regenerated the three maxItems comments through orval. Added `declaredAndServedBatchLimitsAgree_AC_005_8_BUG_040`, which checks both the declared YAML and served OpenAPI for every action: red on missing declared limit, then **full Maven verify 64/64 passed**. Repeated API generation leaves SHA-256 unchanged; frontend format passes. Prior frontend 418/418 and live HTTP 25/25 remain applicable (final generator changes only comments). The earlier 63-test count is superseded by 64 and the earlier YAML claim is corrected here.

Graphify final refresh: 1,896 nodes / 4,248 edges; same missing SQL extractor limitation. Companion harness commit records the approved revised spec, narrowed ownership, replayed tracking, named defect evidence and updated PR body for the user's chosen manual paste workflow. Push targets only feat/ticket-review.
