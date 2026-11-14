# Staging deploy (one VPS, Docker Compose)

One Compose project runs everything: Caddy (HTTPS), Postgres (pgvector), Redis, Keycloak, the backend (web), the
worker, the web app and Ollama. Only Caddy faces the internet (80, 443). Files:

| File | What |
|---|---|
| `compose.staging.yml` | the stack |
| `staging.env.example` | template of the one env file |
| `caddy/Caddyfile` | hosts and routing |
| `keycloak/staging/` | realm files and the Google redirect URIs (`README.md`) |
| `postgres/init/10-tsolve-dbs.sh` | creates the databases and users |
| `scripts/seed-first-admin.sh` | creates the first admin Account |

Hosts (all under `TSOLVE_DOMAIN`): `<slug>.<domain>` (company site and `/api`), `admin.<domain>` (Platform Console and
`/api`), `login.<domain>` (Keycloak, `/admin` blocked), `api.<domain>` (Jira app and health only).

Sizing: the memory limits add up to about 12.4 GB (db 2g, keycloak 1.5g, backend 1.5g, worker 1g, ollama 6g,
redis 192m, caddy 128m, web 64m), meant for a 16 GB box. Change them in the env file (`*_MEM_LIMIT`).

## 1. Prerequisites

- A VPS with Docker Engine and the Compose plugin, about 16 GB RAM, ports 80 and 443 (tcp, and 443 udp) open to the
  internet, and nothing else listening on them.
- DNS A records to the VPS for: `<domain>`, `www.<domain>`, `login.<domain>`, `admin.<domain>`, `api.<domain>` and
  every company host (`<slug>.<domain>`). No wildcard is needed: Caddy gets one certificate per host.
- A Google OAuth client with these Authorized redirect URIs (details: `keycloak/staging/README.md`):
  - `https://login.<domain>/realms/tsolve/broker/google-<slug>/endpoint`
  - `https://login.<domain>/realms/tsolve-ops/broker/google-operators/endpoint`

## 2. Clone

```bash
sudo mkdir -p /srv/tsolve && sudo chown "$USER" /srv/tsolve
git clone https://github.com/<org>/T-Solve.git /srv/tsolve/repo
cd /srv/tsolve/repo        # all commands below run from here
```

## 3. Create the env file

```bash
mkdir -p /srv/tsolve/staging
cp infra/staging.env.example /srv/tsolve/staging/.env
chmod 600 /srv/tsolve/staging/.env
```

Edit it: the `[GEN]` values are secrets you generate (`openssl rand -hex 32`; the UUIDs with `uuidgen`), the `[FILL]`
values are yours (domain, slug, Google client, emails). Keep `SEARCH_DEV_SCOPE` empty. The Workspace UUID is the same
in `TSOLVE_STUB_WORKSPACES` (field 3), `TSOLVE_MOCK_ACTIVE_WORKSPACES` and `TSOLVE_TRACKER_DEV_WORKSPACE_ID`; field 4
of the stub is the Google domain and field 5 equals `TSOLVE_DEMO_ORG_ID`. Compose reads this one file.

To keep the file elsewhere, also run `export TSOLVE_ENV_FILE=/path/to/.env` in your shell. Shorthand used below:

```bash
dc() { docker compose -f infra/compose.staging.yml --env-file "${TSOLVE_ENV_FILE:-/srv/tsolve/staging/.env}" "$@"; }
```

## 4. Start

```bash
dc up -d --build --wait
```

The first run builds the backend and the web image (several minutes) and starts the services in order: db, redis,
keycloak (imports both realms), backend (runs Liquibase), worker. `--wait` returns when every service is healthy.
`dc ps` shows the state.

The databases are created by `postgres/init/10-tsolve-dbs.sh` **only when the `db-data` volume is empty** (first
start). Changing a database password in the env file later does not change it in Postgres: change it there too
(`dc exec db psql -U postgres -c "ALTER ROLE tsolve PASSWORD '...'"`), or reset the volume (loses all data).

## 5. Pull the AI models

```bash
dc --profile models run --rm ollama-pull
```

This downloads `OLLAMA_EMBED_MODEL` (default `bge-m3`) and `OLLAMA_CHAT_MODEL` into the `ollama-data` volume; it is
not part of `up`. Run it again whenever you change a model name.

