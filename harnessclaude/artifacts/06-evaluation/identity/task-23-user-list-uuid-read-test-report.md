# Test Report: identity — Task 23: UUID User List read integration

**Date:** 2026-11-05
**Base:** `ed34ba3` (KAN-235)

| Check | Result | Evidence |
|---|---|---|
| Backend | Pass | Java 21 `mvn -B -Dapi.version=1.44 spotless:apply verify` exited 0. 488 unit and 46 integration tests passed with zero failures, errors or skips. |
| User List integration | Pass | 4 repository and 7 HTTP tests on PostgreSQL. Both tenant databases load Account changeset 006. Test fixtures supply Department, role and tenant guard infrastructure. |
| Frontend full suite | Pass on recheck | `npm run test -- --maxWorkers=2` passed 1,313 of 1,313, with zero failed or pending. Git confirmed the frontend tree matched the tested candidate after the main merge. |
| Frontend focused | Pass | UserListPage, UserListUuid and handler tests passed 38 of 38. |
| Frontend checks | Pass | Prettier, ESLint, typecheck and production build passed. Existing fast-refresh warning in TicketListFilters. Orval regenerated 137 files with identical hashes. |
| Spec trace | Pass | `bun harnessclaude/hooks/sdd.ts trace identity STORY-004` found tests for all 19 criteria. |

## Acceptance criteria

- AC-004.1–.8: UserListPage and MSW handler tests cover navigation, search, filters, loading, errors, empty state and input limits.
- AC-004.9–.15: Repository, HTTP, DTO and UserListUuid tests cover migrated Account columns, UUID identifiers, role and Department joins, search, status, pagination and lookups.
- AC-004.16: HTTP and security tests cover session binding, current roles, CORS and the shared error response.
- AC-004.17–.19: UserListUuid covers the removed Last sign-in sort. Transaction and HTTP tests cover tenant mismatch, isolation and read-only behavior.

## Failures and rechecks

- BUG-118: Before changing the repository, four repository tests returned MSG06 after the fixture loaded changeset 006. After aligning the queries to the migration, all four passed. An earlier attempt ended during setup because Docker Desktop was stopped. The comparison used the PostgreSQL run after Docker was started.
- BUG-070: The first full frontend run passed 1,240 of 1,313 tests and failed 73. The focused run and the full two-worker recheck passed. Test source, assertions and timeouts were unchanged between those full runs. The cause of the first run remains unconfirmed.

## Scope

Tenant routing and trusted Workspace session issuance are required before the feature flag can be enabled. The PostgreSQL integration tests use test adapters for those dependencies.
