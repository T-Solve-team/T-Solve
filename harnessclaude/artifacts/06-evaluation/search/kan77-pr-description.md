## Jira issue

[KAN-77](https://t-solve.atlassian.net/browse/KAN-77). Reuses the existing Ticket List UI from KAN-227 and implements the backend/connection covered by KAN-147.

## What & why

Implement `GET /api/v1/tickets` for Ticket Resolvers and Project Managers, with server-side visibility, literal keyword matching, Department/source/status filters and stable sorting/paging. Add public UUIDs and a ticket update timestamp through new Liquibase migrations; normalize malformed Department/sort values from shared frontend URLs.

The default visibility provider returns no data until identity/project integration supplies membership. The approved local demo provider is enabled only by exact `SEARCH_DEV_SCOPE=all`; it is not a production membership implementation. The existing detail route also depends on the ticket detail/review backend integration.

Preserve every bug-log row for the report. Verification-only changes wait for real Review Queue mutations and use paste plus Enter in one Chatbot rating test; these should be separately committed/reviewed from the Ticket List product change.

## How to test

Recorded results for the current Ticket List implementation:

- Backend unit/controller suite: 553 passed. New Search tests: 25 passed.
- Ticket List frontend tests: 37 passed.
- PostgreSQL Search integration suite: 15 passed, 0 failures/errors/skips, including migrations, rollback/reapply, visibility, filtering, sorting, UUIDs and HTTP serialization. Local Docker 29 needs `-Dapi.version=1.44` for the existing Testcontainers client (BUG-129).
- Fresh whole-backend verification: 553 unit/controller tests and all 53 PostgreSQL integration tests passed. Unit tests run without Ollama system properties; integration tests receive the test settings separately, preserving the negative startup checks.
- API generation succeeded twice with no generated diff.
- Frontend format, lint, typecheck and production build passed; one existing Fast Refresh warning remains.
- Full frontend rerun after test synchronization: 1,354 passed, 2 failed across 75 files. Both failures are unchanged My Solutions tests (BUG-163/127), unable to find the My Solutions table. Chatbot and Review Queue passed. The full suite is not green.
- Both My Solutions cases passed in an independent recheck (2 passed/46 name-filtered); the recorded full-suite failures remain in the defect log.

Pending checks:

- The standard Spotless ratchet check fails to discover Git in the local linked worktree (BUG-130); the same formatter checked all 24 Search Java files successfully through a temporary formatter POM. No standard check is marked passing or disabled.
- Verify the browser flow with mock mode off, real sign-in, real membership and the ticket-detail endpoint.

## Screenshots (UI changes)

No new layout. URL filter normalization uses the existing Ticket List controls.

## Checklist

- [ ] CI green
- [x] Tests added/updated
- [x] New migrations added; merged migrations unchanged
- [x] Existing bug history retained and new defects recorded
- [x] `.env.example` documents the local demo scope
- [x] New database integration tests and deterministic API generation verified
- [ ] Real browser flow verified with identity/membership

