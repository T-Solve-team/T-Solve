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

---

## Session 2026-10-29

### Working on: merging `main` (19e7c51) into KAN-25 — PR #82 had conflicts
- `main` brought KAN-31 (import-ticket-list), which refactored the old `common/error/GlobalExceptionHandler` to
  `ProblemDetail` in parallel and added `common/error/MessageCodeException` (status, code, params), extended by the six
  `ticket/exception/*` classes. Two `@RestControllerAdvice` beans named `globalExceptionHandler` cannot both stay.
- Two of its behaviours broke the approved spec: a message-only `NotFoundException` answered 404 MSG04 (AC-001.6 said
  no code) and `MaxUploadSizeExceededException` answered 413 MSG28. The servlet's 10 MB limit fires before the
  service's own size check, so MSG28 for a too-large CSV comes only from that mapping.
- The user chose option A (29/10): keep both behaviours through a spec change. Spec: AC-001.6, AC-001.8, AC-001.9
  reworded; new AC-001.10 (NotFound without code → MSG04), AC-001.11 (`MessageCodeException`), AC-001.12 (413 MSG28);
  design decision row and file tree; tasks.md Task 1. `sdd.ts check` OK; re-approved (spec version ac914d1f).
- Code: `MessageCodeException` moved to `common/exception` unchanged; `GlobalExceptionHandler` gains
  `handleMessageCode`, MSG04 for a code-less `NotFoundException`, and `handleMaxUploadSizeExceededException` → MSG28;
  `common/error` deleted (handler and its test; their cases are covered by AC-001.6/10/11/12 and AC-002.5 tests).
  The twelve `ticket/` files that imported `common.error` change their import line only (AC-001.9).
- Other conflicts: `docs/bug-log.md` takes main's padded table with every row (BUG-025, -029…-031 kept) and BUG-001
  → Fixed, Jira KAN-25, Fixed in #82; `artifact-index.md` keeps both sides' rows; `ticket/TicketService.java`
  deleted (main retired it).
- Spotless cannot run in a linked worktree (JGit). Workaround with the same effect as `ratchetFrom origin/main`: a
  temporary copy of `pom.xml` without `ratchetFrom`, run with `-DspotlessFiles=` listing exactly the Java files changed
  since `origin/main` (dot-separated regexes; `mvn.cmd` eats backslashes). Checked that it catches a misformatted file.

### Verify
- `mvn -f .pom-noratchet.xml clean verify "-DspotlessFiles=…" "-DargLine=-Dapi.version=1.44"` in `backend/`:
  BUILD SUCCESS, 107 Surefire + 6 Failsafe tests, 0 failures; `Spotless.Java is keeping 21 files clean`. Without the
  `argLine`, `ImportConfirmIT` cannot reach Docker Engine 29 (BUG-058, logged Open; Testcontainers 1.19.8).
- `sdd.ts trace be-foundation STORY-001`: 0/12 (the known gap); manual trace 12/12.
- Independent review (attempt 1): **pass** — `artifacts/06-evaluation/be-foundation/task-1-problem-errors-review.md`.
  Informational 2 fixed after the review: the AC-001.1 test now throws with a non-empty `params` and asserts it
  (`GlobalExceptionHandlerTest` 20/20, Spotless clean). Informational 1 (400 from parameter-level method validation
  has no `fieldErrors`) is outside STORY-001 as written; left for the user to decide.
- Feature `problem-errors` → passing. Merge commit: `default@70d72a9` (parents b6ad936, main 19e7c51).

### Blockers
none

