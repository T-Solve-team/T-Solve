---
module: platform
feature: registry-staging-script
task: 3b
verdict: pass
attempt: 1
date: 2026-11-23
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: platform — Task 3b: Registry database on the existing staging volume (PR #321)

## Summary

Reviewed `origin/main...HEAD` (commits d5f63ce0, 7135a2ec; 5 files, all under `infra/`; nothing outside scope changed, no `harnessclaude/` file in the diff). Verdict: **pass with minors**. No blocking finding. The script refuses without the four variables and names the missing one, creates the role (LOGIN only, so no superuser, BYPASSRLS, CREATEDB, CREATEROLE or REPLICATION) and the database it owns, leaves an existing role alone (no ALTER ROLE), and prints no password. `registry-db.test.sh` passes S1-S5 on a fresh run by me (log outside the repo, rc 0). The three red flags are all pre-existing on main (see below). Proposed status: `passing`; no feature status changed.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.15 (database exists on a volume made before 3a): S2, S4. AC-001.32 (no superuser, no BYPASSRLS): S2 asserts `t|f|f|f|f|f` (login, super, bypassrls, createdb, createrole, replication). AC-008.5: S5. Edges 73, 74, 75, 14 each have a check. |
| Completeness | pass | Q4 (second run leaves the role, prints "already exists", never a password): S3, including a different password and the partial state (database gone, role kept). Q3: one user owns the DB, LOGIN only. `CATALOG_SECRET_KEY` has its own line, noted as never the `WORKSPACE_SECRET_KEY` value. README runbook line covers staging and local. |
| Correctness | pass | `bash infra/tests/registry-db.test.sh`: "all checks passed", S1-S5. `sh -n` of the script ok. CI of PR #321: see Test Report. |
| Integration | pass | Same end state as `infra/postgres/init/10-tsolve-dbs.sh` (S4 compares db/role/schema rows with a fresh-volume baseline). Compose `db` gets `REGISTRY_DB_*` with the same `:?` form as the other users; the backend gets `REGISTRY_DB_URL` / `CATALOG_SECRET_KEY` through `env_file`. |

### Quoting, injection, secrets

- Role and database names go through psql variables (`:"dbuser"`, `:"dbname"`, `:'n'`), never through shell interpolation into SQL; the heredocs are quoted (`<<'SQL'`). The `eval` in the refusal loop runs only over a fixed list of four literal names. No injection path found. Not tested with a hostile name (M3).
- Output of the create statements goes to `/dev/null`; the only echoed text is the role and database names. S1/S2/S3 grep every output for the three passwords.
- The password is passed as `-v dbpw=...` on the psql command line, so it shows in `ps` inside the db container for the moment of the create (M1). The existing init script does the same.

## Traceability

`bun harnessclaude/hooks/sdd.ts trace platform` (exit 1, whole module; orch-check for STORY-001 gives 9/20):

- AC-008.5: `infra/tests/registry-db.test.sh` (new with this PR; MISSING on main).
- AC-001.15: only `backend/.../RegistryChangelogIT.java`; this PR's test does not name it.
- AC-001.32: `no test under this module's paths` although S2 does assert it.
- Module total 38/187 (main: 37/187).

## Issues

Blocking: none.

Minor:
- **M1** `infra/scripts/registry-db-init.sh:36` (the `CREATE ROLE` psql call) — `-v dbpw="$REGISTRY_DB_PASSWORD"` puts the password in the psql argv, visible in `ps` to anyone inside the db container. Fix: read it in psql with `\getenv dbpw REGISTRY_DB_PASSWORD` (psql 16 in the image) and drop the `-v`; the init script has the same pattern, so change both in one later card or accept the risk in the design.
- **M2** `infra/tests/registry-db.test.sh:1-9` and the S2 check name — the file never writes `AC-001.15` or `AC-001.32`, so `sdd.ts trace` shows AC-001.32 as untested and AC-001.15 as covered only by the backend IT. Fix: name the ids in the header and in S2's pass/fail messages (S2 already checks the attributes).
- **M3** (informational) no check with a name that needs quoting (space, quote, hyphen); the script handles it by construction, nothing proves it. Optional S2 variant.
- **M4** (informational) AC-008.5 reads "the backend refuses to start and names the variable"; S5 proves compose refuses for `REGISTRY_DB_USER`/`PASSWORD` and that the other variables are listed and passed. The backend-side refusal for `CATALOG_SECRET_KEY` belongs to Task 3a; acceptable here.

## Red flags (clean `origin/main` in a temporary worktree under the scratchpad, removed afterwards)

1. `sdd.ts trace platform` exit 1: **same on main** (exit 1; 37/187 there, 38/187 on the PR; the one-criterion difference is AC-008.5, which this PR adds a test for). Exit 1 comes from the other uncovered criteria.
2. `orch-check.ts platform STORY-001` audit FAILED (1 failure, 117 warnings): **same on main**. The single failure is `module dashboard-audit: is implementing but its spec is changed since approval`; the warnings are the same set (order differs). No file of this PR is named.
3. registry FAILED, 463 files without a row: **same on main** (identical output on both). The tool scans only `harnessclaude/artifacts/**`; the PR touches no file there, so `infra/scripts/registry-db-init.sh` and `infra/tests/registry-db.test.sh` cannot appear in it. Not caused by this PR. This review file adds one (464) until registered.

## Test Report

- `MSYS_NO_PATHCONV=1 bash infra/tests/registry-db.test.sh`: rc 0, S1-S5 ok, "KAN-386 registry-db: all checks passed". Matches the developer's result; no change after it (`git status` clean, HEAD 7135a2ec).
- Not run: backend/frontend suites (infra-only PR; CI decides).
- `gh pr checks 321`: backend pass, frontend pass, changes pass, gitleaks pass, pr-title pass; backend-tests, frontend-checks and frontend-tests skipping (no backend/frontend change). Nothing failing or pending.
