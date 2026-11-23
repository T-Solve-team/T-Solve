#!/bin/sh
# Creates the Registry database and its owner on an EXISTING staging volume (the init script in infra/postgres/init
# runs only on an empty one). Run it once, from the repo root on the VPS, after the env file has the REGISTRY_DB_* lines
# and the db container has been recreated with them:
#   docker compose -f infra/compose.staging.yml --env-file /srv/tsolve/staging/.env exec -T db sh -s < infra/scripts/registry-db-init.sh
# The variables come from the db container environment. Idempotent: an existing role is left as it is (no ALTER ROLE,
# so a changed REGISTRY_DB_PASSWORD is NOT applied), and only a missing database is created. Prints no password.
set -eu

for v in POSTGRES_USER POSTGRES_PASSWORD REGISTRY_DB_USER REGISTRY_DB_PASSWORD; do
    eval "val=\${$v:-}"
    if [ -z "$val" ]; then
        echo "registry-db-init: $v is not set (or empty); nothing was created" >&2
        exit 1
    fi
done
: "${REGISTRY_DB_NAME:=tsolve_staging_registry}"

# The SQL comes from a heredoc, so psql never reads the script's own stdin (`sh -s`).
exists_role() {
    [ "$(psql -X -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -Atq -v n="$REGISTRY_DB_USER" \
        <<'SQL'
select 1 from pg_roles where rolname = :'n'
SQL
    )" = 1 ]
}
exists_db() {
    [ "$(psql -X -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -Atq -v n="$REGISTRY_DB_NAME" \
        <<'SQL'
select 1 from pg_database where datname = :'n'
SQL
    )" = 1 ]
}

if exists_role; then
    role_note="role $REGISTRY_DB_USER already exists, left as it is"
else
    psql -X -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v dbuser="$REGISTRY_DB_USER" -v dbpw="$REGISTRY_DB_PASSWORD" <<'SQL' >/dev/null
CREATE ROLE :"dbuser" LOGIN PASSWORD :'dbpw';
SQL
    role_note="role $REGISTRY_DB_USER created"
fi

if exists_db; then
    db_note="database $REGISTRY_DB_NAME already exists, left as it is"
else
    psql -X -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v dbname="$REGISTRY_DB_NAME" -v dbuser="$REGISTRY_DB_USER" <<'SQL' >/dev/null
CREATE DATABASE :"dbname" OWNER :"dbuser";
SQL
    db_note="database $REGISTRY_DB_NAME created, owned by $REGISTRY_DB_USER"
fi

echo "registry-db-init: $role_note; $db_note"
