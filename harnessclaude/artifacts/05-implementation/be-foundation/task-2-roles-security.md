# Task 2: Five roles, deny-by-default security and CSRF — KAN-117

**Module:** be-foundation
**Assignee:** Phạm Tùng Dương
**Status:** passing
**Iteration:** 1

---

## Session 2026-11-04 — PR #107 review repair

### Working on: roles-security

- User requested checking the BE lead review and resolving PR #107 conflicts. The PR is
  KAN-117 (`feat/KAN-117-be-foundation-roles-security`), separate from KAN-107 User List.
- Integrated main `3ff7218` in merge commit `9b4abdc`. Preserved both bug-log append blocks
  and both Harness event histories. No source conflict, history rewrite or force push.
- BUG-109: TSolveUser now implements AuthenticatedPrincipal and returns only its immutable
  UUID from getName. toString excludes email/fullName; roles use an immutable EnumSet,
  and null roles report the argument name. Regression proves stable Authentication name
  across account/role changes and compatibility with the 150-character import owner column.
- BUG-110: web and FIT chains use SecurityProblemWriter with common code/params/detail/instance.
  Existing Jira codes/text remain; MSG07 uses its catalog text. Instance contains only the
  request path, excluding query/token data. Trust resolver is a static constant.
- Added the test annotation metadata requested by review. Public-doc tests now require 200;
  a full-context PostgreSQL test proves actual actuator health is 200/UP and no generated
  UserDetailsService exists. Added outside-path code and anonymous-CSRF cases, plus real
  cookie/header CSRF checks for PUT/PATCH/DELETE alongside POST.
- Removed the production test-only SecurityConfig import from AuthSecurityConfig. The five
  affected identity test slices import the shared security explicitly; normal application
  component scanning remains responsible for production configuration.
- Removed the applied BUG-093-proposed.patch. The canonical correction and its regression
  tests are in commit `21096b37c74f455a1f772d4391c1529f1072f7ac`; no protected hook was edited.
- Corrected the backend BUG-093/094 owner metadata to Phạm Tùng Dương. Existing published
  history is retained; new review fixes and their Harness records are committed separately.
- Updated MethodSecurityConfig's stale default-login Javadoc. The task record follows the
  framework session-log template, with portable verification details in its separate report.

### Verification

- RED: getName returned the PII-bearing record string; role ordering was unstable. The
  corrected writer test reproduces missing params using Spring's ProblemDetail Jackson mixin.
- GREEN: focused four-class security run: 31 tests, zero failures/errors/skips.
- Full Java 21 `mvn verify -Dapi.version=1.44`: BUILD SUCCESS, 427 unit + 18 PostgreSQL
  integration tests, zero failures/errors/skips, Spotless passes. Initial integration run
  found Docker Desktop stopped; after starting it the same checks pass.
- Official trace: STORY-002 13/13; spec completeness: 4 stories/42 ACs; hook regression tests 4/4.
- Gitleaks production security scan and whitespace check pass. No dependency or frontend
  source change versus main. No new secret or original database-reference file committed.
- Raw local logs are ignored under backend/target; the report records durable results.
- Graphify code graph refreshed to 7642 nodes / 17293 edges and queried for the changed
  principal, writer test and health IT. SQL parsing remains unavailable; no schema changed.

### Follow-ups outside this repair

- FE client X-XSRF-TOKEN transport; identity CSRF rotation; proxy/Secure/SameSite deployment
  policy require their own owners' changes. No external ticket or owner message was sent.
- Protected Harness hook customization must be preserved during upgrade; FEEDBACK records
  upstream needs, including the unsupported *Tests.java convention. No hook workaround.
- Optional toAuthentication helper is not needed for this repair: the constructor enforces
  role union and the existing test factory already uses authorities().

### Blockers

None in the implemented review repair. The BE lead still owns re-review approval.

### Next up

Review fixes are committed as `default@6e07c51` with repository hooks passed. The official
feature tracker recorded that code commit and moved fixing → evaluating → passing.
Save the separate Harness evidence and update the existing PR branch, then verify GitHub
no longer reports a merge conflict. Do not merge the PR into main.

## Earlier delivery references

- Approved STORY-002 first delivery: `default@673f3abcfd893c972558b3e8630b9e2bba859bac`.
- User-applied Java trace fix and original handoff: `21096b37c74f455a1f772d4391c1529f1072f7ac`.
- Original verification at that earlier base: 271 unit (one pre-existing disabled callback),
  ten integration tests; trace 13/13 and hook tests 4/4. Current main enables later callback
  coverage; the fresh review report supersedes those old counts.
