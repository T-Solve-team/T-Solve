# Task 8: Import controller and template download

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 19:30

### Working on: import-endpoints
- Decision (user, 2026-10-22): method security is switched on in `common/config/MethodSecurityConfig` (`@EnableMethodSecurity`). Before this, the backend had no security config, so `@PreAuthorize` was ignored. That one file is added to the module's `paths` in `module.md` (not a spec change). Sign-in itself (Google OIDC) stays with the identity module; until then Spring Boot's default login applies.
- `ImportController` (`/api/v1/imports`, `@Tag("import")`):
  - `@PreAuthorize("hasRole('PROJECT_MANAGER')")` on the class, so every endpoint is covered.
  - Endpoints, with `operationId`s as in the contract: `GET /csv/template`, `POST /csv` (multipart `file` + `projectId`), `GET /previews/{id}/rows` (`status`, `page`, `size`, default 20), `GET /previews/{id}/rows/{rowNo}`, `POST /previews/{id}/remove-rows`.
  - Each endpoint calls one service method with `Principal.getName()`.
- Template: `CsvImportService.template()` returns the header line in template order. The controller sends it as `text/csv;charset=UTF-8` with `Content-Disposition: attachment; filename="ticket-import-template.csv"`.
- `RemoveRowsRequest(@NotEmpty List<@NotNull @Min(1) Integer>)`: an empty list → 400 MSG01.
- `GlobalExceptionHandler`: `AccessDeniedException` → 403 MSG08 (a signed-in user without the role).
- Tests (TDD: red on compile, then green):
  - `ImportControllerTest` (`@WebMvcTest` + `MethodSecurityConfig`), 8 tests: upload binding; Staff and Admin get 403 MSG08 on every endpoint (AC-001.10); 413 MSG28 (AC-001.4); the template (AC-005.1) and its role check (AC-005.2); the paging and status parameters; an empty `rowNumbers` → 400.
  - Also added `CsvImportServiceImplTest.templateIsTheHeaderRowOnly_AC_005_1` and `GlobalExceptionHandlerTest.missingRoleIsForbiddenWithMsg08_AC_001_10`.
- `@WebMvcTest` loads `WebConfig`, which needs `tsolve.cors.allowed-origins`, so the test sets it with `@TestPropertySource`.
- The confirm endpoint is added in Task 9, as planned.
- Verify: `mvn spotless:apply`, then `mvn verify` → exit 0; 69 tests, 0 failures.

- Commit: default@586b7c6 (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Task 9 csv-confirm.
