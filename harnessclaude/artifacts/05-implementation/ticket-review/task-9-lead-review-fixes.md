# Task 9: Resolve main conflicts and rejected-decision recovery

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-10-24

### Working on: lead-review-fixes

Spec: `artifacts/03-modules/ticket-review/spec.md`.

- Incoming main is e681f9a; original branch head f31b5a4. Merge is local and uncommitted. Published history is preserved; no rebase/force-push or merge into main. Conflict entries in OpenAPI, bug log and events are resolved; no unresolved index entries remain.
- Preserve chatbot paths, root tag, `UnavailableDocuments`, generated endpoint and feature `useUnavailableDocuments`. The generated hook is named `useListUnavailableCitedDocuments` by operationId; the feature wrapper remains `useUnavailableDocuments`. Retain both review schemas and main chatbot additions.
- Main's seven bug rows are exact. User explicitly requested consecutive branch IDs after main's highest; remap branch BUG-007…041 to BUG-008…042 and update references. Main IDs are never reassigned. New BUG-043…050 retain full-name attribution. Events merged by provenance; no manual progress/approval JSON edits.
- Refresh queue, complete eligible-ID query and affected details on `onSettled` for all three decisions. Selection reconciliation uses complete IDs, preserving off-page selections. Project URL scope keys local selection/dialog state so Back/Forward resets it. Shared batch constant mirrors OpenAPI maxItems 500. Drop unused MSG17 count, duplicate page rendering/error coverage and self-mock due-date assertion.
- New tests first failed before the fix. Full run after implementation found BUG-050: the new 409 workflow rendered the 20-row fixture repeatedly and exceeded 15 seconds. Narrow its fixture to its two participating tickets without raising timeouts or dropping payload/state assertions. First full run: 480 passed / one timed out. Next full run: **29 files, 481 passed**, no unhandled errors; known jsdom pseudo-element warnings remain.
- Final frontend checks: **format:check, lint, typecheck, build, test pass**. Repeated `generate:api` leaves the entire generated file set and SHA-256 hashes unchanged. No hand-edited generated code. Canonical bug rows, consecutive IDs and conflict-marker absence verified programmatically.
- Unstaged `git diff --check` passes. The staged merge reports an existing trailing blank line in main's `chatbot/task-2-citations.md:72`; main's chatbot artifact is preserved unchanged. This is separate from the passing frontend Prettier check.
- Updated spec first for canonical KAN-31 ticket/history and foundation audit, trusted writer identity, reachable MSG18 validation and clean-environment tests. User approved the direction and explicitly chose trusted user IDs rather than matching free-text writer names. Design/tasks record dependency gates and forward migration requirements. No backend integration implementation or clean-machine Maven success is claimed.
- `sdd.ts check ticket-review`: 6 stories / 35 criteria, pass with the existing STORY-002 failure-path warning. Approval is now stale by design. Standalone approval command was blocked by the hook: Codex cannot confirm the user's spec approval. Required user terminal command: `bun harnessclaude/hooks/sdd.ts approve ticket-review`. Do not retry or bypass it.
- Graphify refreshed: 2,071 nodes / 4,592 edges. SQL indexing remains unavailable because `tree_sitter_sql` is missing; reviewed SQL directly. Local graph output is untracked and excluded from commits.
- No commit or push in this session. Proposed feature message: `fix(review): refresh rejected decisions and preserve chatbot contract (KAN-24)` with STORY-005 and normal AI trailer; companion harness changes remain separately scoped.

### Blockers

1. Backend integration: KAN-31 is an unmerged branch and be-foundation is planned. Required canonical locking/history service contract and exact existing-review-to-canonical identity/history mapping are not available. No copied entities, competing store or destructive migration workaround was introduced. BUG-045…048 stay Open. Backend CI and isolated Testcontainers verification remain pending.
2. Updated spec requires human terminal approval before further module code. Cross-module service/POM/test-config/CI changes additionally need their normal scoped gate/owner review; no blanket expansion is assumed.
3. PR description update: user expressly authorized replacing PR #26 with the prepared complete body. GitHub connector returned **403 Resource not accessible by integration**; remote description is unchanged. `gh` is not available on PATH. The full body remains in `task-5-pr-description.md`. Automatic review initially required renewed authorization because the user previously preferred manual paste; that authorization is now granted, but does not resolve the GitHub integration's missing permission.
4. No current browser/screenshot verification, clean-machine backend pass or production auth/mail delivery is claimed. Remaining nonblocking nits (foreign Project display fallback, evidence size, relative-URL CSRF comment) are pending their owners/scope. No formal re-review requested.

### Next up

Record the current feature's commit agreement, obtain spec approval, then implement Task 10 only against reviewed dependency interfaces and migration mapping. Re-run combined backend verification before merge; update PR metadata once GitHub write permission is available or use the prepared body manually.

## Continuation 2026-10-24

- Human ran approval successfully: `sdd.ts diff ticket-review` confirms the current 35-criterion spec matches approval. The approval blocker above is resolved; no approval command was retried or bypassed by Codex.
- Human explicitly authorized the verified conflict/FE recovery commit. Created **default@3d9f65b** through Gitleaks, lint-staged and commitlint. Incoming main harness files belong to the merge; branch-owned spec/design/log/approval changes are recorded in the companion harness commit. No push.
- `sdd.ts commit ticket-review default` records 3d9f65b. `sdd.ts trace ticket-review STORY-001 STORY-005 STORY-006` exits 1: **10/23** criteria traced. Existing Java detection limitation is BUG-015; new canonical/audit/clean-test criteria and external documentation/delivery obligations also remain incomplete. Do not promote the module or call the full acceptance gate green.
- A fresh fetch finds main has advanced to eccb9c9 (chatbot ratings plan, KAN-75). The committed code/FE verification above is against e681f9a. No backend foundation implementation branch exists in the fetched refs; KAN-31 still exposes only import services/ProjectAccess, with no canonical review queue/batch-lock/status/history service. TicketReviewDecision currently only exposes an approve factory. Task 10 requires these dependency contracts; approval alone does not supply them.
