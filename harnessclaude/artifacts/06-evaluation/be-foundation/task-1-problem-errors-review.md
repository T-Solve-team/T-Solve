---
module: be-foundation
feature: problem-errors
task: 1
verdict: pass
attempt: 1
date: 2026-10-29
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: be-foundation — Task 1: Errors as ProblemDetail with a message code

## Summary
Reviewed `origin/main` (19e7c51) against the working tree of `feature/KAN-25-be-foundation-problem-errors`
(b6ad936 plus the uncommitted merge of `origin/main`), using only `git diff origin/main -- backend docs/bug-log.md`,
the spec (STORY-001, AC-001.1 to AC-001.12, as changed on 2026-10-29) and the design. One `GlobalExceptionHandler` is
left, in `common/exception`. It extends `ResponseEntityExceptionHandler` and keeps import-ticket-list's behaviour
(404 MSG04, 413 MSG28, `MessageCodeException` with its own status, 403 MSG08). `common/error` is gone. The
`ticket/` and `knowledge/` callers changed only their imports. `Solution.java` was also reformatted by Spotless,
and its tokens are otherwise identical (see Assumptions made).

All 12 criteria have a test that names them and checks them. The full `mvn verify` passes: 113/113 tests, Spotless
clean. There are no CRITICAL findings and two Informational ones. Verdict: **pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1: 12/12 criteria PASS (table under Issues). I checked the design's edge cases 3 and 9 to 13 too: 403 MSG08 from method security (not a 500); 500 MSG06 with no `detail` and the personal data only in the log; one entry per field, where MSG01 beats MSG02; MSG02 with `max_length` 150; 400 or 404 MSG06 for a malformed body or an unknown path; 409 `fieldErrors[email]` MSG11 |
| Completeness | pass | No MISSING criteria. The only EXTRA behaviour is `AccessDeniedException` → 403 MSG08 inside the dispatcher, and the design asks for it (edge case 3, Key Decisions). `CodedException`, `ProblemDetails`, the message-only constructors and `MessageCodeException` match the Interface Contracts. `common/error` is deleted in both `main` and `test`, and no reference to it is left in `backend/`, `frontend/src` or `docs/` |
| Correctness | pass | `mvn -f .pom-noratchet.xml clean verify` → BUILD SUCCESS. 107 Surefire tests and 6 Failsafe tests, 0 failures. `Spotless.Java is keeping 21 files clean`. No CRITICAL findings |
| Integration | pass | `ImportControllerTest` (`@WebMvcTest`, which loads the merged advice) passes unchanged: 413 MSG28 from `UploadTooLargeException`, 403 MSG08 (`$.code` checked), 409 MSG33, and 400 MSG01 for an empty `rowNumbers`. `ImportConfirmIT` (`@SpringBootTest`) starts the full context, so only one `globalExceptionHandler` bean exists. The body matches the contract's `ProblemDetail`/`FieldError` (`frontend/openapi/tsolve-api.yaml:1342-1381`), and the codes match the web catalog (`frontend/src/shared/messages/catalog.ts:23-28`: MSG01 `{field_name}`, MSG02 `{field_name}`/`{max_length}`, MSG03 and MSG06 with no params). `common/` imports no module (NFR 4), and `pom.xml` is unchanged against `origin/main` (NFR 3) |

## Traceability

`bun harnessclaude/hooks/sdd.ts trace be-foundation STORY-001` (run 2026-10-29):

```
  ✗ AC-001.1  no test under this module's paths (… backend/src/test/java/vn/tsolve/common/exception/** …)
  … (same line for AC-001.2 … AC-001.12)
trace be-foundation (STORY-001): 0/12 acceptance criteria have a test
```

