---
module: platform
feature: workspace-catalog
task: 4 (4a-1, 4a-2, 4b, 4c)
verdict: pass
attempt: 1
date: 2026-11-25
reviewer: tsolve-code-reviewer
iteration: 1
currentIteration: 1
---

# Review: platform — Task 4: WorkspaceCatalog, bluefin adopted, stand-ins replaced (PR #324, KAN-252)

## Summary

Verdict **APPROVE WITH CHANGES** (template verdict `pass`): 0 Critical, 0 Important, 6 Minor. None of the findings blocks
the merge. Fix Minor 1 before the adopt script runs on the VPS.

Range: `origin/main...HEAD` = `3f85d5d2...7bacd3ae` (78 files, +3807 / -952), worktree
`T-Solve-KAN-252` on `feat/KAN-252-platform-workspace-catalog`, clean, HEAD = PR #324 head `7bacd3ae`. The merge
commit only brings in main's wiki/infra changes. No code changed after the developer's merge-time checks.

The catalog matches its contract. `WorkspaceSummary` has exactly the seven contract fields. `RegistryRepository` never
selects `db_name`, `db_user` or `db_password`. The catalog only reads `TenantContext`, and `WorkspaceCatalogIT` proves
this with a `TenantContext` bean that fails on any `runIn`. `claimEmail` is race-safe: it inserts with
`ON CONFLICT DO NOTHING` (autocommit on the Registry pool), then reads the owner, and the 20-round latch test shows
exactly one row and `MSG155` for the loser. `CatalogJiraWorkspaceLookup` is unconditional over `ObjectProvider`, and
`TicketListRepositoryIT.gateOffContextHasOneCatalogLookupEach_edge83` checks the full context with the gate off. The
worker service takes `WorkspaceCatalog` directly, so with jobs on and no catalog startup fails naming it (R19). Every
Registry failure propagates and is never turned into an empty result. The removed stand-ins are gone everywhere outside
`harnessclaude/` and the historical bug-log rows (grep over `backend`, `infra`, `e2e`, `.github`, `docs`, `frontend`,
root files). The adopt script uses no `set -x` and echoes no secret. Secrets reach containers only as `-e NAME`
(never on argv), psql values are bound as `:'var'` literals, and a rerun is idempotent.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 below: every in-scope criterion PASS |
| Completeness | pass | No MISSING or EXTRA. AC-004.28 has no test, as planned (Task 7's source scan); the diff adds no controller |
| Correctness | pass | Targeted unit run 51/51 green; developer's verify and ITs as given on the card; CI on `7bacd3ae` still pending |
| Integration | pass | `WorkspaceCatalog` matches spec Interface Contracts (incl. `findActive()`); identity, jira-app and worker ports wired as R19 says; compose and env templates consistent |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | platform AC-004.17 lookups by id, site name (no case), cloud id; empty when none | PASS | `RegistryRepository.java:556-584`; `WorkspaceCatalogIT` tests 1-3; `CatalogLookupsIT` 1-2; `PlatformWorkspaceLookupTest` |
| 2 | AC-004.18 no connection data to a module | PASS | `SUMMARY_COLUMNS` (`RegistryRepository.java:547`); `WorkspaceCatalogIT.noLookupReturnsConnectionData_AC_004_18` (markers + record components) |
| 3 | AC-004.25 claim a free email, lower-case, for the bound Workspace | PASS | `WorkspaceCatalogImpl.java:695-703`; IT tests 8, 12 (edge 76) |
| 4 | AC-004.26 other Workspace → `MSG155` on `email`, nothing recorded; same Workspace → no change | PASS | IT tests 9, 10, 11 (race, edge 26) |
| 5 | AC-004.27 only catalog tables; never binds | PASS | IT context has no business DataSource; `NoBindingTenantContext`; test 7 |
| 6 | AC-004.28 no lifecycle endpoint | PASS (no test, planned for Task 7) | no `@RestController` in the diff |
| 7 | AC-004.29 Active list, no cache, empty when none, binds nothing | PASS | IT tests 5, 6, 7 |
| 8 | platform AC-001.15/.17/.19/.24 (4a-2) adopted row, no business data, password per Workspace | PASS | `registry-adopt.test.sh` A1-A8 (developer run), `RegistryPasswordCliTest` 9/9 |
| 9 | platform AC-002.14/.15 `runIn` accepts the catalog's id, refuses another, binds a Suspended one | PASS | `CatalogLookupsIT.runInAcceptsOnlyARegistryWorkspace_AC_002_14_AC_002_15` |
| 10 | worker AC-002.12 jobs on + no real service → startup fails; no mock adapter | PASS | `WorkerConfigurationTest.catalogServiceIsTheOnlyWorkerTenantService_AC_002_12`; `NoWorkspaceFallbacksTest` scans `src/main` |
| 11 | worker AC-003.1 Active UUIDs, no binding | PASS | `CatalogWorkerTenantServiceTest.activeWorkspaceIdsIsTheCatalogsActiveList_AC_003_1` |
| 12 | worker AC-003.3 failure throws, never empty | PASS | `activeListFailurePropagates_AC_003_3_edge84` (also `enqueueWorkspaceId`, bound and unbound) |
| 13 | jira-app AC-001.2 / AC-001.3 | PASS | `CatalogJiraWorkspaceLookupTest` (suffixes kept; trace shows no test fewer) |
| 14 | Edge #80 / #81 / #82 / #83 / #84 / #85 / #86 | PASS | `PlatformWorkspaceLookupTest`, `SignInStartControllerTest` / `WebSessionFilterTest` (500 `MSG06`), `CatalogWorkerTenantServiceTest`, `TicketListRepositoryIT`, `WorkerConfigurationTest` |
| 15 | Edge #87 single-database guard | Accepted risk (Hiếu, 24/11), closed by Task 6 | no test by decision |

### Card focus checks

- **`WorkspaceCatalog` never binds:** no `runIn` in `WorkspaceCatalogImpl` or `RegistryRepository`; the IT fails on any bind.
- **Email rule and race (MSG155):** correct. The insert and the owner read run on autocommit Registry connections. A
  concurrent insert waits on the unique index and then does nothing, so the read always sees the committed owner.
- **No connection data or password** in `WorkspaceSummary`, logs, errors or test output. The CLI prints only
  `PasswordCipher`'s fixed messages and variable names. The adopt test checks argv, stdout and stderr for secrets
  (`no_secrets_in`, the argv log).
- **Adopt script:** idempotent (A3 snapshot is unchanged). A differing row is refused naming the field (A4). The Jira
  site is copied both-or-neither and the cloud id lower-cased (A5, A6). No `set -x`. Secrets go only through env names.
  The gap is Minor 1.
- **`CatalogJiraWorkspaceLookup`:** unconditional `@Service` over `ObjectProvider` — as planned.
- **Removed names:** no caller or config left (see Summary). Two bug-log rows still cite deleted tests (Minor 2).

## Traceability

```
trace platform (STORY-004, STORY-001): 24/33 acceptance criteria have a test
  ✓ AC-004.17  CatalogLookupsIT, WorkspaceCatalogIT
  ✓ AC-004.18 / .25 / .26 / .27 / .29  WorkspaceCatalogIT
  ✗ AC-004.19 – AC-004.24  linkJiraSite: Task 18 (not in this PR)
  ✗ AC-004.28  no test: Task 7's source scan (planned)
  ✓ AC-001.15 / .17 / .19  registry-adopt.test.sh (+ RegistryChangelogIT)
  ✓ AC-001.24  registry-adopt.test.sh, PasswordCipherTest, RegistryPasswordCliTest
  ✗ AC-001.26, AC-001.34  other tasks (not in this PR)
trace worker: 36/36 acceptance criteria have a test (AC-002.12, AC-003.1, AC-003.3 incl. CatalogWorkerTenantServiceTest / WorkerConfigurationTest)
trace jira-app: 84/85 (AC-001.2, AC-001.3 via CatalogJiraWorkspaceLookupTest; the one ✗ is AC-005.3, an id ambiguity with ticket-repository, unrelated)
```

Every ✗ is outside this PR's Covers list, so no in-scope criterion is uncovered.

## Issues

Critical: none. Important: none.

Minor (does not block):

1. `infra/scripts/registry-adopt-workspace.sh:89-93` — `read_tenant_jira` reads bluefin's tenant `workspace` row but
   never checks that its `workspace_id` equals `TSOLVE_DEMO_WORKSPACE_ID`. If the id is wrong, the script adopts bluefin
   under a new id but with that row's Jira columns. `/me` then reports "not set up", because
   `WorkspaceStatusLookupImpl` calls `existsById(bound id)`. Setup then hits the singleton constraint, and recovery
   needs a hand delete in the Registry. Only the pre-deploy checklist guards this today.
   Fix: select `workspace_id` as well, and when a tenant row exists with another id, refuse naming `workspace_id` and
   write nothing. Add one script check (A9). `infra/README.md:52` should also say that for an existing Workspace the
   id is its current id (the old stub's field 3).
2. `docs/bug-log.md:168` (BUG-135) and `docs/bug-log.md:232` (BUG-205) — their Test column cites tests this PR deletes:
   `WorkerConfigurationTest.acceptsEmptyMockAllowListsAndDeniesEveryWorkspace_BUG_135_AC_001_11` and
   `StubWorkspaceStatusLookupTest.flagTrue…_BUG_205_AC_003_7`. BUG-324's row was updated; these two were not.
   Fix: in each row's notes, say that the stand-in and its test were removed in KAN-252 (Task 4c and Task 4b), and that
   the rule is now worker AC-002.12 / be-foundation AC-003.4.
3. `e2e/README.md:28-35` — the `TSOLVE_STUB_WORKSPACE_READY` line is gone, but nothing replaces it. On a fresh dev
   database there is no tenant `workspace` row, so Alice (Admin) is sent to Workspace Setup, and `smoke.spec.ts`'s
   "Workspace users" assertion fails. This is a local smoke only; CI does not run it.
   Fix: say "complete Workspace Setup once" in the README, or insert the `workspace` row for the dev Workspace id in
   `e2e/fixtures/dev-data.ts`.
4. `backend/src/main/java/vn/tsolve/platform/service/impl/WorkspaceCatalogImpl.java:697` — `claimEmail(null)` throws an
   NPE (500), and a blank email records `""` for the bound Workspace (the Registry CHECK only enforces lower case). There
   is no caller until identity Task 14.
   Fix: refuse null or blank before the insert (field `email`), or record this as a precondition in the Task 14
   hand-off.
5. `harnessclaude/artifacts/04-plans/platform/design.md:707` (edge #87) — one listed control reads "the app's Registry
   user has no `insert` until Task 7". That is not true at the database level: `REGISTRY_DB_USER` owns the Registry
   database (`infra/scripts/registry-db-init.sh:52`), and the adopt script inserts as that user. The control that
   actually holds is in code: `RegistryRepository` has no insert method.
   Fix: reword it in Change Mode, or in the Task 6 hand-off, so the accepted Critical risk lists only real controls.
6. `backend/README-worker.md:8` — the worker run step names only `REGISTRY_DB_URL` and `CATALOG_SECRET_KEY`. The worker
   also needs `REGISTRY_DB_USER` / `REGISTRY_DB_PASSWORD` and `TSOLVE_SITE_ORIGIN_TEMPLATE`: `PlatformWorkspaceLookup`
   is the worker's `KnownWorkspaces` source, and its edge #86 check fails startup without the template.
   Fix: list all four.

Informational: `findByCloudId` matches exactly (`RegistryRepository.java:580`). The adopt script lower-cases the
value, and Atlassian cloud ids are lower-case UUIDs, so this is fine today. Task 18's `linkJiraSite` should store the
cloud id lower-cased too.

## Assumptions made

- The card's commit-level verify results (4a-1 through the merge) are taken as given. The merge commit changes only
  main's own files, and the developer re-ran spotless, `registry-db.test.sh` and `WorkspaceCatalogIT` +
  `CatalogLookupsIT` after it.
- Minor 1 is rated Minor, not Important, because the pre-deploy checklist (tasks.md item 1) already names the right
  value. Hiếu may want to raise it, because a wrong id has no automated guard and no clean undo.

## Test Report

- Targeted, this session (`mvn -q -o test -Dtest=PlatformWorkspaceLookupTest,CatalogJiraWorkspaceLookupTest,CatalogWorkerTenantServiceTest,WorkerConfigurationTest,RegistryPasswordCliTest,NoWorkspaceFallbacksTest`),
  exit 0: PlatformWorkspaceLookupTest 7/7, CatalogJiraWorkspaceLookupTest 3/3, CatalogWorkerTenantServiceTest 10/10,
  WorkerConfigurationTest 20/20, RegistryPasswordCliTest 9/9, NoWorkspaceFallbacksTest 2/2 (51 tests, 0 failures).
  The startup-failure warnings in the log are the expected negative cases (edge #86, AC-002.12, AC-002.13).
- Not re-run (card: the reviewer does not run the full verify): `spotless:check test` 3240/0 (4c), the ITs listed on
  the card, `registry-adopt.test.sh` A1-A8, `registry-db.test.sh`.
- GitHub CI on PR #324 @ `7bacd3ae` (`gh pr checks 324`, 2026-11-25): frontend, changes, gitleaks, pr-title pass;
  backend-tests unit, it-other, it-keycloak-a, it-keycloak-b **pending**. The merge needs them green.
