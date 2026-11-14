---
module: platform
feature: staging-cd
task: KAN-14 (STORY-010; SSH forced-command CD, user deviation of 13/11)
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: platform — KAN-14: continuous deploy to staging over SSH

Reviewed state: uncommitted worktree `T-Solve-cd`, branch `chore/KAN-14-platform-cd-staging`, base `b860ff6e`.
Files: `.github/workflows/cd-staging.yml`, `infra/scripts/deploy.sh`, `infra/README.md` section 12,
`harnessclaude/artifacts/04-plans/platform/design.md`. Verdict in the card's terms: **APPROVE WITH FIXES**
(no security blocker; issues 1 to 4 are fixes to make before the commit).

## Summary
The security core holds. The job runs only for `workflow_dispatch` or a successful `CI` run whose event is `push`
(CI runs on push only for `main`), so a pull request, a fork or a manual CI run never reaches the `staging`
environment. No third-party action, `permissions: contents: read`, every input and event field goes through `env`,
no `set -x`, the host key is pinned (`StrictHostKeyChecking=yes`, own known_hosts file, `BatchMode`), the key file
is written under `umask 077` and removed in `always()`. The forced command accepts exactly 40 lowercase hex
characters, never evaluates the argument, fetches first and checks ancestry against the fetched `origin/main`.
It prints no value of the env file and has no `down -v`. The defects are in the "previous commit" logic, the Caddy
step, the manual-step warning and the README's trust statement.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass with fixes | Deploy path, argument check, ancestry check, lock, named failing step and exit codes work (harness below). Issues 1 to 3 break the Caddy restart and the warning |
| Completeness | deviation accepted by the user | AC-010.2 to AC-010.6 knowingly not met (design.md, "KAN-14 CD deviation"); the spec is unchanged |
| Correctness | pass with fixes | `bash -n`, shellcheck and actionlint clean; 3 defects confirmed by execution or by reading the compose file |
| Integration | pass | The workflow name `CI` matches `ci.yml`; compose service names and the `TSOLVE_ENV_FILE` export match `compose.staging.yml` |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace platform STORY-010`: 0/12 acceptance criteria have a test. The story is
infrastructure with no automated test in the repository (same as the KAN-317 review); see Assumptions.

## Issues

Fix before the commit:

1. `infra/scripts/deploy.sh:57` — `PREVIOUS` is the checkout's `HEAD`, not the last successful deploy. After a failed
   deploy, `HEAD` is the failed commit. Confirmed: A deployed, B (changes `infra/caddy/` and a realm file) fails at
   build, C succeeds: the log says "previous: B (to roll back: redeploy B)", Caddy is not restarted and no warning is
   printed. Fix: read `PREVIOUS` from the `DEPLOYED` file (40 hex and `git cat-file -e`); when it is missing or
   invalid, restart Caddy and print the WARNING for every manual file.
2. `infra/scripts/deploy.sh:88` — `up -d --no-deps caddy` does nothing when only the Caddyfile changed: the service
   definition is the same, so Compose does not recreate the container, and the single-file bind mount still points
   at the old file after `git checkout` replaced it. Fix: `"${DC[@]}" up -d --no-deps --force-recreate --wait caddy`
   (before the smoke checks, as now). README section 12 step 3 says "restarts caddy".
3. `infra/scripts/deploy.sh:90` — the pattern `keycloak/realm-.*\.json` does not match the files the stack mounts,
   `infra/keycloak/staging/realm-*.json` (`compose.staging.yml:164-165`). Confirmed: a change to
   `infra/keycloak/staging/realm-tsolve.json` gives no warning. Fix: `keycloak/(staging/)?realm-[^/]*\.json`; also
   decide on `keycloak/themes/` (bind-mounted, changes on checkout without a Keycloak restart).
4. `infra/README.md:34` with `:176-178` and `:204-205` — trust model. (a) Section 2 gives `/srv/tsolve` to the login
   user, so a root-owned `/srv/tsolve/bin` can be renamed and replaced by that user: install the script outside it
   (`/usr/local/sbin/tsolve-deploy`) or make `/srv/tsolve` root-owned 755. (b) "A merged PR does not change what the
   key runs" is true but reads as more than it is: every deploy builds and runs code from `main` (Dockerfiles, and
   the compose file of the new commit for backend, worker and web) as a member of the `docker` group, which equals
   root on the VPS and can read `.env`. State it: the forced command limits a stolen key to "deploy a commit of
   main"; the protection of `main` (required reviews and checks) is the real boundary. (c) `authorized_keys` is
   owned by `deploy`; say so or move it to a root-owned `AuthorizedKeysFile`.

Should fix:

5. `.github/workflows/cd-staging.yml:28,52` — a late or re-run `CI` run of an older `main` commit deploys that older
   commit over a newer one (CI runs on `main` are not cancelled and can finish out of order). Fix: for `workflow_run`,
   compare `RUN_SHA` with `gh api repos/$REPO/commits/main` and skip with a notice when it is not the head. Also add
   `github.event.workflow_run.head_branch == 'main'` to the `if` as a second lock.
6. `infra/scripts/deploy.sh:13-22` — the `TSOLVE_*` overrides are honoured in the forced-command run. A stock sshd
   does not let the client set them (`AcceptEnv LANG LC_*`, `PermitUserEnvironment no`), but one sshd setting would
   turn `TSOLVE_SRC` into code execution. Fix: when `SSH_ORIGINAL_COMMAND` is set, use the fixed paths only; use
   `restrict` in the `authorized_keys` line instead of the four `no-*` options (it also covers `~/.ssh/rc`).
7. `infra/scripts/deploy.sh:111-124` — `check_me` passes on any 401, also one with no JSON `code` (confirmed: body
   `Unauthorized` passes with no note). A 401 from another layer would pass. Fix: require `"code":"MSG07"`.

Informational:

- `cd-staging.yml:84` — the summary `grep` also matches Maven `[WARNING]` lines of the build; anchor it to the
  script's timestamped lines. `GH_TOKEN` and the secrets can move from job `env` to the one step that uses them.
- `cd-staging.yml:29` — `ubuntu-latest`; `ci.yml` pins `ubuntu-24.04`.
- `deploy.sh:48` — fetch with an explicit refspec (`+refs/heads/main:refs/remotes/origin/main`) so the ancestry
  check does not depend on the clone's configuration.
- `deploy.sh:76` — a malformed `.env` line makes Compose print that line in its error, which then reaches the
  Actions log. Send the stderr of `config -q` to the deploy log only, or print a fixed message.
- `deploy.sh:79` — images are rebuilt on every deploy and never pruned; the disk of the VPS fills over time.
- `infra/README.md:207-209` — the compose file of the new commit IS applied to backend, worker and web; only the
  other services are left to a person.
- `infra/scripts/deploy.sh` is untracked: add it with `git add --chmod=+x` (Windows adds 100644).

## Test Report
- `bash -n infra/scripts/deploy.sh`: exit 0.
- `koalaman/shellcheck:stable -x infra/scripts/deploy.sh`: exit 0, no output.
- `rhysd/actionlint .github/workflows/cd-staging.yml`: exit 0, no output.
- Harness (a copy of the script in an Alpine container, a throwaway git repository, stub `docker`, `curl`, `sleep`):
  empty, short, upper-case, `<sha>; id`, `<sha>\n`, two SHAs and a bad `SSH_ORIGINAL_COMMAND` beside a good `$1`
  all exit 2; a side-branch commit and an unknown SHA exit 1 before checkout; a failed build exits 1 with
  "FAILED at step 'build backend web'" and leaves `DEPLOYED` unchanged; a refused connection and a failing health
  check are retried 12 times, then exit 1 with the step named; a missing env name is listed by name only; a held
  lock exits 3; the secret values of the test env file appear in no output and not in `deploy.log`.
- Not run: the workflow itself and a real deploy (no SSH, no secrets, by the card). No PR yet, so no CI result.

## Assumptions made
- `verdict: pass` follows the KAN-317 review: STORY-010 has no automated test and the user accepted the deviation
  from AC-010.2 to AC-010.6. Under the strict rule of the evaluate skill (a criterion without a test fails) this
  would be a fail; the user decides.
- Issue 5: skipping a superseded commit means that when the newest commit's CI fails, staging stays on the older
  deployed commit. Proposed as the simpler rule.
- Proposed feature status: `staging-cd` stays `in-progress` until issues 1 to 4 are fixed; no re-review needed
  beyond a new shellcheck run if they are fixed as written.