This 0/12 comes from the known harness gap in `harnessclaude/FEEDBACK.md` (2026-10-22, 2026-10-25, 2026-10-28).
`TEST_GLOBS` does not match `*Test.java` or `*IT.java`, and `AC_MENTION` does not read the Java spelling
`_AC_001_1` that `backend/AGENTS.md` §2 requires. Below is my manual trace. I read each test and checked that it
asserts the criterion and is not just named after it. All are in
`backend/src/test/java/vn/tsolve/common/exception/GlobalExceptionHandlerTest.java`:

| AC | Test (line) | Checks |
|----|-------------|--------|
| AC-001.1 | `answersNotFoundAsProblemDetailWithCode_AC_001_1_BUG_001` (:54) | 404, `application/problem+json`, `status`, `code`, `params` is a map |
| AC-001.2 | `answersBusinessRuleWith409CodeAndParams_AC_001_2` (:64) | 409, problem+json, `code` MSG49, `params.department_name` |
| AC-001.3 | `putsTheNamedFieldInFieldErrors_AC_001_3` (:74) | 409, one `fieldErrors` entry `{field: email, code: MSG11, params}` |
| AC-001.4 | `mapsAMissingRequiredValueToMsg01_AC_001_4` (:85), `mapsAValueOverItsLengthLimitToMsg02WithMaxLength_AC_001_4` (:97), `mapsAnInvalidEmailToMsg03_AC_001_4` (:107), `givesOneEntryPerFieldAndMsg01WinsOnTheSameField_AC_001_4` (:116) | 400; MSG01 + `field_name`; MSG02 + `field_name` + `max_length` 150; MSG03; one entry per field |
| AC-001.5 | `mapsAnyOtherFailedRuleToMsg06_AC_001_5` (:126) | `@Pattern` → `fieldErrors[key]` MSG06 |
| AC-001.6 | `answersABusinessRuleWithoutCodeWithItsMessageInDetailOnly_AC_001_6` (:135) | 409, problem+json, no `code`, message in `detail` |
| AC-001.7 | `answersAnUnknownExceptionWith500Msg06NoDetailAndLogsIt_AC_001_7` (:172) | 500, MSG06, no `detail`, the body has neither the e-mail nor the class name, the log has both |
| AC-001.8 | malformed body (:187), wrong method (:195), unsupported media type (:202), missing parameter (:209), unknown path (:216) | Spring's status (400/405/415/400/404) + MSG06 |
| AC-001.9 | `keepsTheBaseExceptionsInCommonExceptionWithTheirMessageOnlyConstructors_AC_001_9` (:223), plus the whole build compiling `knowledge/` and `ticket/` | the three classes are in `vn.tsolve.common.exception`, no `vn.tsolve.common.error.*` class loads, and the message-only constructor works |
| AC-001.10 | `answersANotFoundWithoutCodeWithMsg04AndItsMessageInDetail_AC_001_10` (:144) | 404, MSG04, empty `params`, message in `detail` |
| AC-001.11 | `answersAMessageCodeExceptionWithItsOwnStatusCodeAndParams_AC_001_11` (:154) | the exception's status (409), `code` MSG30, `params.count` |
| AC-001.12 | `answersAnUploadOverTheSizeLimitWith413Msg28_AC_001_12` (:164) | 413, problem+json, MSG28 |

Manual trace: 12/12 have a test, and all of them pass.

## Issues

