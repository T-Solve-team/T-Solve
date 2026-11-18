# Task 27: Unpublish / re-publish on the backend

**Module:** ticket-repository
**Assignee:** dang minh
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-15

### Working on: ticket-publication
- `TicketDecisionService.unpublish` / `republish` through one private `changePublication` in `TicketDecisionServiceImpl`; two `@PostMapping`s in `ReviewDecisionController`; `ReviewDecisionRecord.Decision` + `UNPUBLISH`, `REPUBLISH`.
- Changeset `037-review-decision-publication.sql` (034-036 were taken on `main`), with rollback.
- Deviation 1: the design relied on `TicketTransitions.move` alone for AC-009.3. `PENDING_REVIEW -> PUBLISHED` is an allowed transition, so re-publish of a Pending review ticket would have published it without a review (caught by `republishRefusesAMixedBatchWithAnyOtherStatus_AC_009_3`). `changePublication` now checks the source status (Published for unpublish, Unpublished for re-publish) and throws MSG06 before `move`.
- Deviation 2: `backend/src/test/java/vn/tsolve/review/ReviewStorageIT.java` (module `ticket-review`) lists the changesets that touch Review tables; `037-review-decision-publication` was added to that list, as 024 is. Without it the IT of BUG_145 fails (`relation "review_decision" does not exist`).
- P9 checked: every reader of `review_decision` filters by decision (`ReviewDecisionRecordRepository` history queries, `DashboardRepository`); the writer-notification query runs only for `ReviewFeedbackDecidedEvent`, which these decisions do not publish (asserted in `TicketPublicationIT`).
- Tests: `TicketPublicationTest` (15), `TicketPublicationIT` (6, real `ProjectMembershipReviewAccess`; a Project that is not Active answers 403, D37).
- Verify: `mvn verify` 2500 unit tests passed, 770 of 771 ITs passed; the one failure (`ReviewStorageIT`) was fixed and rerun with `TicketPublicationIT` and the Spotless check: BUILD SUCCESS. The full `mvn verify` was not run again after that fix.
- Environment: the machine has JDK 24 only (`JAVA_HOME` names a missing jdk-21); Maven ran on JDK 24 with `JAVA_TOOL_OPTIONS=-Dnet.bytebuddy.experimental=true`.
- `sdd.ts trace ticket-repository STORY-009`: 10/10 acceptance criteria have a test.
- Commit: default@da60828

### Blockers
none

### Next up
Task 28

## Session 2026-11-15 (after review attempt 1)

### Working on: ticket-publication — informational review findings
- `TicketPublicationIT` (D37 case): Project status `REMOVED` instead of `INACTIVE`, which is not a `ProjectStatus` value.
- Changeset 037 rollback: deletes `UNPUBLISH` / `REPUBLISH` rows before restoring the four-value check. Replayed changeset + rollback on a throwaway pgvector:pg16 container: ok.
- Verify: `TicketPublicationTest` 15/15, `TicketPublicationIT` + `ReviewStorageIT` 10/10, Spotless check: BUILD SUCCESS.
- Commit: default@92b1e9d

### Next up
the design vs frontend guideline 01 question (Task 28) is open for the user / FE Lead
