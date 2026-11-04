---
module: be-foundation
feature: roles-security
task: 2
verdict: pass
attempt: 2
date: 2026-11-04
reviewer: Codex (inline spec/quality pass following BE lead review)
iteration: 1
---

# Review: be-foundation — Task 2: roles-security

## Summary

The two required BE lead fixes and applicable security/test cleanups are implemented and
verified against merged main. This local pass does not dismiss the lead's GitHub review.
Code repair commit: `6e07c51`; main integration commit: `9b4abdc`. Repository hooks passed.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stable non-personal principal name; role union and deny-by-default/CSRF behavior preserved |
| Completeness | pass | All 13 selected ACs have reviewed assertions; required patch cleanup done |
| Correctness | pass | RED/GREEN for BUG-109/110; 427 unit and 18 integration tests pass |
| Integration | pass | Actual actuator/no-generated-login checks, identity slices and Jira/import regressions pass |

## Stage 1 — Spec compliance

| AC | Result | Evidence |
|----|--------|----------|
| AC-002.1 | PASS | Five workspace role enum/constants unchanged; TSolveUserTest |
| AC-002.2 | PASS | Canonical constructor retains Employee and immutable enum order; authority union tests |
| AC-002.3 | PASS | Real method-security hasAnyRole test accepts one permitted role |
| AC-002.4 | PASS | Anonymous API, mutation and outside-path cases return 401 MSG07 |
| AC-002.5 | PASS | Authenticated insufficient role and outside path return 403 MSG08 |
| AC-002.6 | PASS | Unannotated API remains protected by web chain |
| AC-002.7 | PASS | Both anonymous/authenticated outside-path denials assert codes |
| AC-002.8 | PASS | Docs slice now requires 200; actual actuator health returns 200/UP |
| AC-002.9 | PASS | Configured/foreign CORS preflight regressions retained |
| AC-002.10 | PASS | Basic challenge/redirect absent and real application has no UserDetailsService |
| AC-002.11 | PASS | Actual SPA cookie/header CSRF round trips on all four mutation methods |
| AC-002.12 | PASS | WithTSolveUser retains factory and adds Target/Inherited/Documented metadata |
| AC-002.13 | PASS | Missing/invalid mutation tokens return 403 MSG07 for authenticated users |

## Traceability

`trace be-foundation (STORY-002): 13/13 acceptance criteria have a test`.
Tests inspected include TSolveUserTest, SecurityConfigTest/IT, SecurityProblemWriterTest,
JiraAppSecurityConfigTest and affected identity tests. Trace is not treated as proof alone.

## Stage 2 — Quality and review disposition

| BE lead point | Disposition |
|---------------|-------------|
| 1: Principal name/PII/order | Fixed; BUG-109 guard checks UUID name, stable identity and redacted rendering |
| 2: Proposed patch artifact | Deleted; session record references actual commit 21096b3 |
| 3: Duplicate writer/trust resolver | Shared writer, canonical params/detail/path instance; static resolver; BUG-110 |
| 4: Null roles/helper | Named null guard added; optional helper unnecessary because constructor/factory enforce union |
| 5: Annotation metadata | Target, Inherited and Documented added |
| 6: Weak/missing tests | Real health and no-generated-login IT; explicit denied codes; all mutation methods and anonymous valid-CSRF case |
| 7: Production import for test slices | Removed from production; explicitly imported in five affected test classes |
| 8: Framework upgrade/globs | No protected-hook edit; durable FEEDBACK note on preserving local fix and *Tests.java gap |
| 9: Owner names | Backend BUG-093/094 metadata corrected to full owner name |
| 10: Commit/task hygiene | Published history retained; new merge, code fix and Harness records separated; task record shortened |

No blocking finding remains in the repaired feature. Existing role/session contracts remain
UUID-based as the approved STORY-002 requires. The SQL User List migration is a separate PR.
Writer instance includes request path but no query/header/token; catalog messages preserve
Jira semantics. No authentication, CSRF or method-security protection was disabled.

Recommendations for FE CSRF transport, identity rotation and proxy/cookie deployment settings
remain follow-ups; no unsolicited owner messages or tickets were sent. This pass adds no
scope enforcement from KAN-118 and does not alter protected framework code or dependency versions.

## Issues

None remaining within this review repair. BE lead re-review is still required by GitHub.

## Test Report

See `artifacts/06-evaluation/be-foundation/task-2-roles-security-test-report.md`.
