# Test Report: triage — Task 3: suggestion-history-api (attempt 1)

**Date:** 2026-11-04
**Range:** repo `default`, `e9fee1b..9970e7e` (code commit `9970e7e`; harness commit `ee16edd` ignored)
**Commands (run by the reviewer, in `backend/`):**
- `SERVER_PORT=8080 mvn -q test -Dtest='SuggestionResult*Test' -Dsurefire.failIfNoSpecifiedTests=false` (exit 0)
- `SERVER_PORT=8080 mvn verify` (exit 1, 02:04 min; cause below)
- `mvn -q spotless:check` (exit 0)
- `mvn test -Dtest='vn.tsolve.tracker.**'` without `SERVER_PORT` (checks the known environment fact)
- `bun harnessclaude/hooks/sdd.ts trace triage STORY-003 STORY-004` (exit 1; tool limitation below)

There is no separate typecheck or lint step. `mvn verify` compiles main and test sources before running Surefire, and Spotless is the format check. `manifest.yaml` has no `test_cmd` for the monorepo.

## Results

| Run | Total | Passed | Failed | Errors | Skipped |
|-----|-------|--------|--------|--------|---------|
| Targeted triage unit tests (`target/surefire-reports`) | 19 | 19 | 0 | 0 | 0 |
| — `SuggestionResultControllerTest` (39.6 s, includes context start) | 6 | 6 | 0 | 0 | 0 |
| — `SuggestionResultServiceImplTest` (1.7 s) | 13 | 13 | 0 | 0 | 0 |
| `mvn verify`, Surefire (whole backend) | 425 | 425 | 0 | 0 | 0 |
| `mvn verify`, Failsafe (`*IT`) | 3 | 0 | 0 | 3 | 0 |

- Compile: pass (main and test sources compiled inside `mvn verify`).
- Spotless (`ratchetFrom origin/main`): pass, `mvn -q spotless:check` exit 0. `mvn verify` stops at `failsafe:verify`, so the check was also run on its own.
- No coverage tool is configured (`pom.xml` has no JaCoCo). The spec sets no coverage target.

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line/branch coverage | not measured (no coverage plugin) | none set |
| AC coverage by test name | 10/10 STORY-003/004 criteria have tests (read by the reviewer) | NFR-1: every AC has a named test |

## Acceptance Criteria → Tests

`sdd.ts trace triage STORY-003 STORY-004` output:

```
  ✗ AC-003.1  no test under this module's paths (...)
  ... (same for AC-003.2 … AC-004.4)
trace triage (STORY-003, STORY-004): 0/10 acceptance criteria have a test
```

This is a limitation of the tool, not missing tests. There are two causes:
1. `harnessclaude/hooks/sdd.ts:47`: `TEST_GLOBS = ["**/*.{test,spec}.*", "**/test_*.py", "**/*_test.*"]` does not match `*Test.java` or `*IT.java`.
2. `harnessclaude/hooks/lib/spec.ts:34`: `AC_MENTION = /\bAC-\d{3}\.\d+\b/g` accepts only the dotted form. The backend names tests `_AC_003_1` (`backend/AGENTS.md` §2).

The reviewer mapped the tests by reading the method names. All files are under `backend/src/test/java/vn/tsolve/triage/`.

| AC | Tests (file:line) |
|----|-------------------|
| AC-003.1 | `controller/SuggestionResultControllerTest.java:49`; `service/impl/SuggestionResultServiceImplTest.java:133`, `:154`; `repository/SuggestionResultRecordRepositoryIT.java:110` (Docker) |
| AC-003.2 | `SuggestionResultServiceImplTest.java:162`; `SuggestionResultRecordRepositoryIT.java:116` (Docker) |
| AC-003.3 | `SuggestionResultControllerTest.java:74` (status 401 only), `:81`, `:90`; `SuggestionResultServiceImplTest.java:176` |
| AC-003.4 | `SuggestionResultControllerTest.java:100`; `SuggestionResultServiceImplTest.java:188`, `:203` |
| AC-003.5 | `SuggestionResultServiceImplTest.java:219`, `:239` |
| AC-003.6 | `SuggestionResultServiceImplTest.java:251` |
| AC-004.1 | `SuggestionResultServiceImplTest.java:266` |
| AC-004.2 | `SuggestionResultControllerTest.java:113`; `SuggestionResultServiceImplTest.java:286`; `SuggestionResultRecordRepositoryIT.java:131` (Docker) |
| AC-004.3 | `SuggestionResultServiceImplTest.java:301` |
| AC-004.4 | `SuggestionResultServiceImplTest.java:320` |

## Failures

### `vn.tsolve.triage.repository.SuggestionResultRecordRepositoryIT` (whole class, plus pre-existing `SignInStateRepositoryIT` and `ImportConfirmIT`)
- Error: `ExceptionInInitializer` → `java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration`
- Cause: this machine has no Docker. Testcontainers cannot start `pgvector/pgvector:pg16`. The error is in the environment, not in a test assertion. All 3 Failsafe classes fail the same way, and 2 of them predate this feature.
- What stays unproven without it: the DB-level proof for AC-003.1 (order with the `issueKey` tie-breaker), AC-003.2 (`answer=NONE` to `IS NULL`; the 16:59:59.999Z / 17:00:00Z Vietnam-day boundary in PostgreSQL) and AC-004.2 (lookup scoped to the given Projects). Also unproven: the JSONB-to-`String` read of `sources_used`, and Hibernate `ddl-auto: validate` of `SuggestionResultRecord` against changeset 002.
- Suggested fix: none in code. Run `mvn verify` with Docker (CI or a developer machine) before merge.

### Known environment fact checked: `SERVER_PORT` unset
- Without `SERVER_PORT`, `vn.tsolve.tracker.controller.JiraAppEventControllerTest` fails to load its ApplicationContext. All 6 of its methods error, and the surefire XML names the unresolved `${SERVER_PORT}`. `IssueCreatedServiceImplTest` passes 4/4. This is pre-existing tracker code that the diff does not touch. With `SERVER_PORT=8080` the whole Surefire suite passes 425/425.

## Notes
- No flaky test: the targeted run and the `mvn verify` run both gave 19/19 for triage.
- No skipped tests. No triage test is slower than 5 s, except that the controller test's first Spring context start falls inside its suite time.
- Frontend checks were not re-run by the reviewer. In `frontend/openapi/tsolve-api.yaml` the commit changed only the `triage` tag description (line 32). A grep of `frontend/src/shared/api/generated` finds no tag description text for triage, so `generate:api` should give no diff. This is unverified; running the generator could rewrite tracked files, which the reviewer may not do.
- Reviewer scratch checks (outside the repo, under the session scratchpad):
  - Jackson 2.17.2 serializes `SuggestionWikiSource` as `{"documentId":…,"title":…,"isAvailable":true}`, which matches the contract.
  - `LocalDate.parse("+999999999-12-31").plusDays(1)` throws `DateTimeException` (see review issue 4).
  - `javap` of Hibernate 6.5.3 `AbstractJsonFormatMapper.fromString` shows that a `String` target receives the raw JSON text.
