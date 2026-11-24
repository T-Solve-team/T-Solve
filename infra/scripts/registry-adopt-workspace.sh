#!/usr/bin/env bash
# Adopts an existing Workspace (staging bluefin, or a local Workspace) into the Tenant Registry database
# (tenant_workspace). Reads variables from the environment, else from TSOLVE_ENV_FILE without sourcing it, encrypts
# DB_PASSWORD inside the backend container with RegistryPasswordCli, and copies the Jira site (both columns or neither,
# cloud id lower-cased) from the tenant workspace table when present. Idempotent: a rerun with matching fields changes
# nothing; a mismatch on any of the five identity fields (site_name, google_domain, keycloak_organization_id, db_name,
# jira_site_url/jira_cloud_id) is refused without modifying the row. Prints no secret and passes no secret on argv.
#
#   infra/scripts/registry-adopt-workspace.sh
#   TSOLVE_COMPOSE_FILE=<compose> TSOLVE_ENV_FILE=<env> infra/scripts/registry-adopt-workspace.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
compose_file="${TSOLVE_COMPOSE_FILE:-$root/infra/compose.staging.yml}"
env_file="${TSOLVE_ENV_FILE:-/srv/tsolve/staging/.env}"
export TSOLVE_ENV_FILE="$env_file"
compose=(docker compose -f "$compose_file" --env-file "$env_file")

# Reads NAME from the env file without sourcing it (values may hold spaces or $).
from_file() {
    [ -f "$env_file" ] || return 0
    local line
    line="$(grep -E "^$1=" "$env_file" | tail -n 1 || true)"
    line="${line#*=}"
    line="${line%$'\r'}"
    line="${line%\"}"; line="${line#\"}"; line="${line%\'}"; line="${line#\'}"
    printf '%s' "$line"
}
pick() {
    local v="${!1:-}"
    [ -n "$v" ] || v="$(from_file "$1")"
    printf '%s' "$v"
}

ws_id="$(pick TSOLVE_DEMO_WORKSPACE_ID)"
slug="$(pick TSOLVE_DEMO_SLUG)"
google_domain="$(pick TSOLVE_DEMO_GOOGLE_DOMAIN)"
org_id="$(pick TSOLVE_DEMO_ORG_ID)"
db_name="$(pick DB_NAME)"
db_user="$(pick DB_USER)"
db_password="$(pick DB_PASSWORD)"
catalog_key="$(pick CATALOG_SECRET_KEY)"
reg_user="$(pick REGISTRY_DB_USER)"
reg_db="$(pick REGISTRY_DB_NAME)"
: "${reg_db:=tsolve_staging_registry}"

for pair in \
    "TSOLVE_DEMO_WORKSPACE_ID:$ws_id" \
    "TSOLVE_DEMO_SLUG:$slug" \
    "TSOLVE_DEMO_GOOGLE_DOMAIN:$google_domain" \
    "TSOLVE_DEMO_ORG_ID:$org_id" \
    "DB_NAME:$db_name" \
    "DB_USER:$db_user" \
    "DB_PASSWORD:$db_password" \
    "CATALOG_SECRET_KEY:$catalog_key" \
    "REGISTRY_DB_USER:$reg_user"; do
    name="${pair%%:*}"
    val="${pair#*:}"
    if [[ -z "${val//[[:space:]]/}" ]]; then
        echo "registry-adopt-workspace: $name is not set (or empty)" >&2
        exit 1
    fi
done

if ! LC_ALL=C grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' <<<"$ws_id"; then
    echo "registry-adopt-workspace: TSOLVE_DEMO_WORKSPACE_ID must be a valid UUID" >&2
    exit 1
fi
ws_id="$(printf '%s' "$ws_id" | tr '[:upper:]' '[:lower:]')"

if ! LC_ALL=C grep -Eq '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$' <<<"$slug"; then
    echo "registry-adopt-workspace: TSOLVE_DEMO_SLUG must be a lower-case DNS label (a-z, 0-9, hyphen)" >&2
    exit 1
fi

google_domain="$(printf '%s' "$google_domain" | tr '[:upper:]' '[:lower:]')"

tmp_err="$(mktemp)"
cleanup() {
    rm -f "$tmp_err"
}
trap cleanup EXIT

read_tenant_jira() {
    local row count addr cloud _end
    export PGPASSWORD="$db_password"
    row="$("${compose[@]}" exec -T -e PGPASSWORD db \
        psql -X -v ON_ERROR_STOP=1 -U "$db_user" -d "$db_name" -Atq -F '|' <<'SQL'
SELECT count(*)::text,
       coalesce(max(btrim(jira_site_address)), ''),
       coalesce(max(btrim(jira_cloud_id)), ''),
       'END'
FROM workspace;
SQL
    )"
    unset PGPASSWORD
    row="${row%$'\r'}"
    IFS='|' read -r count addr cloud _end <<<"$row"
    tenant_ws_count="${count:-0}"
    if [[ -n "$addr" && -n "$cloud" ]]; then
        jira_site_url="$addr"
        jira_cloud_id="$(printf '%s' "$cloud" | tr '[:upper:]' '[:lower:]')"
    else
        jira_site_url=""
        jira_cloud_id=""
    fi
}

# Check whether this Workspace id is already in the Registry (local socket as REGISTRY_DB_USER; no REGISTRY_DB_PASSWORD).
existing_row="$("${compose[@]}" exec -T db \
    psql -X -v ON_ERROR_STOP=1 -U "$reg_user" -d "$reg_db" -Atq -F '|' -v ws_id="$ws_id" <<'SQL'
