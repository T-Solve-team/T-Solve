---
module: platform
feature: kan-317-vps-deploy
task: T7 (KAN-322; cut-down of plan Tasks 2 / 13 / 14 + identity quick change KAN-327)
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: tsolve-code-reviewer
iteration: 1
---

# Review: platform — KAN-317: hand deploy of the staging stack to one VPS

Reviewed state: uncommitted worktree `T-Solve-vps-deploy`, branch `chore/KAN-317-platform-vps-deploy`, base
`945d4354`. Verdict in the card's terms: **PASS WITH FIXES** (no blocker; 3 major fixes before the deploy).

## Summary
The stack is sound for ONE demo company. Sign-in config holds: exact redirect and post-logout URIs (no wildcard),
empty web origins, confidential clients with PKCE, no direct grants, client secrets only from the environment, Caddy
overwrites `X-Forwarded-*` (no `trusted_proxies`) and strips `Forwarded` / `X-Forwarded-Prefix`, the backend and
Keycloak publish nothing to the world (Keycloak only on `127.0.0.1:8081`), the `__Host-` cookies are `Secure` and the
sites are HTTPS-only with HSTS. The stub lookup fails closed. Both SQL scripts quote through psql variables
(`:'x'`, `:"x"`), so no injection. Three things must be fixed before the deploy, listed under Issues.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-009.2, .4, .5, .6, .7, .8, .9, .11 are met by `infra/caddy/Caddyfile` (read, not executed). AC-009.1 partly (explicit hosts, a new company needs an edit and a restart). AC-009.10 not met (see Assumptions) |
| Completeness | pass with deviations | Cut-down by design: no CI/CD, no basic auth, Ollama in the stack (against AC-008.8), no Garage. Needs the user's acceptance |
| Correctness | pass | `NoWorkspaceLookupTest` 10/10, Spotless clean, compose file valid; majors 1-3 open |
| Integration | pass | Env names in `staging.env.example` match `application.yml`; staging realms equal the dev realms except Google, URLs and `sslRequired` |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace platform STORY-009` → `0/10 acceptance criteria have a test`. The routing is
configuration; its only check is the manual smoke list in `infra/README.md` §7. By the harness rule this is a fail;
for a hand deploy before the planned Tasks 13/14 it is recorded as an open judgment call instead.

## Issues

### Major (fix before the deploy)
1. `infra/staging.env.example:14,45` and `.env.example:60-63` (also `NoWorkspaceLookup.java:46-64`): the docs invite
   several companies ("one per Workspace", "Several companies: separate the entries with ;"), but every entry uses
   the same app database (`LocalTenantScope` only binds a thread; there is no per-Workspace datasource). A second
   company would read and write the first company's Accounts, tickets and knowledge.
   Fix: say "exactly one entry on this stack" in both files and the README, or make the stub refuse more than one
   entry outside tests.
2. `infra/scripts/seed-first-admin.sh:1` (and `infra/postgres/init/10-tsolve-dbs.sh:1`): new files on Windows with
   `core.fileMode=false`; the repo tracks no other `.sh`, so they will be committed as 100644. README §6
   (`infra/README.md:86`) then fails with "Permission denied".
   Fix: `git add --chmod=+x` both files (or write `bash infra/scripts/seed-first-admin.sh` in the README).
3. `infra/compose.staging.yml:136`: `KC_HOSTNAME` is a full URL and no `KC_HOSTNAME_ADMIN` is set, so Keycloak
   builds the Admin Console URLs on `https://login.<domain>/admin/...`, which Caddy answers with 404. The SSH tunnel
   of README §9 (`infra/README.md:114-125`) most likely cannot open the console (not run here: verify on the box).
   Fix: add `KC_HOSTNAME_ADMIN: http://localhost:8081` to the keycloak service.

### Minor
4. `infra/caddy/Caddyfile:93`: a deny-list for `/admin`. Keycloak's proxy guide recommends an allow-list.
   Fix: proxy only `/realms/*`, `/resources/*`, `/robots.txt`, answer 404 otherwise, and also 404 `/realms/master/*`
   (the master login and the `admin-cli` password grant are public today).
5. `infra/compose.staging.yml:19-21`: `env_file` hands the whole file to backend and worker, including
   `POSTGRES_PASSWORD` (superuser), `KEYCLOAK_DB_PASSWORD`, `GOOGLE_CLIENT_SECRET`, `KEYCLOAK_ADMIN_CLIENT_SECRET`.
   Fix: blank them in the two services' `environment:` or use a separate backend env file.
