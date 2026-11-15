---
module: be-foundation
feature: me-project-status
task: 5
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: be-foundation — Task 5: Project status in `/me`

## Summary
Commit `44138dc5` (range `default: 6e69f821..87018e28`) does three things. It adds a `common/session/dto/ProjectStatus`
enum (`ACTIVE`, `REMOVED`). It adds `projectStatus` to the `ProjectMembership` record as a required `enumAsRef` field.
It updates the `ProjectMembershipLookup` Javadoc to say the lookup returns the status. `SessionServiceImpl` still
passes the lookup's entries through unchanged, so each `/me` Project carries the status the lookup gives (AC-003.8).
Tests check three things: the status passes through unchanged (`SessionServiceImplTest:111`), the JSON field
(`SessionControllerTest:92`), and the schema (`CurrentUserSchemaTest:38`, `:77`). That schema check holds the
contract to the fe-foundation Interface Contracts (required set, `$ref`, enum values). `mvn clean verify` passes:
1728 unit and 556 integration tests, and spotless.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `ProjectMembership.java:15-16` adds the field. `SessionServiceImpl` maps nothing, so the status the lookup returns is the status served, ACTIVE and REMOVED alike (`SessionServiceImplTest.java:111-124`) |
| Completeness | pass | AC-003.8 and the Interface Contracts change (the lookup returns the status, any status listed) are both met. The `ProjectMembershipLookup.java` Javadoc states both |
| Correctness | pass | `mvn -B -Dapi.version=1.44 clean verify`: BUILD SUCCESS. Surefire 1728/0/0/0, Failsafe 556/0/0/0, spotless check clean |
| Integration | pass | It matches `frontend/openapi/tsolve-api.yaml` `ProjectMembership` (required list including `projectStatus`, `$ref: ProjectStatus`, `enum [ACTIVE, REMOVED]`). That is AC-003.6, checked by `CurrentUserSchemaTest` |

## Traceability
```
  ✓ AC-003.1  backend/src/test/java/vn/tsolve/common/session/SessionControllerTest.java, backend/src/test/java/vn/tsolve/common/session/SessionServiceImplTest.java, backend/src/test/java/vn/tsolve/common/session/SessionMeIT.java
  ✓ AC-003.2  backend/src/test/java/vn/tsolve/common/session/SessionControllerTest.java, backend/src/test/java/vn/tsolve/common/session/SessionServiceImplTest.java
  ✓ AC-003.3  backend/src/test/java/vn/tsolve/common/session/SessionServiceImplTest.java
  ✓ AC-003.4  backend/src/test/java/vn/tsolve/common/session/SessionControllerTest.java, backend/src/test/java/vn/tsolve/common/session/SessionServiceImplTest.java, backend/src/test/java/vn/tsolve/common/session/StubWorkspaceStatusLookupTest.java
  ✓ AC-003.7  backend/src/test/java/vn/tsolve/common/session/StubWorkspaceStatusLookupTest.java
  ✓ AC-003.5  backend/src/test/java/vn/tsolve/common/session/SessionControllerTest.java, backend/src/test/java/vn/tsolve/common/session/SessionMeIT.java
  ✓ AC-003.8  backend/src/test/java/vn/tsolve/common/session/CurrentUserSchemaTest.java, backend/src/test/java/vn/tsolve/common/session/SessionServiceImplTest.java
  ✓ AC-003.6  backend/src/test/java/vn/tsolve/common/session/CurrentUserSchemaTest.java, backend/src/test/java/vn/tsolve/common/session/SessionMeIT.java
  ! ids in tests but not in the spec: AC-001.72
trace be-foundation (STORY-003): 8/8 acceptance criteria have a test
```
`AC-001.72` comes from identity tests (`SecurityConfigCsrfOrderTest.java:26`, `JiraLinkControllerTest.java:199`).
It is older than this range and is not part of this task.

## Issues
None blocking. Notes:

1. **INFO:** `ProjectMembershipLookup` has no implementation in `backend/src/main`. Searching for
   `implements ProjectMembershipLookup` and `new ProjectMembership(` outside the tests finds nothing. So in a
   running app `/me.projects` is still `[]` (AC-003.4), and only the unit test exercises AC-003.8. When
   project-integration implements the lookup, it must map `project/entity/ProjectStatus` to
   `common/session/dto/ProjectStatus` and add a test that a REMOVED Project is listed with `REMOVED`. The BUG-216
   bug-log note already records this.
2. **INFO:** two identical enums now exist, `common/session/dto/ProjectStatus.java` and
   `project/entity/ProjectStatus.java`. The copy is deliberate (`common` depends on no module, ticket-repository
   decision 48). If the project enum gains a value, the copy must follow. Mapping by name (`ProjectStatus.valueOf(entity.name())`)
   in the future lookup would fail fast on drift.

## Assumptions made
- AC-003.8 says the status is "that the Project membership lookup returns". Since no lookup implementation exists
  yet (AC-003.4 empty answer), I accepted a mocked-lookup unit test plus the controller and schema tests as
  evidence. No integration test with real Projects is possible until project-integration lands.

## Test Report
See `task-5-me-project-status-test-report.md`. BUILD SUCCESS in 19:37 min. Unit tests: 1728, integration tests:
556, 0 failures, errors or skips. The session tests: `CurrentUserSchemaTest` 3, `SessionControllerTest` 5,
`SessionServiceImplTest` 8, `SessionMeIT` 4, all passed.