SELECT site_name,
       google_domain,
       coalesce(keycloak_organization_id, ''),
       db_name,
       coalesce(jira_site_url, ''),
       coalesce(jira_cloud_id, ''),
       'END'
FROM tenant_workspace
WHERE workspace_id = :'ws_id'::uuid;
SQL
)"
existing_row="${existing_row%$'\r'}"

if [[ -n "$existing_row" ]]; then
    IFS='|' read -r ex_site_name ex_google_domain ex_org_id ex_db_name ex_jira_url ex_jira_cloud _end <<<"$existing_row"

    if [[ "$ex_site_name" != "$slug" ]]; then
        echo "registry-adopt-workspace: existing row for $ws_id differs on site_name; nothing changed" >&2
        exit 1
    fi
    if [[ "$ex_google_domain" != "$google_domain" ]]; then
        echo "registry-adopt-workspace: existing row for $ws_id differs on google_domain; nothing changed" >&2
        exit 1
    fi
    if [[ "$ex_org_id" != "$org_id" ]]; then
        echo "registry-adopt-workspace: existing row for $ws_id differs on keycloak_organization_id; nothing changed" >&2
        exit 1
    fi
    if [[ "$ex_db_name" != "$db_name" ]]; then
        echo "registry-adopt-workspace: existing row for $ws_id differs on db_name; nothing changed" >&2
        exit 1
    fi

    read_tenant_jira
    if [[ "$ex_jira_url" != "$jira_site_url" ]]; then
        echo "registry-adopt-workspace: existing row for $ws_id differs on jira_site_url; nothing changed" >&2
        exit 1
    fi
    if [[ "$ex_jira_cloud" != "$jira_cloud_id" ]]; then
        echo "registry-adopt-workspace: existing row for $ws_id differs on jira_cloud_id; nothing changed" >&2
        exit 1
    fi

    echo "registry-adopt-workspace: workspace $ws_id already adopted; nothing changed"
    exit 0
fi

# Encrypt DB_PASSWORD in the backend container (no TTY; small heap so the live backend is not OOM-killed).
export CATALOG_SECRET_KEY="$catalog_key"
export DB_PASSWORD="$db_password"
export TSOLVE_DEMO_WORKSPACE_ID="$ws_id"
if ! encrypted_pw="$("${compose[@]}" exec -T \
    -e CATALOG_SECRET_KEY \
    -e DB_PASSWORD \
    -e TSOLVE_DEMO_WORKSPACE_ID \
    backend java -Xmx64m -cp /app/app.jar \
    -Dloader.main=vn.tsolve.platform.registry.RegistryPasswordCli \
    org.springframework.boot.loader.launch.PropertiesLauncher 2>"$tmp_err")"; then
    unset CATALOG_SECRET_KEY DB_PASSWORD TSOLVE_DEMO_WORKSPACE_ID
    cat "$tmp_err" >&2
    exit 1
fi
unset CATALOG_SECRET_KEY DB_PASSWORD TSOLVE_DEMO_WORKSPACE_ID
encrypted_pw="${encrypted_pw%$'\r'}"

if [[ "$(wc -l <<<"$encrypted_pw")" -ne 1 || "$encrypted_pw" != v1:* ]]; then
    echo "registry-adopt-workspace: password encryption returned an unexpected format" >&2
    exit 1
fi

export PGPASSWORD="$db_password"
schema_version="$("${compose[@]}" exec -T -e PGPASSWORD db \
    psql -X -v ON_ERROR_STOP=1 -U "$db_user" -d "$db_name" -Atq <<'SQL'
SELECT id FROM databasechangelog ORDER BY orderexecuted DESC, dateexecuted DESC LIMIT 1;
SQL
)"
unset PGPASSWORD
schema_version="${schema_version%$'\r'}"
if [[ -z "$schema_version" ]]; then
    echo "registry-adopt-workspace: databasechangelog in $db_name is empty; nothing written" >&2
    exit 1
fi

read_tenant_jira

export ENC_DB_PASSWORD="$encrypted_pw"
"${compose[@]}" exec -T -e ENC_DB_PASSWORD db \
    psql -X -v ON_ERROR_STOP=1 -U "$reg_user" -d "$reg_db" -Atq \
    -v ws_id="$ws_id" \
    -v site_name="$slug" \
    -v db_name="$db_name" \
    -v db_user="$db_user" \
    -v schema_version="$schema_version" \
    -v jira_site_url="$jira_site_url" \
    -v jira_cloud_id="$jira_cloud_id" \
    -v google_domain="$google_domain" \
    -v org_id="$org_id" <<'SQL' >/dev/null
\getenv db_password ENC_DB_PASSWORD
INSERT INTO tenant_workspace (
    workspace_id,
    site_name,
    status,
    db_name,
    db_user,
    db_password,
    schema_version,
    jira_site_url,
    jira_cloud_id,
    google_domain,
    keycloak_organization_id
)
VALUES (
    :'ws_id'::uuid,
    :'site_name',
    'ACTIVE',
    :'db_name',
    :'db_user',
    :'db_password',
    :'schema_version',
    NULLIF(:'jira_site_url', ''),
    NULLIF(:'jira_cloud_id', ''),
    :'google_domain',
    :'org_id'
)
ON CONFLICT (workspace_id) DO NOTHING;
SQL
unset ENC_DB_PASSWORD

if [[ "$tenant_ws_count" == "0" ]]; then
    echo "registry-adopt-workspace: adopted $slug ($ws_id); tenant workspace table is empty, so no Jira site was set"
else
    echo "registry-adopt-workspace: adopted $slug ($ws_id)"
fi