6. `infra/compose.staging.yml:101-104`: the Redis password is a command argument (visible in `docker inspect` and
   the process list). Fix: `sh -c 'exec redis-server --requirepass "$$REDIS_PASSWORD" ...'` with the value in
   `environment:`.
7. `infra/keycloak/staging/realm-tsolve-ops.json:89-103`: no `hostedDomain`, so any Google account becomes a realm
   user and enrols TOTP before the backend refuses it. The gate itself is sound (`email_verified` check, exact
   allow-list match, OTP at every login, no cookie step, brute-force protection). Left over: junk users, and TOTP is
   set by whoever signs in first with an operator's Google account.
   Fix: set `hostedDomain` if the operators share one domain; otherwise accept and note it.
8. `infra/keycloak/staging/realm-tsolve.json:4`, `realm-tsolve-ops.json:4`: `sslRequired: external` lets plain HTTP
   through from private addresses. Fix: `all` (every call goes through Caddy over HTTPS, the backend included).
9. `infra/compose.staging.yml:56,98,243`: `pgvector/pgvector:pg16` and `redis:7.4-alpine` are floating tags; Ollama
   runs as root; `ollama-pull` (line 262) has no memory limit. Fix: pin patch tags; accept root for Ollama or note it.
10. `infra/caddy/Caddyfile:36-39`: the access log holds full query strings (OAuth `code`, `state`) and client IPs.
    Fix: accept for the demo, or drop the query with a log filter.
11. `infra/scripts/seed-first-admin.sh:45`: `-e PGPASSWORD=<value>` is on the host's process list for a moment; and
    `:19-26` does not strip a trailing CR from a file edited on Windows. Fix: export `PGPASSWORD` and pass
    `-e PGPASSWORD`; strip `\r` in `from_file`.
12. `infra/README.md:137-138`, `infra/keycloak/staging/README.md:37-38`: they speak of companies added through the
    Platform Console, but the web process runs without the `ops` profile, so no provisioning exists here (and the
    `/admin` block would stop its Admin API calls). Fix: delete or reword.
13. Naming: `NoWorkspaceLookup` now knows Workspaces; the test display names say KAN-317 while the quick change is
    KAN-327; `harnessclaude/progress.json` records branch `feat/platform` for this work.

### Checked and fine
- Stub lookup: empty, blank or `;`-only value → no Workspace (sign-in start 404, requests anonymous); a wrong field
  count, blank field, bad UUID or duplicate → startup fails; the host match is exact (lower case, port dropped), no
  suffix or wildcard match; the error message names only the host.
- Seed and init scripts: values reach SQL only through `psql -v` with `:'x'` / `:"x"`; the env file is not sourced.
- `docker compose config` prints every secret resolved: expected, do not paste its output anywhere.

## User decisions (13/11)
- Accepted for the demo, no change: findings 7 (no `hostedDomain` on the operators IdP), 9 (floating image tags,
  Ollama as root, no memory limit on `ollama-pull`), 10 (query strings in the access log).
- Accepted spec deviations of the one-VPS cut-down: AC-009.10 (no basic auth on staging), AC-008.8 (Ollama in the
  staging stack), AC-009.1 (a new company is a manual edit of env, realm and Caddy hosts).
- Fixed in the T7b round: findings 1-6, 8, 11, 12, 13 (the stub lookup now accepts exactly one entry).

## Assumptions made
- AC-009.10 (staging sites behind basic auth or an IP allow-list) is left out on purpose for the public demo.
- AC-008.8 (no Ollama container on staging) and AC-009.1 (certificates for a new company with no manual step) are
  accepted deviations of the one-VPS cut-down.
- No developer verify result was given; only the two commands the card allowed were run.

## Test Report
- `mvn -q spotless:check test -Dtest=NoWorkspaceLookupTest` (backend): exit 0; surefire
  `tests="10" errors="0" skipped="0" failures="0"`.
- `docker compose -f infra/compose.staging.yml --env-file <dummy> config -q`: exit 0; published ports are 80, 443,
  443/udp and `127.0.0.1:8081` only. With `TSOLVE_SITE_HOSTS` missing it stops with "set TSOLVE_SITE_HOSTS in the
  env file".
- `docker manifest inspect`: the five pinned tags exist (caddy 2.11.7-alpine, keycloak 26.4.7, ollama 0.12.6,
  temurin 21.0.5_11-jre-alpine, maven 3.9.9-eclipse-temurin-21).
- Not run: the stack itself, the image builds, the realm import, the full suites (CI on the PR).
- Not verified: Major 3; that Keycloak keeps the Organization `id` on import; that the Caddy health check
  (`wget --spider` on the admin API, `compose.staging.yml:48`) answers 200.