## 6. Create the first admin

Accounts are never created by sign-in. Set `FIRST_ADMIN_EMAIL` and `FIRST_ADMIN_NAME` in the env file, then:

```bash
infra/scripts/seed-first-admin.sh
```

It inserts an ACTIVE account with the ADMIN role and does nothing if the email already exists (safe to repeat).
Use an address on the company's Google domain.

## 7. Smoke checks

```bash
D=<domain>; S=<slug>
curl -fsS https://api.$D/actuator/health                                   # {"status":"UP"}
curl -fsS https://$S.$D/ | head -c 200                                     # the web app's index.html
curl -fsS https://login.$D/realms/tsolve/.well-known/openid-configuration | grep -o '"issuer":"[^"]*"'
curl -s -o /dev/null -w '%{http_code}\n' https://login.$D/admin/           # 404 (blocked)
dc ps                                                                      # every service healthy
```

Then open `https://<slug>.<domain>` and sign in with the first admin's Google account.

## 8. Update

```bash
git pull
dc up -d --build --wait
```

Compose rebuilds the changed images and recreates only the changed services. Liquibase migrates at backend start.

## 9. Keycloak Admin Console (SSH tunnel)

The console is not reachable on `login.<domain>`. Keycloak listens on the VPS loopback only (`127.0.0.1:8081`):

```bash
ssh -L 8081:127.0.0.1:8081 <user>@<vps>
# then open http://localhost:8081/admin/master/console/   (plain /admin/ answers 404)
```

The keycloak service sets `KC_HOSTNAME_ADMIN=http://localhost:8081`, so the console builds its own links on the
tunnel address (`http://localhost:8081`), not on `login.<domain>` where Caddy answers 404. Keep the local port 8081.

The bootstrap admin is not created by the stack: create one once with
`dc exec keycloak /opt/keycloak/bin/kc.sh bootstrap-admin user` (it asks for a password), or set
`KC_BOOTSTRAP_ADMIN_USERNAME` and `KC_BOOTSTRAP_ADMIN_PASSWORD` on the keycloak service for the first start.

## 10. Reset a realm

The realm import runs with `IGNORE_EXISTING`: a realm that already exists in the database is skipped, so editing
`realm-*.json` has no effect on a running system. Change values in the Admin Console, or delete the realm there
(Realm settings, Action, Delete) and restart Keycloak to import the file again:

```bash
dc restart keycloak
```

Deleting a realm deletes its users and sessions. This stack has no company provisioning (the web process runs without
the `ops` profile): a new company means editing the env file, the realm file and the Caddy hosts by hand, and this
stack serves exactly one company (`TSOLVE_STUB_WORKSPACES` accepts one entry).

## 11. Logs and housekeeping

```bash
dc logs -f --tail 200 backend          # or worker, keycloak, caddy, db, ollama
dc ps
docker stats --no-stream
```

Each container keeps at most 3 files of 10 MB. Stop without losing data: `dc down`. `dc down -v` also deletes the
volumes (database, Redis, models, certificates): only for a full reset.

## 12. Continuous deploy (CD)

`.github/workflows/cd-staging.yml` deploys staging by itself. It starts when the `CI` workflow finishes successfully
for a push to `main` (never for a pull request), or by hand ("Run workflow", optional `sha`). A GitHub-hosted job
connects over SSH with a dedicated deploy key and sends the commit SHA. The key can run one program only,
`/usr/local/sbin/tsolve-deploy` (source: `infra/scripts/deploy.sh`), which:

1. refuses a SHA that is not on `origin/main`, checks the commit out in `/srv/tsolve/src` (detached) and prints the
   previous commit (the one in `DEPLOYED`, the last successful deploy);
2. fails, naming the variables, when `/srv/tsolve/staging/.env` lacks a name that `staging.env.example` has (it never
   prints values);
3. builds `backend` and `web`, then `up -d --wait --no-deps backend worker web` (so the compose file of the new commit
   does apply to these three); recreates `caddy` when `infra/caddy/` changed, or when no valid `DEPLOYED` exists;
