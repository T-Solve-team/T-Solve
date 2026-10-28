# Task 1: Errors as ProblemDetail with a message code

**Module:** be-foundation
**Assignee:** Anh Tran Hai
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-28

### Working on: problem-errors (KAN-25, branch `feature/KAN-25-be-foundation-problem-errors`, from `main` acb6e79)
- STORY-001, AC-001.1–AC-001.9; design edge cases 3, 9, 10, 11, 12, 13. Fixes BUG-001.
- Jira: KAN-25 ("Backend errors must return ProblemDetail with an MSG code") was set Done on 21/10 without code;
  the user chose to reuse it (to be reopened in Jira by the user) instead of a new issue.
- Baseline checked first against the shared Drive `DocumentWorkGuide` (snapshot 25/10): MSG01/02/03/06/07/08/11/43
  texts and placeholders (`field_name`, `max_length`) match the spec; nothing to change.
- `git mv` of `NotFoundException`, `BusinessRuleException`, `GlobalExceptionHandler` from `common/error` to
  `common/exception`; the three callers change their import only. Spotless (ratchet from `origin/main`) reformats the
  whole of each touched caller, so `Solution.java` and `TicketService.java` show format-only lines.
- New `CodedException` (code, params, field; message-only constructor keeps `code` null) and `ProblemDetails`
  (`of`, `withCode`, `withFieldErrors`, record `FieldError`). Writing a body to a servlet response is left to Task 2,
  which needs it.
- `GlobalExceptionHandler extends ResponseEntityExceptionHandler`: `createResponseEntity` adds MSG06 to every Spring
  MVC `ProblemDetail` without a code (AC-001.8); `handleMethodArgumentNotValid` builds one `fieldErrors` entry per
  field, sorted by field name, precedence MSG01 > MSG02 > MSG03 > MSG06.
- Decision not written in the spec or design: the top-level `code`/`params` of a 400 validation body are those of
  its first `fieldErrors` entry (MSG06 when there is no field error), like the web app's mocks
  (`features/users/mocks/handlers.ts` `fieldError`).
- Decision: `@Size` gives MSG02 only when the value is longer than `max`; a value under `min` is "another rule"
  (MSG06), so it never shows "must not exceed".
- Palantir Java Format 2.43 cannot parse `case null, default ->`; `lengthOf` uses `instanceof` instead.
- Test: `backend/src/test/java/vn/tsolve/common/exception/GlobalExceptionHandlerTest.java` (standalone MockMvc, test-only
  controller, 17 tests; `answersNotFoundAsProblemDetailWithCode_AC_001_1_BUG_001` names the bug). It was red first
  (compile errors: no class in `common/exception`). The AC-002.5 test covers the dispatcher-side 403 only; Task 2 tests
  the filter chain.
- `docs/bug-log.md`: BUG-001 → Fixed, Test filled; `Fixed in` to be set with the PR number.
- Commits: `default@cf8607a` (move only, verified on its own: `mvn clean verify` BUILD SUCCESS) and `default@43d1506`
  (the feature). A linked git worktree cannot run Spotless (JGit finds no repository), so the move-only commit was
  built in the main checkout from a backup of the work.

### Verify
- `mvn clean verify` in `backend/` at 43d1506: "Tests run: 17, Failures: 0, Errors: 0, Skipped: 0", BUILD SUCCESS
  (Spotless check included).
- `sdd.ts trace be-foundation STORY-001`: "0/9" — the known gap in FEEDBACK.md (2026-10-25, 2026-10-28): `TEST_GLOBS`
  does not match `*Test.java`, and `AC_MENTION` only reads `AC-001.1`, which a Java method name cannot hold. Checked by
  hand instead: every AC-001.1 … AC-001.9 has at least one test method named `…_AC_001_<n>` (AC-001.4: 4, AC-001.8: 5).

### Blockers
none

### Next up
Push and the PR for KAN-25 (user's go-ahead), then the review of Task 1; then Task 2 (roles-security).
