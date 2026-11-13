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