No blocking issues.

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.1 | PASS | `GlobalExceptionHandler.java:48-55` → `coded()` :118-128 (`ProblemDetails.of` puts `code` and `params`) |
| 2 | AC-001.2 | PASS | `GlobalExceptionHandler.java:62-65`, :118-128 |
| 3 | AC-001.3 | PASS | `GlobalExceptionHandler.java:123-126`; `BusinessRuleException.java:17` |
| 4 | AC-001.4 | PASS | `GlobalExceptionHandler.java:81-99` (one entry per field via `groupingBy`), :130-156 (precedence MSG01 > MSG02 > MSG03 > MSG06; `max` read from the constraint descriptor) |
| 5 | AC-001.5 | PASS | `GlobalExceptionHandler.java:151`, :154 |
| 6 | AC-001.6 | PASS | `GlobalExceptionHandler.java:119-121` (no `code`, the message in `detail`); `BusinessRuleException.java:8` |
| 7 | AC-001.7 | PASS | `GlobalExceptionHandler.java:75-79` |
| 8 | AC-001.8 | PASS | `GlobalExceptionHandler.java:109-116`: every body that `ResponseEntityExceptionHandler` builds keeps Spring's status and gets MSG06 |
| 9 | AC-001.9 | PASS | `common/exception/{NotFoundException,BusinessRuleException,MessageCodeException}.java`. `MessageCodeException` differs from `origin/main` only in its `package` line. The callers' diffs are import lines only, except `Solution.java` (Spotless whitespace; see Assumptions made) |
| 10 | AC-001.10 | PASS | `GlobalExceptionHandler.java:50-53` |
| 11 | AC-001.11 | PASS | `GlobalExceptionHandler.java:57-60` |
| 12 | AC-001.12 | PASS | `GlobalExceptionHandler.java:101-107`. The real overflow is thrown in `DispatcherServlet.checkMultipart` with no handler. It reaches the advice the same way as the unknown-path case (no handler), which the AC-001.8 test shows works |

### Stage 2 — Quality

CRITICAL (blocks pass):
No issues found.

Informational (does not block):
1. [backend/src/main/java/vn/tsolve/common/exception/GlobalExceptionHandler.java:81] Only
   `MethodArgumentNotValidException` is turned into `fieldErrors`. Spring 6.1 runs method validation as soon as any
   handler parameter carries a constraint (for example `@PathVariable @Min(1) long id`). Then an invalid
   `@Valid @RequestBody`, or a constrained `@RequestParam`, raises `HandlerMethodValidationException`, and the
   answer is 400 MSG06 with no `fieldErrors`. That is short of AC-001.4 for such an endpoint. My reviewer probe
   confirmed it (scratchpad, outside the repo): body `{"name":""}` on `/mv/{id}` with `@Min(1)` on `id` →
   `{"status":400,"detail":"Validation failure","code":"MSG06","params":{}}`. **No endpoint does this today**: no
   controller in `knowledge/` or `ticket/` puts a constraint on a parameter.
   Fix: override `handleHandlerMethodValidationException` and map its `ParameterErrors` and
   `ParameterValidationResult`s through the same `toFieldError` precedence. Or, if you prefer, state in
   `backend/AGENTS.md` §5 that controllers put constraints only on `@Valid` request records.
2. [backend/src/test/java/vn/tsolve/common/exception/GlobalExceptionHandlerTest.java:59-60, :257] The AC-001.1 test
   throws `NotFoundException("MSG110", Map.of())` and asserts only that `$.params` is a map. A handler that dropped
   the parameters of a coded `NotFoundException` would still pass. The shared `coded()` path is checked with real
   params only through `BusinessRuleException` (AC-001.2).
   Fix: throw with a non-empty map (for example `Map.of("ticket_id", "ITSUP-1")`) and assert
   `$.params.ticket_id`.

Scout (impact): the advice is global. These changes reach every caller:
- A `MessageCodeException` body no longer carries `detail` (`origin/main` sent `"MSG28 {}"`).
- A validation `fieldErrors` entry for MSG03 or MSG06 no longer carries `field_name`.
- A validation body's top-level `params` is now the first field's params.
- An email failure is now MSG03 (it was MSG06).

None of these breaks the contract or the web catalog: the catalog's MSG03 and MSG06 take no params, and the web
app reads `code`. `ImportControllerTest` and the `ticket/` service tests pass unchanged apart from their imports.

## Assumptions made

These are interpretations that I, as the reviewer, could not confirm with the user. The user can correct any of them:

