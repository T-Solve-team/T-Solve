# Test Report: ticket-repository — Task 5: Jira preview through the CSV preview endpoints (attempt 2)

**Date:** 2026-11-07
**Code under test:** `791d4ddd` plus the uncommitted fix for BUG-165 in the working tree (HEAD `b95b8877`):
`FillSolutionRequest.java` (a compact constructor that strips the sections) and `JiraPreviewControllerTest.java` (a new test).
**Command run by the reviewer** (from `backend/`, Git Bash, IntelliJ jbr Java 21, because the machine's JAVA_HOME is broken):
`mvn -B test -Dtest='JiraPreviewControllerTest,ImportPreviewServiceImplTest,JiraPreviewServiceTest,*FillSolution*' -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.check.skip=true`
**Full suite:** the main session ran it after the fix. I did not re-run it:
`mvn -B -Dapi.version=1.44 -Dspotless.check.skip=true verify` → 633 unit + 48 IT, 0 failures, BUILD SUCCESS. Spotless
check on a git clone with the two changed files → exit 0. (`ratchetFrom` cannot run inside this git worktree; see
attempt 1.)

## Results (reviewer run)
| Class | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| `JiraPreviewControllerTest` | 9 (8 in attempt 1, +1) | 0 | 0 | 0 |
| `JiraPreviewServiceTest` | 16 | 0 | 0 | 0 |
| `ImportPreviewServiceImplTest` (CSV) | 13 | 0 | 0 | 0 |
| **Total** | **38** | **0** | **0** | **0** |

- `BUILD SUCCESS`, exit 0, 23 s. No test class matches `*FillSolution*` on its own. The fill solution tests live in
  the two Jira classes.
- New test: `fillSolutionRejectsSectionsOfUnicodeSpacesOnly_AC_002_4_BUG_165`. It sends `symptoms = "　"` and
  `scope = "   "` and expects 400, `code` MSG01, field errors for exactly `symptoms` and `scope`, and no call to
  `ImportPreviewService`. Without the fix, `@NotBlank` would accept both values, the request would reach the mocked
  service, and `verifyNoInteractions` would fail. So the test does guard the bug.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no JaCoCo in `backend/pom.xml`) | none in spec |

## Acceptance Criteria → Tests
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-002` → `8/11`:
- ✓ AC-002.1, AC-002.2, AC-002.3, AC-002.4 (now ASCII and Unicode blanks), AC-002.5, AC-002.7, AC-002.8, AC-002.9
- ✗ AC-002.6, ✗ AC-002.10, ✗ AC-002.11: web-app criteria for Task 9, not this task.

## Failures
None.

## Notes
- The AC-002.4 gap from attempt 1 is closed. The new controller test exercises the real Jackson + Bean Validation
  path, which is where the fix sits.
- `JiraPreviewServiceTest` builds `FillSolutionRequest` directly. The constructor now strips its inputs, and the
  service tests still pass, because the service ran the same strip already.
