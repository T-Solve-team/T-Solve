---
module: platform
feature: staging-cd
task: KAN-14 follow-up, BUG-207 (quick change; own DOCKER_CONFIG, `docker config` preflight, SSH user/host as variables)
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: platform — KAN-14 / BUG-207: CD build fails on root-owned `~/.docker`

Reviewed state: uncommitted worktree `T-Solve-cd`, branch `fix/KAN-14-platform-cd-docker-config`, base `origin/main`
66d358e7. Files: `.github/workflows/cd-staging.yml`, `infra/scripts/deploy.sh`, `infra/README.md` section 12,
`docs/bug-log.md`, `infra/tests/deploy-docker-config.test.sh` (untracked). Verdict in the card's terms:
**APPROVE WITH FIXES** (no security blocker; fix issues 1 to 3 before the commit, 4 and 5 are small).

## Summary
The security of the first review is intact. The forced command, the 40-hex argument check and `cfg()` are untouched;
`DOCKER_CONFIG` is built from `$STATE`, which is fixed under `SSH_ORIGINAL_COMMAND`, so an environment value is
ignored (the test shows it). The new step logs only a path and a user name. The workflow has no new trigger,
permission or action; the key and `known_hosts` stay secrets, the host key stays pinned; the two variables are
checked by anchored regexes before `ssh` and are quoted. `export DOCKER_CONFIG` (deploy.sh:26) comes before every
docker call (lines 115, 122, 125, 139, 181). The open point is the host: nothing checks that the compose and buildx
plugins are still found once the config folder moves.

## Criteria
| Criterion | Result | Notes |
|---|---|---|
| Functionality | pass with fixes | An empty `DOCKER_CONFIG` builds (measured, see Test Report). Plugin lookup on the VPS is unverified (issue 1) |
| Completeness | pass with fixes | README lacks the rollout order (issue 3); bug-log row has no Owner (issue 5) |
| Correctness | pass with fixes | Test passes 4/4, but case (a) does not reach the `find` check (issue 2) |
| Integration | pass | Missing variables give a clear `::error::` and exit 1 before any key is written |

## Traceability
Quick change against STORY-010; no acceptance criterion changes, so `sdd.ts trace` was not run. Bug test:
`infra/tests/deploy-docker-config.test.sh`, check names start with `BUG-207`.

## Issues
1. `infra/scripts/deploy.sh:58-72` (and `:115`) — no check that `docker compose` and `docker buildx` still resolve.
   If they are installed per user in `~deploy/.docker/cli-plugins`, they vanish with the moved `DOCKER_CONFIG`.
   Reproduced in a `docker:cli` container: `docker: unknown command: docker compose`. The script would then stop at
   `compose file check` with "compose config is not valid (details hidden)", which points the wrong way.
   Fix: in step `docker config`, run `docker compose version` and `docker buildx version` (output to /dev/null) and
   fail with a hint to install the plugins system-wide (`docker-compose-plugin`, `docker-buildx-plugin`, i.e.
   `/usr/libexec/docker/cli-plugins`). Say the same in README step 5, with the command to check
   (`ls /usr/libexec/docker/cli-plugins /usr/local/lib/docker/cli-plugins ~deploy/.docker/cli-plugins`). Do not advise
   a symlink into `$DOCKER_CONFIG/cli-plugins`: `find ... ! -writable` follows it to a root-owned file and the
   preflight fails. Add a test case for a missing plugin.
2. `infra/tests/deploy-docker-config.test.sh:60-64` — case (a) leaves `/work/state/.docker` owned by root (only
   `buildx` is chowned), so `[[ ! -w "$DOCKER_CONFIG" ]]` trips first and the `find` clause, the real BUG-207 case
   (a root-owned file in a folder the user owns), is never tested. Measured: `root 755 /work/state/.docker`,
   "dir-NOT-writable". Fix: replace lines 62-64 with `chown -R deploy:deploy /work/state/.docker` then
   `chown root:root /work/state/.docker/buildx/.lock`; keep a separate case for the root-owned folder.
3. `infra/README.md:205-216` and `:241-243` — the rollout order is not written, and the old secrets are deleted at
   once. Fix: add an ordered list: (1) set the two variables before the merge; (2) merge: the first run still fails
   at the build because the VPS runs the old script (it has already checked out the new commit); (3) reinstall the
   script as root from `/srv/tsolve/src`; (4) "Run workflow"; (5) delete the two old secrets only after a green run
   (a revert of the PR needs them).
4. `infra/scripts/deploy.sh:60-68` — `mkdir -p` runs before the `-w "$STATE"` check, so on a read-only state folder
   the ERR trap fires and the chown hint for `$STATE` is never printed. Fix: move the `$STATE` check above the
   `mkdir`. Low.
5. `docs/bug-log.md:222` — Owner is empty; step 3 of the log asks for a full name. Put the PR number in "Fixed in"
   when the PR exists. Low.

Informational: `README.md:229-231` says the first build can be slower; with the default builder the build cache is
in the daemon, not in the config folder, so it is not. A `docker login` or a `docker buildx use` done by `deploy`
no longer applies (all images are public, so only the anonymous Docker Hub pull limit matters). The test is not run
by any workflow.

## Test Report
- `bash -n` on `deploy.sh` and the test: ok. `git diff --check origin/main`: clean.
- `bash infra/tests/deploy-docker-config.test.sh`: exit 0, 4 checks ok (a, b, b, c). Docker 29.2.1.
- Regexes, run in bash: user accepts `deploy`, `_svc`; rejects empty, `Deploy`, `a@b`, `de ploy`, `-oProxyCommand=x`.
  Host accepts `tsolve.io.vn`, an IPv4 address; rejects empty, a leading `-`, a space, `;`, a trailing dot or newline.
- Empty `DOCKER_CONFIG`, local probe (Compose v5.1.0, buildx v0.32.1): `config -q` exit 0, `compose build` exit 0
  with builder `default` (driver `docker`); the folder gets `buildx/.lock`, `current`, `defaults`, `instances`,
  `activity/`, `refs/`. Not the VPS versions.
- NOT rerun: `shellcheck -x` and `actionlint` (not installed here; running them from container images was refused by
  the permission system). The developer's results for both stand unverified. No PR yet, so no CI result.

## Assumptions made
- Where the plugins live on the VPS is unknown (no SSH in this review); issue 1 is rated on that uncertainty.
- Variables instead of secrets for user and host is no loss: both are public in the README, the host key is pinned.
