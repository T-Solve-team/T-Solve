#!/usr/bin/env bash
# Creates the first admin of the staging stack: an ACTIVE Account (app_user) with the ADMIN Role (user_role) in
# the app database. Accounts are never created by sign-in, so without this row nobody can enter the company site.
# Safe to run again: if the email already exists, nothing changes.
#
#   FIRST_ADMIN_EMAIL=you@company.com FIRST_ADMIN_NAME="Your Name" infra/scripts/seed-first-admin.sh
#
# FIRST_ADMIN_EMAIL / FIRST_ADMIN_NAME are read from the environment, else from the env file
# (TSOLVE_ENV_FILE, default /srv/tsolve/staging/.env). The Workspace has its own database (one database per
# Workspace), so the row needs no Workspace id; the email must be on the Workspace's Google domain.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
env_file="${TSOLVE_ENV_FILE:-/srv/tsolve/staging/.env}"
cd "$root"
compose=(docker compose -f infra/compose.staging.yml --env-file "$env_file")

# Reads NAME from the env file without sourcing it (values may hold spaces or $).
from_file() {
    [ -f "$env_file" ] || return 0
    local line
    line="$(grep -E "^$1=" "$env_file" | tail -n 1 || true)"
    line="${line#*=}"
    line="${line%$'\r'}"   # a file edited on Windows ends its lines with CR
    line="${line%\"}"; line="${line#\"}"; line="${line%\'}"; line="${line#\'}"
    printf '%s' "$line"
}
pick() { local v="${!1:-}"; [ -n "$v" ] || v="$(from_file "$1")"; printf '%s' "$v"; }

email="$(pick FIRST_ADMIN_EMAIL)"
name="$(pick FIRST_ADMIN_NAME)"
db_name="$(pick DB_NAME)"
db_user="$(pick DB_USER)"
db_password="$(pick DB_PASSWORD)"

for pair in "FIRST_ADMIN_EMAIL:$email" "FIRST_ADMIN_NAME:$name" "DB_NAME:$db_name" "DB_USER:$db_user" "DB_PASSWORD:$db_password"; do
    [ -n "${pair#*:}" ] || { echo "seed-first-admin: ${pair%%:*} is empty (set it in $env_file or the environment)" >&2; exit 1; }
done
case "$email" in
    *@*.*) ;;
    *) echo "seed-first-admin: FIRST_ADMIN_EMAIL is not an email address" >&2; exit 1 ;;
esac
[ "${#name}" -le 100 ] || { echo "seed-first-admin: FIRST_ADMIN_NAME is longer than 100 characters" >&2; exit 1; }

# The app is the Liquibase owner of the tables; the web process must have started once (backend healthy).
# PGPASSWORD goes by name (-e PGPASSWORD) from the exported variable, so the value is not on any command line.
export PGPASSWORD="$db_password"
"${compose[@]}" exec -T -e PGPASSWORD db \
    psql -v ON_ERROR_STOP=1 -U "$db_user" -d "$db_name" -h 127.0.0.1 \
    -v email="$email" -v fullname="$name" <<'SQL'
WITH new_account AS (
    INSERT INTO app_user (email, full_name, status, created_at)
    VALUES (lower(:'email'), :'fullname', 'ACTIVE', now())
    ON CONFLICT (email) DO NOTHING
    RETURNING id
), new_role AS (
    INSERT INTO user_role (user_id, role, assigned_at)
    SELECT id, 'ADMIN', now() FROM new_account
    RETURNING user_id
)
SELECT CASE WHEN EXISTS (SELECT 1 FROM new_role)
            THEN 'created first admin ' || lower(:'email')
            ELSE 'account ' || lower(:'email') || ' already exists, nothing changed' END AS result;
SQL
