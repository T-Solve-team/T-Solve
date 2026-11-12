# Task 19: Read a Jira project and its statuses (KAN-127)

**Module:** tracker
**Assignee:** Developer (Claude, supervising Codex CLI)
**Status:** in-progress
**Iteration:** 1

---

## Session 2026-11-12

### Working on: jira-project-read (STORY-013, AC-013.1 – AC-013.13)

Branch `feat/KAN-127-project-integration-add-project`. Read-only `TrackerProjectService.findProject(key)` for the
Project module: service account → `GET /rest/api/3/project/{key}` → `GET /rest/api/3/project/{canonicalKey}/statuses`
→ deduplicated `TrackerProjectLookup` (FOUND / NOT_FOUND / DENIED / UNAVAILABLE).

**Who wrote what.** The Codex CLI wrote the code test-first in two runs (`codex exec -s workspace-write --ephemeral`),
and Claude wrote the prompts, reviewed each diff against STORY-013 and `backend/AGENTS.md`, and ran every check.
- Codex run 1 (tests only): added 14 STORY-013 test methods to `JiraClientTest` and wrote
  `TrackerProjectServiceImplTest` (14 methods); existing comment tests untouched. Red confirmed with
  `mvn test-compile`: the only errors were the missing `TrackerStatus`, `TrackerProjectLookup`, `Outcome` and
  `TrackerProjectService` symbols.
- Codex run 2 (production code): `TicketTrackerClient` methods, `JiraClient.findProject` / `projectStatuses` /
  `requiredString`, `TrackerProjectService`, `TrackerProjectServiceImpl`, `TrackerProjectLookup`, `TrackerStatus`.
- Claude's edits: the step 2 prompt named the log wording the FOUND test expects and the `""`/`null` body case;
  after review Claude added the Javadoc on `TrackerProjectLookup` (null key and no statuses unless FOUND). Spotless
  reflowed the text blocks of the existing tests in `JiraClientTest` (formatting only). Nothing was reverted:
  both runs wrote only files on the task's list.

### Decisions
- Project 404 is caught only inside `JiraClient.findProject` (D38), so comment 404s stay `JiraRejectedException`.
- New `requiredString` helper requires a JSON string (`isTextual`) for key/id/name; the comment methods keep
  `requiredText`, whose `asText` accepts numbers, unchanged.
- An issue type without a `statuses` array, a non-array root, or an empty/`null` body is UNAVAILABLE (D35), not an
  empty list; only a valid `[]` is FOUND with no statuses (AC-013.9).
- `done` only for the exact category key `done` (case and spaces count).
- Logs: one line per lookup with the key, the outcome and the HTTP status (`200` on FOUND, `404`, the rejected or
  unavailable status, or none when there is no service account); never the account, an exception or a body.

### Files
- Modify: `backend/src/main/java/vn/tsolve/tracker/client/TicketTrackerClient.java`
- Modify: `backend/src/main/java/vn/tsolve/tracker/client/JiraClient.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/service/TrackerProjectService.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/service/impl/TrackerProjectServiceImpl.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/dto/TrackerProjectLookup.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/dto/TrackerStatus.java`
- Modify: `backend/src/test/java/vn/tsolve/tracker/client/JiraClientTest.java`
- Create: `backend/src/test/java/vn/tsolve/tracker/service/impl/TrackerProjectServiceImplTest.java`

### Verify
- `mvn -B test -Dtest='JiraClientTest,TrackerProjectServiceImplTest'` → Tests run: 74, Failures: 0, Errors: 0 —
  BUILD SUCCESS.
- `mvn -B spotless:apply && mvn -B -Dapi.version=1.44 verify` → unit (Surefire) `Tests run: 1420, Failures: 0, Errors: 0, Skipped: 0`;
  IT (Failsafe) `Tests run: 433, Failures: 0, Errors: 0, Skipped: 0`; `spotless:check` passed; `BUILD SUCCESS`.
- `bun harnessclaude/hooks/sdd.ts check tracker` → `spec tracker: 8 stories, 56 acceptance criteria — OK` (exit 0).
- `bun harnessclaude/hooks/sdd.ts trace tracker STORY-013` → `13/13 acceptance criteria have a test` (exit 0);
  it also warns `ids in tests but not in the spec: AC-001.72`, which comes from existing identity/common tests
  outside this task.

### Deviations
None from the task or design. The model session log named in the brief
(`05-implementation/workspace/task-1-create-department-api.md`) does not exist; this log follows
`templates/session-log.md`.

### Bugs
None found.

### Commit
default@aa966ab — `feat(tracker): read Jira projects and statuses (KAN-127)`, committed after the user agreed (12/11).

### Blockers
none

### Next up
Evaluate `jira-project-read` (Reviewer); project-integration Task 1 `project-done-statuses-schema`.