1. **AC-001.9 "unchanged except for their imports" vs. `knowledge/Solution.java`.** That diff also reformats the
   file: one-line getters are split and annotations are moved onto their own lines. I checked that the file is
   token-identical to `origin/main` apart from the import line (whitespace stripped, `common.error` →
   `common.exception`). Touching the file for its import makes Spotless `ratchetFrom origin/main` require the
   reformat (NFR 2). I read "unchanged" as "no change in code or behaviour" and passed it. The stricter alternative
   is to commit the import alone, but that would break `mvn verify`.
2. **NFR 1 / Definition of Done "`sdd.ts trace be-foundation` passes".** The tool reports 0/12 because of the harness
   gap in FEEDBACK.md, not because a test is missing. I accepted the manual trace (12/12) for Task 1. The NFR clause
   about the tool cannot be met by any implementation until the harness reads `*Test.java` and `_AC_001_1`.
   **Open judgment call:** fix the harness, or reword NFR 1, before the module is marked `completed`.
3. **AC-001.4 "request validation".** I read this as Bean Validation of a `@Valid` request body (the only kind the
   code base uses). Parameter-level method validation (Informational 1) is treated as latent, not as a criterion
   failure. **Open judgment call:** does it belong in AC-001.4 now, or later when an endpoint needs it?
4. **Requirement 1 "every error" vs. AC-001.8 "Spring MVC rejects".** Errors raised outside the `DispatcherServlet`
   are not covered by any STORY-001 criterion, and I did not evaluate them. Examples: the servlet container, Spring
   Security's request firewall, or a non-security filter. Spring Boot answers these from `/error` with its own JSON
   map, not `ProblemDetail`. The security entry point and the access-denied handler belong to STORY-002 (Task 2).
   The contract lets the web app show MSG06 when `code` is missing. **Open judgment call:** is that fallback enough,
   or should a later task render `/error` as `ProblemDetail`?
5. **Interface Contracts "built with a message code, optional `params`".** I read this as "the params map may be
   empty", not as a separate `(code)`-only constructor overload.

## Test Report

The file `task-1-problem-errors-test-report.md` already existed, and my instructions said not to edit any file
except this review, so I left it unchanged. My own run below matches its numbers.

- **Command** (in `backend/`, 2026-10-29):
  `mvn -f .pom-noratchet.xml clean verify "-DspotlessFiles=$(cat .spotless-files)" "-DargLine=-Dapi.version=1.44"`.
  Two environment workarounds were needed, and neither is a finding:
  - `.pom-noratchet.xml` differs from `pom.xml` only by the missing `ratchetFrom` line. I checked this with `diff`.
  - `.spotless-files` lists exactly the 21 Java files that exist and changed since `origin/main`. 25 changed, minus
    the 4 deleted under `common/error`.
- **Result:** BUILD SUCCESS.
  - Surefire: 107 run, 0 failures, 0 errors, 0 skipped.
  - Failsafe (`ImportConfirmIT`, Testcontainers): 6 run, 0 failures.
  - `GlobalExceptionHandlerTest`: 20/20.
  - `ImportControllerTest`: 11/11.
  - Spotless: `keeping 21 files clean`.
- **Coverage:** not measured. `backend/pom.xml` has no JaCoCo, and the spec sets no target.
- **Reviewer probe** (scratchpad only, compiled against `target/classes`, never added to the repo):
  - It confirmed Informational 1.
  - It also showed that a `MultipartFile` parameter on an endpoint without `consumes` answers a non-multipart
    request with 500 MSG06 and an ERROR log line. This matches Spring's own status for `MultipartException`
    (AC-001.8). `ImportController` declares `consumes = multipart/form-data`, so it answers 415 instead. This is not
    a finding.
- **Bug log:** BUG-001 is `Fixed`, KAN-25, PR #82 (open; head `feature/KAN-25-be-foundation-problem-errors`), with test
  `answersNotFoundAsProblemDetailWithCode_AC_001_1_BUG_001`. BUG-058 (Testcontainers vs. Docker Engine 29) is logged
  as `Open` with its workaround.