4. runs the smoke checks (`api.<domain>/actuator/health` UP, `<slug>.<domain>/` 200, `<slug>.<domain>/api/v1/me` 401
   with `"code":"MSG07"`), removes dangling images (`docker image prune -f`, never volumes), writes the SHA to
   `/srv/tsolve/staging/DEPLOYED` and a line per step to `/srv/tsolve/staging/deploy.log`.

A run for a push that is no longer the head of `main` (a late CI run) is skipped with a notice and stays green.

### Trust model

- The forced command only limits a stolen key to "deploy a commit of `main`".
- The protection of `main` (required reviews and checks) is the real boundary: every deploy builds and runs code from
  `main` (Dockerfiles, and the compose file for backend, worker and web) as a member of the `docker` group, which is
  root-equivalent on the VPS and can read `.env`.
- `~deploy/.ssh/authorized_keys` is owned by `deploy`, so anyone who gets a shell as `deploy` can change the key's
  options. Recommended: a root-owned `AuthorizedKeysFile` for this user (`/etc/ssh/authorized_keys/%u`, mode 644, owned
  by root). Otherwise accept this limit.
- The script lives in `/usr/local/sbin`, outside `/srv/tsolve` (which belongs to the login user), so `deploy` cannot
  replace it.
- The `staging` environment's deployment-branch rule "main only" is load-bearing (setup step 3): without it, any branch
  could use the secrets.

There is no automatic migrate, seed or rollback: Liquibase runs at backend start (section 8); the AI models and the
first admin stay manual (sections 5 and 6). One deploy runs at a time (a second one fails at once).

### One-time setup

1. On the VPS, as root: `install -o root -g root -m 755 infra/scripts/deploy.sh /usr/local/sbin/tsolve-deploy`. The
   `deploy` user must not be able to write it.
2. The `deploy` user (member of the `docker` group, owner of `/srv/tsolve/src` and `/srv/tsolve/staging`) gets the
   key. On your machine: `ssh-keygen -t ed25519 -f tsolve-deploy -C tsolve-cd-staging -N ""`. In
   `~deploy/.ssh/authorized_keys` add the public key on one line:

   ```
   restrict,command="/usr/local/sbin/tsolve-deploy" ssh-ed25519 AAAA... tsolve-cd-staging
   ```

   (See "Trust model" for who may edit this file.)
3. In GitHub, Settings, Environments: create `staging`. **Required:** set Deployment branches to "Selected branches" and
   allow only `main`. Then add these secrets:
   `STAGING_SSH_HOST`, `STAGING_SSH_USER` (`deploy`), `STAGING_SSH_KEY` (the private key file) and
   `STAGING_SSH_KNOWN_HOSTS`. Then delete the private key from your machine.
4. For `STAGING_SSH_KNOWN_HOSTS` (the host, `tsolve.io.vn`, has an RSA host key only) run
   `ssh-keyscan -t rsa <host>`, and compare the fingerprint of the scanned line (pipe it to `ssh-keygen -lf -`) with
   `ssh-keygen -lf /etc/ssh/ssh_host_rsa_key.pub` run on the server. Paste the line only when they match. Windows
   OpenSSH's `ssh-keyscan` may fail with an unsupported key exchange: use the one from Git Bash.

### Redeploy or roll back

Actions, "CD staging", "Run workflow", and enter the `sha` (empty means the latest `main`). To roll back, enter the
previous SHA from the run summary or `deploy.log`; it must still be on `main`. A rollback does not undo database
migrations.

### When the script changes

A merged PR does not change the script the key runs. After `infra/scripts/deploy.sh` changes, reinstall it as root (the
`install` command of step 1) from the checked-out repo.

### "Manual step needed" warning

Backend, worker and web are deployed (the new compose file applies to them), but `compose.staging.yml` (for the other
services), a `keycloak/(staging/)realm-*.json` file, `keycloak/themes/` or `postgres/init/` changed and CD did not apply
it. With no valid `DEPLOYED` file the warning lists every such file. Read the change (`git -C /srv/tsolve/src diff <previous> <sha> -- <file>`) and apply
it by hand as in sections 4, 8 and 10 (for the compose file, `dc up -d --wait`; a realm file needs a realm reset).
