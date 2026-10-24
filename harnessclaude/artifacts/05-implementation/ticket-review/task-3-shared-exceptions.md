# Review exception alignment with main

Date: 2026-10-22. User explicitly selected refactoring review to the new exception rule. No acceptance criteria or API contract changed. Work performed sequentially without subagents.

## Changes

- Integrated main through ecbe484 and 8534089. FE Lead review subsequently restored canonical main BUG-001 (ProblemDetail) and BUG-002 (route test) and reconciled branch-only IDs from BUG-010.
- Main changes include backend exception/client package rules, audit support for .gitkeep and lazy route test timeout. No dependency version, security policy or production configuration changed by this refactor.
- Shared ApiException carries status/code/params/field. ReviewException lives in ticket/exception; one GlobalExceptionHandler lives in common/exception with no module imports.
- Preserved review error metadata and status mapping. Framework errors retain their HTTP status; unexpected exceptions return MSG06 without payload details.
- Migrated legacy NotFoundException/BusinessRuleException and their three callers. Legacy errors now use existing MSG06 fallback and RFC 9457; dedicated domain-specific messages remain for the future owning modules. Spotless reformatted the touched legacy Solution class.

## Verification

- RED: rendersSharedErrorsAsProblemDetail_BUG_001 failed: application/json was incompatible with application/problem+json.
- Focused backend: 28 tests passed, including shared error metadata, validation, review MVC/service/entity behavior and safe fallback.
- Full mvn verify: 37 tests passed, zero failures/errors/skips, BUILD SUCCESS, including PostgreSQL transaction and concurrency tests. Spotless check passed.
- Full frontend default test run: 12 files, 78 tests passed, no unhandled errors. jsdom still emits its known getComputedStyle pseudo-element warning.
- Harness audit and git diff --check passed. Graphify refreshed: 1665 nodes, 3742 edges. SQL extraction remains unavailable because tree_sitter_sql is missing.

## Local verification setup and failed attempts

- The initial focused MVC command omitted required SERVER_PORT and failed Spring property binding; reran successfully with required environment variables. No application configuration changed.
- One frontend command resolved the temp directory relative to the wrong working directory and failed before tests. Corrected the invocation to the ignored local node_modules/.cache/vitest-temp directory (existing BUG-023); full suite passed.
- Docker initially was stopped/starting. The previously used manual container was absent in the current engine. An early full verify consequently failed nine PostgreSQL context tests with no database. These were environment failures, not ignored assertions.
- First postgres:16 image pull failed with EOF; retry succeeded. Created tsolve-review-verification-20261022, bound only to 127.0.0.1:55432, using isolated review_verification database and local test credentials. pg_isready reported accepting connections, then full verify passed.
- This test container does not restore the earlier manual UI fixtures or run frontend/backend application servers.

## Checkpoint

User approved commit and push. Code recorded as default@b6370ac10d395246fcb7b31e8f2445831f155211: refactor(review): centralize API exception handling (KAN-24). The user created this code commit after the privacy hook blocked the staged secret-scan command; tracking was then updated through sdd.ts commit. Existing module stays implementing; this verification does not resolve the previously recorded harness Java trace limitation or external auth/email integration.
