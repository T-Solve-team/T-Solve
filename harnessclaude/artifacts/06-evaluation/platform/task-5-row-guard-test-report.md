---
module: platform
feature: row-guard
verdict: fail
date: 2026-11-06
---

# KAN-100: tenant row-guard verification

Implementation is present locally on `feat/KAN-100-platform-tenant-row-guard`. The PostgreSQL row-guard suite now passes; verdict remains **not fully integrated** because runtime routing/provisioning and the standard worktree formatter gate remain pending.

| Check | Result |
|---|---|
| Full BE unit/controller suite | 531 passed, 0 failures/errors/skips. |
| New Liquibase parser tests | 3 passed: last-run ordering after a later module and complete function/DO-block statement generation. Removing only runOrder reproduced the assertion failure; restoring the XML byte-for-byte returned all 3 to passing. |
| Final test source compilation | Passed. |
| PostgreSQL runtime isolation and convention ITs | 30 passed, 0 failures/errors/skips after full access and api.version=1.44. Runtime role and actual PostgreSQL policy assertions executed successfully. |
| Docker/API environment | Earlier permission denial is cleared. The default Java client still gets HTTP 400 with local Docker 29; the per-run API property resolves discovery (BUG-129). |
| New Java formatting | 4 files clean with the existing Spotless/Palantir version through a temporary formatter POM. |
| Standard Spotless check | Failed: worktree Git repository not discovered. pom.xml unchanged. |
| AC source trace | 6/21 for STORY-001 + STORY-003; includes all AC-001.28–32 and AC-003.10, with other platform items pending. |
| Secret scan / diff whitespace | Passed for new test sources / current diff. |

The passing integration tests cover two isolated Workspace databases; absent, empty, malformed and wrong settings; positive CRUD; misrouting; transaction-setting reset; singleton identity; read-only identity and runtime DDL/TRUNCATE refusals; missing/renamed/altered/additional policies; disabled RLS; superuser/BYPASSRLS, table ownership and owner-role membership; and no legacy Workspace-column policy.

The actual runtime role, identity seeding and transaction setting remain integration dependencies of KAN-98/99 and provisioning. No separate frontend screen is specified for this infrastructure task. Browser/API acceptance must use the real router and runtime grants once those dependencies are available.

Run in a terminal with Docker access:

```powershell
cd 'C:\Users\Tranh\OneDrive\Desktop\Capstone\T-Solve-kan100\backend'
mvn -B -o test-compile failsafe:integration-test failsafe:verify '-Dit.test=RowGuardIT,RowGuardConventionIT' '-Dapi.version=1.44'
```

BUG-128 remains recorded in `docs/bug-log.md`, with its original design hazard and parser regression. Keep the row for the user's report after the fix is committed and verified. See the [implementation log](../../05-implementation/platform/task-5-row-guard.md).
