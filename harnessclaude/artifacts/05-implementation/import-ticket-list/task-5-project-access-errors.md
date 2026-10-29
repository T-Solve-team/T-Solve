# Task 5: ProjectAccess stub and ProblemDetail errors

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 18:00

### Working on: project-access-errors
- `ticket/service/ProjectAccess`: `managesActive(user, projectId)` and `isActive(projectId)`.
- `ticket/service/impl/StubProjectAccess`:
  - reads `tsolve.stub.projects` (`alice:1,2;bob:3`) and `tsolve.stub.inactive-projects` (`2,5`);
  - fails closed: with an empty setting or an unlisted user, nobody manages anything; a malformed id grants nothing; an inactive Project is not managed.
- `common/error/GlobalExceptionHandler` now returns RFC 9457 `ProblemDetail` everywhere:
  - `MessageCodeException` → its status + `code` + `params`;
  - `MaxUploadSizeExceededException` → 413 MSG28;
  - `NotFoundException` → 404 MSG04;
  - `BusinessRuleException` → 409 without a code (the web app falls back to MSG06);
  - `MethodArgumentNotValidException` → 400 with `fieldErrors[{field, code, params}]` (NotNull/NotBlank/NotEmpty → MSG01; Size → MSG02 with `max_length`; anything else → MSG06), and the top-level `code` taken from the first field error.
- `application.yml`: `spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 11MB`, `tsolve.stub.projects: ${TSOLVE_STUB_PROJECTS:}` and `tsolve.stub.inactive-projects: ${TSOLVE_STUB_INACTIVE_PROJECTS:}`. Both names are added to `.env.example` with empty values.
- Deviation from the design (Stub safety): the stub is a plain `@Component`, not `@ConditionalOnMissingBean`. That annotation is reliable only in auto-configuration, so the stub is to be deleted when the workspace module adds the real `ProjectAccess`, as its Javadoc says.
- `MessageCodeException` itself was created in Task 3.
- Effect on the existing `knowledge` endpoints: they now get the ProblemDetail shape instead of `{timestamp, status, message}`. The web app already expects ProblemDetail.
- Tests (TDD: red on compile, then green):
  - `StubProjectAccessTest` (5): fail-closed, per user, inactive, malformed entries.
  - `GlobalExceptionHandlerTest` (5): including AC-001.4 (MSG28 mapping).
  - One test first assumed empty properties; Spring keeps them null until one is set, so the test was corrected.
- Verify: `mvn spotless:apply`, then `mvn verify` → exit 0; 40 tests, 0 failures.

- Commit: default@0394f88 (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Task 6 csv-upload.
