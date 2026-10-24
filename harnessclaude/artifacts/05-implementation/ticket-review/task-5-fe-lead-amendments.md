# FE Lead D1/D2 amendments

Date: 2026-10-22. Human owner: Phạm Tùng Dương. Work performed sequentially without subagents. Feature: review-lead-amendments, still in-progress pending the per-feature commit checkpoint.

## Contract and sources

- Updated spec first in Change Mode: AC-004.1/004.2/004.4 changed; AC-002.4 removed and never reused. Changelog records D1/D2. sdd.ts check reports 6 stories, 29 AC, OK with one warning about STORY-002 having no story-local failure criterion; selection/state failures remain in STORY-005.
- User renewed approval at 2026-10-22T13:47:03.377Z, hash 5d4de2285469e55a8ceef828fb6913b2d2fb514c0144575e6154ce9eaffd5c79, before application changes.
- Verified the actual team Report3 UC list, Business Rules and System Messages from OOXML. Context brief/spec point there; the portable manifest source index replaces the personal path. D1/D2 override source wording awaiting correction. Historical reports explicitly mark the previous template interpretation as superseded.
- Master/iteration design and tasks now agree: batch Request changes, no empty-Solution review case, shared exception handler, trusted submission/auth ports and durable email handoffs.

## Bug reconciliation

- Restored main BUG-001 and BUG-002 rows byte-for-byte, including original people, statuses and #34. Route test is BUG-002 again. Main BUG-001 remains Open exactly as requested, although this branch includes shared-handler regression evidence.
- Branch-only bugs now run consecutively from BUG-008, with simultaneous remapping in tests, source comments and harness references. Canonical main history retained; branch event references reconciled by provenance. Added five defects previously recorded only in Task 2 (now BUG-024..025).
- BUG-008 is Won't fix under D1 / FE-34. Review message/project-filter defects BUG-009/005 are Fixed with named test evidence. Review Found by/Owner are Phạm Tùng Dương as supplied by the user; main records retain their original names.
- Known BUG-015 (Java trace), BUG-027 (privacy hook) and BUG-029 (default-suite timing) remain Open. The latest passing run does not pretend to fix those defects.
- Newly observed BUG-030 records Ticket Detail date wrapping in a narrow browser viewport. Existing responsive layout was not changed; no fix claimed.

## Implementation

- OpenAPI first: RequestChangesRequest takes ticketIds plus one comment; client regenerated only by orval. Queue restores Request Changes for selected/all-page IDs; detail sends a one-element array. Success invalidates queue, ID selection queries and every affected detail query.
- Backend uses the existing ordered row locks and validates the complete batch before mutation. Same comment, one decision/audit/notification per ticket, unchanged due dates/Solution/Evidence. Invalid source or batch member rejects the entire request.
- Removed MSG20 catalog entry, empty-Solution approval warning/disable logic, empty-Solution predicate and backend approval check. Removed obsolete empty-Solution test scenarios/fixtures; populated Solutions are supplied by the trusted submission integration under D2.
- Catalog comparison against main confirms only MSG13..MSG20 definitions changed; all other catalog definitions are unchanged. No new dependency, schema or production security configuration change.

## Verification

- RED backend: batch contract test expected 200 but got 400 before the request DTO/controller change.
- RED frontend: modal test expected ticketIds [7,8] but received a comment without selection before the prop/payload change.
- Focused backend: 29 pass. Full final mvn spotless:apply verify: 39 pass, zero failures/errors/skips. Includes HTTP batch persistence, per-ticket comments/history/audit/notifications, due-date/Evidence preservation, invalid source, rollback for both Reject and Request changes, and concurrency.
- Frontend focused: 4 files, 24 pass. Full default Vitest: 12 files, 78 pass, no unhandled errors. Known jsdom pseudo-element warnings remain. format:check, lint, typecheck and build pass.
- Repeated orval generation yields an identical SHA-256 for every generated file/path. No hand-edited generated code.
- Real localhost HTTP harness: 19 checks pass, including two-ticket Request changes with count 2, same comment and separate records. Test-only harness uses isolated review_manual database; production auth/mail implementations are not simulated.
- Browser on real FE/API: selected tickets 21/22, opened one form and submitted one comment. Both disappeared from queue, selection reset; detail shows Changes requested and saved comment. Database confirmed both IDs with identical comment and exactly one decision, one audit and one pending notification each. Form screenshot: backend/target/d1-batch-form.png; result screenshot: backend/target/d1-batch-result.png.
- Framework trace: 17/29; known Java discovery/naming limitation remains BUG-015. No trace gate bypass or completed-module claim. Harness audit passes.
- Verification helper corrections: initial Prettier pathspec was relative to the wrong directory; corrected without application changes. A catalog-inspection helper initially used the regex capture list incorrectly; corrected and the scope assertion passed. These command mistakes are not treated as product fixes.

## PR description and checkpoint

Prepared task-5-pr-description.md using the repository What & why / How to test template and actual evidence. GitHub update endpoints returned 403 Resource not accessible by integration; browser is not signed in. User chose to paste the prepared body manually. No comment sent, review resolved, PR merged or permissions changed.

User approved the code commit. Recorded as default@61ca98e: fix(review): apply FE Lead batch review decisions (KAN-24). The commit passed staged secret scanning and diff checks. Tracking was recorded through sdd.ts commit, followed by a separate harness commit. These amendments remain local/unpushed; the user chose to paste the prepared PR body manually.
