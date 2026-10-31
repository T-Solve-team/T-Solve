# Test Report: be-foundation — Task 1: Errors as ProblemDetail with a message code — Attempt 1

**Date:** 2026-10-29, on branch `feature/KAN-25-be-foundation-problem-errors` (b6ad936) with `origin/main` (19e7c51)
merged in the working tree, not yet committed. JDK 21.0.11, Docker Engine 29.5.3.
**Command** (in `backend/`):
`mvn -f .pom-noratchet.xml clean verify "-DspotlessFiles=<the 21 Java files changed since origin/main>" "-DargLine=-Dapi.version=1.44"`

Two environment quirks, neither a change to a committed file:
- A linked git worktree cannot run Spotless's `ratchetFrom origin/main` (JGit finds no repository).
  `.pom-noratchet.xml` is a temporary copy of `pom.xml` without the `ratchetFrom` line, and `-DspotlessFiles` names
  exactly the files the ratchet would check; a deliberately misformatted file was caught with the same filter.
- Testcontainers 1.19.8 cannot talk to Docker Engine 29 without `api.version` (BUG-058, logged Open). Without the
  `argLine`, `ImportConfirmIT` fails with `Could not find a valid Docker environment`; all other tests pass.

## Results
- Total: 113 · Passed: 113 · Failed: 0 · Skipped: 0 (Surefire 107, Failsafe 6) · BUILD SUCCESS in 1:52 min
- Compile: pass · Spotless: `Spotless.Java is keeping 21 files clean`

| Test class | Tests | Fail/Err/Skip | Time |
|---|---|---|---|
| common.exception.GlobalExceptionHandlerTest | 20 | 0/0/0 | 7.2 s |
| ticket.controller.ImportControllerTest | 11 | 0/0/0 | 11.9 s (Spring context) |
| ticket.csv.CsvImportPerformanceTest | 1 | 0/0/0 | 0.9 s |
| ticket.csv.CsvRowValidatorTest | 15 | 0/0/0 | 0.1 s |
| ticket.csv.CsvTicketParserTest | 15 | 0/0/0 | 0.0 s |
| ticket.service.impl.CsvImportServiceImplTest | 12 | 0/0/0 | 0.7 s |
| ticket.service.impl.ImportConfirmServiceImplTest | 16 | 0/0/0 | 0.8 s |
| ticket.service.impl.ImportPreviewServiceImplTest | 9 | 0/0/0 | 0.1 s |
| ticket.service.impl.PreviewCleanupJobTest | 3 | 0/0/0 | 0.0 s |
| ticket.service.impl.StubProjectAccessTest | 5 | 0/0/0 | 0.0 s |
| ticket.service.impl.ImportConfirmIT (Failsafe) | 6 | 0/0/0 | 54.9 s incl. container start |

`ImportControllerTest` (`@WebMvcTest`) loads the merged `GlobalExceptionHandler`: 413 MSG28 from
`UploadTooLargeException`, 403 MSG08, 409 MSG33 and 400 MSG01 still come out as KAN-31 expects.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no JaCoCo in `backend/pom.xml`) | spec silent |

## Acceptance Criteria → Tests
`bun harnessclaude/hooks/sdd.ts trace be-foundation STORY-001`:
```
  ✗ AC-001.1 … AC-001.12  no test under this module's paths (…)
trace be-foundation (STORY-001): 0/12 acceptance criteria have a test
```
Known harness gap (FEEDBACK.md 2026-10-25, 2026-10-28): the tool scans no `*Test.java` and does not read the
`_AC_001_1` spelling. Manual trace of `GlobalExceptionHandlerTest`:

| AC | Tests | Result |
|---|---|---|
| AC-001.1 | answersNotFoundAsProblemDetailWithCode_AC_001_1_BUG_001 | pass |
| AC-001.2 | answersBusinessRuleWith409CodeAndParams_AC_001_2 | pass |
| AC-001.3 | putsTheNamedFieldInFieldErrors_AC_001_3 | pass |
| AC-001.4 | mapsAMissingRequiredValueToMsg01_AC_001_4, mapsAValueOverItsLengthLimitToMsg02WithMaxLength_AC_001_4, mapsAnInvalidEmailToMsg03_AC_001_4, givesOneEntryPerFieldAndMsg01WinsOnTheSameField_AC_001_4 | pass |
| AC-001.5 | mapsAnyOtherFailedRuleToMsg06_AC_001_5 | pass |
| AC-001.6 | answersABusinessRuleWithoutCodeWithItsMessageInDetailOnly_AC_001_6 | pass |
| AC-001.7 | answersAnUnknownExceptionWith500Msg06NoDetailAndLogsIt_AC_001_7 | pass |
| AC-001.8 | answersAMalformedBody…, answersAWrongMethod…, answersAnUnsupportedMediaType…, answersAMissingParameter…, answersAnUnknownPathWith404Msg06_AC_001_8 | pass |
| AC-001.9 | keepsTheBaseExceptionsInCommonExceptionWithTheirMessageOnlyConstructors_AC_001_9 (+ the whole build compiling the `knowledge/` and `ticket/` callers) | pass |
| AC-001.10 | answersANotFoundWithoutCodeWithMsg04AndItsMessageInDetail_AC_001_10 | pass |
| AC-001.11 | answersAMessageCodeExceptionWithItsOwnStatusCodeAndParams_AC_001_11 | pass |
| AC-001.12 | answersAnUploadOverTheSizeLimitWith413Msg28_AC_001_12 | pass |

## Failures
None.