### Next up
Commit (user's go-ahead), push to the KAN-25 branch when asked, so PR #82 merges cleanly; tell Minh (KAN-31) that
twelve `ticket/` files changed their import only. Then Task 2 (roles-security).

---

## Session 2026-10-30

### Working on: PR #82 review by bluebearccc (10 findings, Auto-fix)
- Fixed, each test-first and logged in `docs/bug-log.md`:
  - [1] BUG-061: the catch-all made an `AuthenticationException` a 500. The advice now rethrows it, so
    `ExceptionTranslationFilter` answers it through the entry point.
  - [2] BUG-062: an anonymous user's `AccessDeniedException` answered 403 MSG08. It is rethrown when the Authentication
    is anonymous (the same test `ExceptionTranslationFilter` makes); a signed-in user still gets 403 MSG08.
  - [10] The tests of [1] and [2] put `ExceptionTranslationFilter` (entry point 401) in front of the advice in
    standalone MockMvc; Task 2 adds the real filter chain and its entry point body (MSG07).
  - [5] BUG-063: `@Size` from a Spring `Validator` (no constraint behind it) made the handler throw; now MSG06.
    Arrays are measured with `Array.getLength`.
  - [6] BUG-064: the top-level `code` of a 400 is the most important field error (MSG01 > MSG02 > MSG03 > MSG06), no
    longer the alphabetically first; `fieldErrors` stays sorted by field name. This replaces the 28/10 decision
    "first `fieldErrors` entry".
  - [8] `MessageCodeException extends CodedException` (adds `status` only); `handleMessageCode` uses `coded()`. Its
    message is now the code alone (was "code params"); nothing reads it. [9] `handleNotFound` reuses `coded()`.
- Left for the user (they change approved decisions): [3] the 500 log line logs the whole exception (design
  "Decided in planning", AC-001.7 "log the exception") vs `backend/AGENTS.md` §9 "never log personal data";
  [4] `HandlerMethodValidationException` (400 without `fieldErrors`) and `ConstraintViolationException` (500) — the
  same point as the 29/10 review's Informational 1; [7] a message-only `BusinessRuleException` answers 409 without
  `code` (AC-001.6, approved 29/10) vs `backend/AGENTS.md` §5 "with a `code` property".
- The reviewer is recorded as "bluebearccc (PR #82 review)" in the bug log: their full name is not known here.

### Verify
- `mvn -f .pom-noratchet.xml clean verify -DskipITs "-DspotlessFiles=…"` in `backend/` (the worktree workaround of
  29/10): 113 unit tests, 0 failures (`GlobalExceptionHandlerTest` 26/26); Spotless keeps 21 files clean. The
  Failsafe ITs were not run: Docker Desktop is not running (and BUG-058 needs `-DargLine=-Dapi.version=1.44`).
- The worktree's `core.hooksPath` points at `.husky`, whose pre-commit cannot start here ("Exec format error") and
  needs gitleaks. The user allowed (30/10) committing with `core.hooksPath` set to an empty folder, as in the main
  checkout where no hook runs; commitlint did not run, so the headers were checked by hand.
- Commits, each verified on its own (unit tests + Spotless): `default@3e4c169` ([1], [2], [10]), `default@092c0b6`
  ([5]), `default@8327ee0` ([6]), `default@fd5d5cd` ([8], [9]).

### Next up
The user's choice on [3], [4], [7]; push, a one-line reply per fixed thread, resolve them.

## Session 2026-10-30 (later)

### Working on: review findings [3], [4], [7] — Change Mode
- The user chose the proposals (30/10). Spec first: AC-001.6 and AC-001.7 reworded, new AC-001.13; design rows "Other
  validation sources", "500 body and log", "Message-only `BusinessRuleException`"; tasks.md Task 1. `sdd.ts check` OK;
  re-approved (spec version 0879bacf). `sdd.ts diff` does not show the AC-001.7 change (only first lines are compared,
  FEEDBACK 2026-10-25), so it was named to the user before the approval.
- Decided while planning: the 500's id travels in RFC 9457 `instance` (`urn:uuid:<id>`), already in the contract, so
  no contract change; a parameter's `field` is its name, a service violation's the last named node of its path.
- [7] `coded()` gives a message-only exception MSG06 (a `NotFoundException` then still gets MSG04).
- [3] ERROR: `Unhandled exception <class> id=<id>`, no stack trace; DEBUG: the same id with the exception. The test
  reads the handler logger's events through a logback `ListAppender` (no console capture any more).
- [4] `handleHandlerMethodValidationException` (Spring 6.1.14 has no `ParameterValidationResult.unwrap`: the
  constraint is the last code and `@Size`'s max the second argument, attributes sorted by name; `ParameterErrors`
  keep their field errors) and a `ConstraintViolationException` handler (400, no detail). The three sources share
  `withValidationErrors` (one entry per field, precedence) and `fromConstraint`.
- Commits: `0c1f652` (spec, design, tasks, approval), `default@76bb33e` ([7]), `default@acc9a95` ([3]),
  `default@b7b546d` ([4]); hooks bypassed as allowed above.

### Verify
- Each code commit: `mvn -f .pom-noratchet.xml clean verify -DskipITs "-DspotlessFiles=…"` BUILD SUCCESS; at b7b546d
  116 unit tests (`GlobalExceptionHandlerTest` 29), Spotless keeps 21 files clean. ITs not run (Docker Desktop off).
- `sdd.ts trace`: still the known Java gap; by hand every AC-001.1–AC-001.13 has a test named `…_AC_001_<n>`.

### Next up
Push; a one-line reply on threads [3], [4], [7] and resolve them; a CODEOWNER's review; then Task 2 (roles-security).
