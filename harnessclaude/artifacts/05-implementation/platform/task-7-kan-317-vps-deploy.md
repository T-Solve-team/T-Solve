# Session log: platform — task 7 (KAN-317) hand deploy of the staging stack

1. Done: hand deploy of the staging stack to the VPS (bluefin.tsolve.io.vn, login., admin., api.); Google sign-in via Keycloak works; stub Workspace lookup KAN-327; /me via main merge (BUG-204); review PASS WITH FIXES, fixes applied.
2. Open: My Profile endpoints (/me/spaces, /me/solutions*) not built; one company per stack (northwind needs the platform module); Forge app id placeholder; no SMTP; no CI/CD, backups, monitoring; Keycloak realm file changes need the realm deleted (IGNORE_EXISTING).
3. Next: PR review + merge; platform Tasks 3–14 for real tenancy; KAN-317 subtasks to Done on merge.
4. Branch: chore/KAN-317-platform-vps-deploy
5. PR: https://github.com/T-Solve-team/T-Solve/pull/220

1. Done: plan reordered into waves, Task 6a split into 6a-1 / 6a-2, commit 70a6bdd8
2. Open: QA step 1 of 6a-1
3. Next: QA step 2 and developer card for 6a-1
4. Branch: refactor/KAN-384-platform-one-tenant-binding
5. PR: none yet

1. Done: Task 6a-1 code and tests in 8 commits on the branch, review fixed, BUG-324 guard restored
2. Open: CI on the PR, staging smoke on bluefin-demo.tsolve.io.vn after merge
3. Next: Task 6a-2 (KAN-385)
4. Branch: refactor/KAN-384-platform-one-tenant-binding
5. PR: being opened
