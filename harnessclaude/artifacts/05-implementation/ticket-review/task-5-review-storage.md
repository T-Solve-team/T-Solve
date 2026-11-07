# Task 5: Shared UUID storage for Review

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-06 20:38

### Working on: review-storage

- Added Liquibase 008. Backfilled ticket UUIDs and remapped existing import decision references before replacing the ticket key. Preserved the external source key and Solution fields. Added Evidence and Review decision tables.
- Updated the shared Ticket entity and repository, Import Confirm, the import decision reference and the publication event. The event still delivers after commit and now carries UUIDs.
- `ReviewStorageIT` first failed on the old schema. The final run exercised the upgrade with existing ticket and decision data and inserted both new child record types.
- `mvn -B -Dapi.version=1.44 verify` passed 528 unit tests and 39 integration tests with zero failures, errors or skips. `mvn -B spotless:check` passed. Review trace found AC-009.9. The other nine STORY-009 criteria belong to Tasks 6 and 7. Import STORY-004 traced 13/13 criteria.
- Code commit: `default@dbfaea6`.

### Blockers

Production endpoints and Jira feedback follow Tasks 6 and 7.

### Next up

Evaluate the storage slice, then continue with the scoped Review API when its prerequisites are available.
