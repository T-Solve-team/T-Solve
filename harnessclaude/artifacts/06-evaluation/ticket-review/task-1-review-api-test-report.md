# Test Report: ticket-review — Task 1: review-api

**Date:** 2026-10-20
**Commands:** Maven spotless:apply, verify, standalone spotless:check; sdd.ts check, trace and audit; git diff --check.
**Scope:** backend review feature; frontend Task 2 and full live integrations remain pending.

## Results
- Total: **33** · Passed: **33** · Failed: **0** · Errors: **0** · Skipped: **0**.
- Entity: 4 · service: 13 · MVC: 8 · PostgreSQL/HTTP integration: 8.
- Maven verify: BUILD SUCCESS. Spotless standalone check: exit 0. Whitespace check: exit 0.
- Actual Java 21.0.12.1, Maven 3.9.9 and disposable PostgreSQL 16; schema migration and Hibernate validation executed.
- Real transaction tests prove bulk rollback and a single successful concurrent decision. The concurrency check observes the second connection waiting on a PostgreSQL lock.
- Actual HTTP controller/service/database integration checks detail JSON, Project denial, paging and a persisted change request. OIDC and email transports are not implemented or tested live.

## Coverage
No code-coverage command or numerical coverage threshold is configured in the backend. Coverage percentage was not measured. Assertions cover authorization, validation, state changes, persistent side effects, rollback, lock contention and API serialization.

## Acceptance Criteria → Tests
- Official `bun harnessclaude/hooks/sdd.ts trace ticket-review`: **failed, 0/30**. BUG-017 excludes `*Test.java` and does not normalize the Java naming required by backend conventions. Neither hooks nor statuses were changed to conceal the result.
- Supporting manual Java-name inventory: **24/30 IDs mentioned**. This is diagnostic evidence only. Several mentioned criteria combine backend and frontend behavior; their full satisfaction is not claimed.
- No Java-name mention: AC-001.4, AC-002.2, AC-002.3, AC-003.1, AC-006.5, AC-006.6. UI assertions and delivery documentation validation remain for Task 2/module evaluation.
- Module/feature has not been marked passing or completed. Implementation remains at its user commit checkpoint.

## Failures found and disposition
| ID | Reproduction / root cause | Disposition |
|----|----------------------------|-------------|
| BUG-013 | Mockito/Byte Buddy cannot instrument Java 25 classes | Fixed local test toolchain to Java 21; no dependency suppression or test weakening |
| BUG-014 | Stubbing a transactional audit proxy triggers MANDATORY outside a transaction | Fixed test injection to target the underlying spy while retaining the production transaction proxy |
| BUG-015 | JSON ID 1.5 accepted as 1; expected 400 versus actual 200 | Fixed strict integer deserialization; regression covers bulk and single-ticket IDs |
| BUG-016 | All-null sections create zero secondary Solution rows; expected 1 versus actual 0 | Fixed mandatory secondary row; PostgreSQL regression passes |
| BUG-017 | Framework trace reports zero despite executing JUnit tests | Open; outside approved review-module code paths; recorded in FEEDBACK.md |

The ledger `docs/bug-log.md` retains all defects, including fixed ones. Frontend BUG-010–BUG-012 remain open. Initial expected-red TDD assertions and a temporary missing test database/configuration incident are retained in local run logs and the session notes; they are not represented as product regressions.

## Notes
- No tests were skipped or deleted to obtain the successful Maven result.
- Graphify updated; SQL parser dependency is absent, so SQL correctness was verified directly in PostgreSQL.
- Generated frontend API sources still reflect the prior contract until Task 2 regeneration. No frontend runtime or build completion is claimed for this backend feature.
- Code commit and harness commit are both pending user agreement; no remote operations performed.

## FE Lead D1/D2 correction (2026-10-22)

The single-ticket restriction and empty-Solution scenarios described in this historical report are superseded. Current approved spec uses batch Request changes with one shared comment and no empty-Solution review case. Report3 sources and D1/D2 replace the layout-template interpretation. Bug references above use canonical reconciled IDs. See task-5-fe-lead-amendments.md for current verification.
