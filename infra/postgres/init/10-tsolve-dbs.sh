#!/bin/sh
# Creates the T-Solve databases and users in a NEW Postgres data volume (the official image runs every file in
# /docker-entrypoint-initdb.d only when the data directory is empty; it never re-runs on a volume with data).
# Each user owns its own database. Passwords come from the container environment (infra/compose.staging.yml).
#   app:      DB_NAME / DB_USER / DB_PASSWORD
#   keycloak: KEYCLOAK_DB_NAME / KEYCLOAK_DB_USER / KEYCLOAK_DB_PASSWORD
#   jobs:     JOBS_DB_NAME / JOBS_DB_USER / JOBS_DB_PASSWORD   (JobRunr, a separate database)
set -eu

create_db() { # name user password
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v dbname="$1" -v dbuser="$2" -v dbpw="$3" <<'SQL'
CREATE ROLE :"dbuser" LOGIN PASSWORD :'dbpw';
CREATE DATABASE :"dbname" OWNER :"dbuser";
SQL
}

create_db "$DB_NAME" "$DB_USER" "$DB_PASSWORD"
create_db "${KEYCLOAK_DB_NAME:-keycloak}" "${KEYCLOAK_DB_USER:-keycloak}" "$KEYCLOAK_DB_PASSWORD"
create_db "${JOBS_DB_NAME:-tsolve_staging_jobs}" "$JOBS_DB_USER" "$JOBS_DB_PASSWORD"

# pgvector: creating the extension needs the superuser, so it is done here (the Liquibase changelog's
# CREATE EXTENSION IF NOT EXISTS then finds it and does nothing).
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$DB_NAME" \
    -c "CREATE EXTENSION IF NOT EXISTS vector"
