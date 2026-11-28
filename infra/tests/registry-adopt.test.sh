#!/usr/bin/env bash
# KAN-252 Task 4a-2: infra/scripts/registry-adopt-workspace.sh adopts bluefin into the Tenant Registry DB.
# Check names start with the id A1..A9.
#
#   A1 edge 14               refuses without a required variable (or with an invalid one), names it, writes nothing
#   A2 AC-001.15 AC-001.24   writes the bluefin row with an encrypted password and prints no password
#   A3 edge 77               a second run with the same values changes nothing
#   A4 edge 78               an existing row with other values is refused, the field named, nothing changed
#   A5 edge 79               a tenant row copies both Jira columns, the cloud id lower-case, or neither
#   A6 edge 79               an empty tenant workspace table gives no Jira site and one line saying so
#   A7 AC-001.17 AC-001.19   adopting creates nothing else and copies no business data
#   A8 edge 14               no secret on any docker command line or in any output
#   A9 edge 78               a tenant workspace_id other than TSOLVE_DEMO_WORKSPACE_ID is refused, nothing written
#
# Run by hand (not in CI), Git Bash on Windows, Docker running:
#   MSYS_NO_PATHCONV=1 bash infra/tests/registry-adopt.test.sh
set -u -o pipefail
export MSYS_NO_PATHCONV=1

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA="$(cd "$HERE/.." && pwd)"
REPO_ROOT="$(cd "$INFRA/.." && pwd)"
SCRIPT="$INFRA/scripts/registry-adopt-workspace.sh"
COMPOSE_FIXTURE="$HERE/fixtures/registry-adopt.compose.yml"
C001_SQL="$REPO_ROOT/backend/src/main/resources/db/changelog/catalog/C001-registry-tenant-workspace.sql"
C002_SQL="$REPO_ROOT/backend/src/main/resources/db/changelog/catalog/C002-registry-workspace-email.sql"

JAR_PATH=""
for candidate in "$REPO_ROOT"/backend/target/t-solve-backend-*.jar; do
  if [[ -f "$candidate" && "$candidate" != *.original ]]; then
    JAR_PATH="$candidate"
    break
  fi
done
if [[ -z "$JAR_PATH" ]]; then
  echo "harness: build the jar first (no jar in backend/target/t-solve-backend-*.jar)"
  exit 2
fi

if ! docker info >/dev/null 2>&1; then
  echo "harness: the docker daemon is not reachable; start Docker and run again"
  exit 2
fi

SUFFIX="$$-$RANDOM"
export COMPOSE_PROJECT_NAME="regadopttest-$SUFFIX"
WORK="$(mktemp -d)"
WORK_M="$(cygpath -m "$WORK" 2>/dev/null || echo "$WORK")"
COMPOSE_M="$(cygpath -m "$COMPOSE_FIXTURE" 2>/dev/null || echo "$COMPOSE_FIXTURE")"
export BACKEND_JAR="$(cygpath -m "$JAR_PATH" 2>/dev/null || echo "$JAR_PATH")"

SHIM_DIR="$WORK/bin"
ARGV_LOG="$WORK/docker-argv.log"
mkdir -p "$SHIM_DIR"
: >"$ARGV_LOG"
REAL_DOCKER="$(command -v docker)"

cat >"$SHIM_DIR/docker" <<EOF
#!/usr/bin/env bash
{
  printf 'CMD:'
  for a in "\$@"; do
    printf ' [%s]' "\$a"
  done
  printf '\n'
} >>"$ARGV_LOG"
exec "$REAL_DOCKER" "\$@"
EOF
chmod +x "$SHIM_DIR/docker"
export PATH="$SHIM_DIR:$PATH"

SUPER_PW="su-$RANDOM-$RANDOM-$RANDOM"
APP_PW="app-$RANDOM-$RANDOM-$RANDOM"
REG_PW="reg-$RANDOM-$RANDOM-$RANDOM"
CATALOG_KEY="$(head -c 32 /dev/urandom | base64 | tr -d '\r\n')"
SHORT_KEY="$(head -c 16 /dev/urandom | base64 | tr -d '\r\n')"

WS_ID="11111111-1111-4111-8111-111111111111"
OTHER_WS_ID="99999999-9999-4999-8999-999999999999"
ORG_ID="22222222-2222-4222-8222-222222222222"
SLUG="bluefin"
GOOGLE_DOMAIN_MIXED="BlueFin.Example.Test"
GOOGLE_DOMAIN_LOWER="bluefin.example.test"
DB_NAME="tsolve_bluefin"
DB_USER="tsolve_bluefin_app"
REG_DB="tsolve_staging_registry"
REG_USER="tsolve_registry"
SVC_TOKEN_MARKER="tok-marker-$RANDOM-$RANDOM"
COMPANY_MARKER="Bluefin Co $RANDOM"

ENV_FILE="$WORK/test.env"
ENV_FILE_M="$WORK_M/test.env"
write_env_file() {
  local target="$1"
  cat >"$target" <<EOF
TSOLVE_DEMO_WORKSPACE_ID=$WS_ID
TSOLVE_DEMO_SLUG=$SLUG
TSOLVE_DEMO_GOOGLE_DOMAIN=$GOOGLE_DOMAIN_MIXED
TSOLVE_DEMO_ORG_ID=$ORG_ID
DB_NAME=$DB_NAME
DB_USER=$DB_USER
DB_PASSWORD=$APP_PW
CATALOG_SECRET_KEY=$CATALOG_KEY
REGISTRY_DB_NAME=$REG_DB
REGISTRY_DB_USER=$REG_USER
REGISTRY_DB_PASSWORD=$REG_PW
POSTGRES_PASSWORD=$SUPER_PW
EOF
}
write_env_file "$ENV_FILE"

ALL_OUTPUTS=""
STORED_V1_VALUES=()
FAILURES=0
pass() { echo "ok   $1"; }
bad() {
  echo "FAIL $1"
  FAILURES=$((FAILURES + 1))
}
note() { printf '       %s\n' "$@"; }

cleanup() {
  TSOLVE_ENV_FILE="$ENV_FILE_M" "$REAL_DOCKER" compose -f "$COMPOSE_M" --env-file "$ENV_FILE_M" down -v >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

# ---- helpers ---------------------------------------------------------------------------------------------------------
dc() {
  TSOLVE_ENV_FILE="$ENV_FILE_M" "$REAL_DOCKER" compose -f "$COMPOSE_M" --env-file "$ENV_FILE_M" "$@"
}

sql_su() {
  dc exec -T db psql -U postgres -d "$1" -v ON_ERROR_STOP=1 -Atq -c "$2" 2>&1
}

sql_reg() {
  dc exec -T db psql -U "$REG_USER" -d "$REG_DB" -v ON_ERROR_STOP=1 -Atq -c "$1" 2>&1
}

sql_tenant() {
  dc exec -T db psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -Atq -c "$1" 2>&1
}

no_secrets_in() {
  local text="$1" s
  for s in "$APP_PW" "$CATALOG_KEY" "$REG_PW" "$SUPER_PW" "$SVC_TOKEN_MARKER" "$SHORT_KEY"; do
    if [[ -n "$s" ]] && grep -qF -- "$s" <<<"$text"; then
      return 1
    fi
  done
  for s in "${STORED_V1_VALUES[@]+"${STORED_V1_VALUES[@]}"}"; do
    if [[ -n "$s" ]] && grep -qF -- "$s" <<<"$text"; then
      return 1
    fi
  done
  return 0
}

safe_tail() {
  local last
  last="$(tail -n 1 <<<"$1")"
  if no_secrets_in "$last"; then
    echo "$last"
  else
    echo "<redacted: secret detected in output>"
  fi
}

run_script() {
  local envf="${1:-$ENV_FILE_M}"
  shift || true
  if [[ ! -f "$SCRIPT" ]]; then
    echo "harness: $SCRIPT does not exist"
    return 127
  fi
  local out rc
  out="$(env "$@" TSOLVE_COMPOSE_FILE="$COMPOSE_M" TSOLVE_ENV_FILE="$envf" bash "$SCRIPT" 2>&1)"
  rc=$?
  ALL_OUTPUTS+=$'\n'"$out"
  printf '%s' "$out"
  return $rc
}

reset_state() {
  sql_reg "TRUNCATE TABLE workspace_email, tenant_workspace CASCADE;" >/dev/null
  sql_tenant "DELETE FROM workspace; DELETE FROM databasechangelog;" >/dev/null
  sql_tenant "
    INSERT INTO databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype) VALUES
      ('001-init-core-schema', 'core', 'db/changelog/changes/001-init-core-schema.sql', now(), 1, 'EXECUTED'),
      ('035-workspace-info', 'workspace', 'db/changelog/changes/035-workspace-info.sql', now(), 2, 'EXECUTED'),
      ('042-workspace-jira-cloud-id', 'workspace', 'db/changelog/changes/042-workspace-jira-cloud-id.sql', now(), 3, 'EXECUTED');
  " >/dev/null
}

seed_tenant_workspace() {
  local jira_url="${1:-}" jira_cloud="${2:-}"
  sql_tenant "DELETE FROM workspace;" >/dev/null
  if [[ -z "$jira_url" && -z "$jira_cloud" ]]; then
    sql_tenant "
      INSERT INTO workspace (workspace_id, company_name, sign_in_provider, jira_site_address, service_account_api_token, singleton, jira_cloud_id)
      VALUES ('$WS_ID', '$COMPANY_MARKER', 'GOOGLE', NULL, '$SVC_TOKEN_MARKER', TRUE, NULL);
    " >/dev/null
  elif [[ -n "$jira_url" && -z "$jira_cloud" ]]; then
    sql_tenant "
      INSERT INTO workspace (workspace_id, company_name, sign_in_provider, jira_site_address, service_account_api_token, singleton, jira_cloud_id)
      VALUES ('$WS_ID', '$COMPANY_MARKER', 'GOOGLE', '$jira_url', '$SVC_TOKEN_MARKER', TRUE, NULL);
    " >/dev/null
  elif [[ -z "$jira_url" && -n "$jira_cloud" ]]; then
    sql_tenant "
      INSERT INTO workspace (workspace_id, company_name, sign_in_provider, jira_site_address, service_account_api_token, singleton, jira_cloud_id)
      VALUES ('$WS_ID', '$COMPANY_MARKER', 'GOOGLE', NULL, '$SVC_TOKEN_MARKER', TRUE, '$jira_cloud');
    " >/dev/null
  else
    sql_tenant "
      INSERT INTO workspace (workspace_id, company_name, sign_in_provider, jira_site_address, service_account_api_token, singleton, jira_cloud_id)
      VALUES ('$WS_ID', '$COMPANY_MARKER', 'GOOGLE', '$jira_url', '$SVC_TOKEN_MARKER', TRUE, '$jira_cloud');
    " >/dev/null
  fi
}

snap_registry_row() {
  sql_reg "
    SELECT workspace_id, site_name, status, db_name, db_user, db_password, schema_version,
           coalesce(jira_site_url, '<NULL>'), coalesce(jira_cloud_id, '<NULL>'),
           google_domain, coalesce(keycloak_organization_id, '<NULL>'),
           created_at, updated_at
    FROM tenant_workspace
    WHERE workspace_id = '$WS_ID';
  "
}

capture_stored_v1() {
  local v
  v="$(sql_reg "SELECT db_password FROM tenant_workspace WHERE workspace_id = '$WS_ID';")"
  if [[ -n "$v" ]]; then
    STORED_V1_VALUES+=("$v")
  fi
  printf '%s' "$v"
}

decrypt_check() {
  local ws="$1" stored="$2"
  dc exec -T \
    -e "DECRYPT_WORKSPACE_ID=$ws" \
    -e "STORED_DB_PASSWORD=$stored" \
    backend java /app/DecryptV1.java >/dev/null 2>&1
}

# ---- setup containers ------------------------------------------------------------------------------------------------
dc up -d >/dev/null 2>&1 || {
  echo "harness: failed to start test containers"
  exit 2
}

ready=0
for _ in $(seq 1 200); do
  if dc exec -T db pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then
    ready=1
    break
  fi
done
if [[ $ready -ne 1 ]]; then
  echo "harness: db container did not become ready"
  exit 2
fi

sql_su postgres "CREATE ROLE \"$DB_USER\" LOGIN PASSWORD '$APP_PW';" >/dev/null || exit 2
sql_su postgres "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USER\";" >/dev/null || exit 2
sql_su postgres "CREATE ROLE \"$REG_USER\" LOGIN PASSWORD '$REG_PW';" >/dev/null || exit 2
sql_su postgres "CREATE DATABASE \"$REG_DB\" OWNER \"$REG_USER\";" >/dev/null || exit 2

dc exec -T db psql -U "$REG_USER" -d "$REG_DB" -v ON_ERROR_STOP=1 -q <"$C001_SQL" >/dev/null || exit 2
dc exec -T db psql -U "$REG_USER" -d "$REG_DB" -v ON_ERROR_STOP=1 -q <"$C002_SQL" >/dev/null || exit 2

sql_tenant "
  CREATE TABLE databasechangelog (
    id VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    dateexecuted TIMESTAMP NOT NULL,
    orderexecuted INT NOT NULL,
    exectype VARCHAR(10) NOT NULL,
    md5sum VARCHAR(35),
    description VARCHAR(255),
    comments VARCHAR(255),
    tag VARCHAR(255),
    liquibase VARCHAR(20),
    contexts VARCHAR(255),
    labels VARCHAR(255),
    deployment_id VARCHAR(10)
  );
  CREATE TABLE workspace (
    workspace_id              UUID         PRIMARY KEY,
    company_name              VARCHAR(150) NOT NULL,
    sign_in_provider          VARCHAR(30)  NOT NULL,
    jira_site_address         VARCHAR(255),
    service_account_email     VARCHAR(254),
    service_account_api_token TEXT,
    token_expiry_date         DATE,
    singleton                 BOOLEAN      NOT NULL DEFAULT TRUE,
    jira_cloud_id             VARCHAR(36),
    CONSTRAINT uq_workspace_singleton UNIQUE (singleton),
    CONSTRAINT ck_workspace_singleton CHECK (singleton)
  );
" >/dev/null || exit 2

reset_state

# ---- A1 edge 14 ------------------------------------------------------------------------------------------------------
a1_ok=1
for var in TSOLVE_DEMO_WORKSPACE_ID TSOLVE_DEMO_SLUG TSOLVE_DEMO_GOOGLE_DOMAIN TSOLVE_DEMO_ORG_ID \
  DB_NAME DB_USER DB_PASSWORD CATALOG_SECRET_KEY REGISTRY_DB_USER; do
  # missing
  grep -v "^$var=" "$ENV_FILE" >"$WORK/a1.env"
  out="$(run_script "$WORK_M/a1.env" -u "$var")"
  rc=$?
  if [[ $rc -eq 0 ]] || ! grep -q "$var" <<<"$out" || ! no_secrets_in "$out"; then
    a1_ok=0
    note "A1 missing $var: rc=$rc, last line: $(safe_tail "$out")"
  fi
  # blank
  sed "s|^$var=.*|$var=|" "$ENV_FILE" >"$WORK/a1.env"
  out="$(run_script "$WORK_M/a1.env" -u "$var")"
  rc=$?
  if [[ $rc -eq 0 ]] || ! grep -q "$var" <<<"$out" || ! no_secrets_in "$out"; then
    a1_ok=0
    note "A1 blank $var: rc=$rc, last line: $(safe_tail "$out")"
  fi
done

# invalid workspace id
sed "s|^TSOLVE_DEMO_WORKSPACE_ID=.*|TSOLVE_DEMO_WORKSPACE_ID=not-a-uuid|" "$ENV_FILE" >"$WORK/a1.env"
out="$(run_script "$WORK_M/a1.env" -u TSOLVE_DEMO_WORKSPACE_ID)"
rc=$?
if [[ $rc -eq 0 ]] || ! grep -q "TSOLVE_DEMO_WORKSPACE_ID" <<<"$out" || ! no_secrets_in "$out"; then
  a1_ok=0
  note "A1 invalid UUID: rc=$rc, last line: $(safe_tail "$out")"
fi

# invalid slug (label rule)
sed "s|^TSOLVE_DEMO_SLUG=.*|TSOLVE_DEMO_SLUG=Blue_Fin|" "$ENV_FILE" >"$WORK/a1.env"
out="$(run_script "$WORK_M/a1.env" -u TSOLVE_DEMO_SLUG)"
rc=$?
if [[ $rc -eq 0 ]] || ! grep -q "TSOLVE_DEMO_SLUG" <<<"$out" || ! no_secrets_in "$out"; then
  a1_ok=0
  note "A1 invalid slug Blue_Fin: rc=$rc, last line: $(safe_tail "$out")"
fi

# invalid 16-byte key (recreate backend container with it)
sed "s|^CATALOG_SECRET_KEY=.*|CATALOG_SECRET_KEY=$SHORT_KEY|" "$ENV_FILE" >"$WORK/a1-badkey.env"
TSOLVE_ENV_FILE="$WORK_M/a1-badkey.env" "$REAL_DOCKER" compose -f "$COMPOSE_M" --env-file "$WORK_M/a1-badkey.env" up -d backend >/dev/null 2>&1
out="$(run_script "$WORK_M/a1-badkey.env" -u CATALOG_SECRET_KEY)"
rc=$?
if [[ $rc -eq 0 ]] || ! grep -q "CATALOG_SECRET_KEY" <<<"$out" || ! no_secrets_in "$out"; then
  a1_ok=0
  note "A1 invalid 16-byte CATALOG_SECRET_KEY: rc=$rc, last line: $(safe_tail "$out")"
fi
dc up -d backend >/dev/null 2>&1

if [[ "$(sql_reg "SELECT count(*) FROM tenant_workspace;")" != "0" ]] ||
   [[ "$(sql_reg "SELECT count(*) FROM workspace_email;")" != "0" ]]; then
  a1_ok=0
  note "A1 refused runs wrote rows"
fi

if [[ $a1_ok -eq 1 ]]; then
  pass "A1 edge 14 refuses without a required variable (or with an invalid one), names it, writes nothing"
else
  bad "A1 edge 14 refuses without a required variable (or with an invalid one), names it, writes nothing"
fi

# ---- A2 AC-001.15 AC-001.24 ------------------------------------------------------------------------------------------
reset_state
seed_tenant_workspace "" ""
A2_LOG_START="$(wc -l <"$ARGV_LOG")"
OUT_A2="$(run_script)"
rc_a2=$?
a2_ok=1
if [[ $rc_a2 -ne 0 ]]; then
  a2_ok=0
  note "A2 rc=$rc_a2, last line: $(safe_tail "$OUT_A2")"
fi

stored_pw="$(capture_stored_v1)"
row_a2="$(sql_reg "SELECT status || '|' || site_name || '|' || db_name || '|' || db_user || '|' || google_domain || '|' || keycloak_organization_id || '|' || schema_version || '|' || (created_at IS NOT NULL) || '|' || (updated_at IS NOT NULL) FROM tenant_workspace WHERE workspace_id = '$WS_ID';")"
expected_a2="ACTIVE|$SLUG|$DB_NAME|$DB_USER|$GOOGLE_DOMAIN_LOWER|$ORG_ID|042-workspace-jira-cloud-id|true|true"
if [[ "$row_a2" != "$expected_a2" ]]; then
  a2_ok=0
  note "A2 row=[$row_a2], want=[$expected_a2]"
fi

if [[ "$(wc -l <<<"$stored_pw")" -ne 1 || "$stored_pw" != v1:* || "$stored_pw" == "$APP_PW" ]] ||
   grep -q "JAVA_TOOL_OPTIONS" <<<"$stored_pw"; then
  a2_ok=0
  note "A2 stored db_password format invalid (must be single v1: line without JAVA_TOOL_OPTIONS)"
fi

if ! decrypt_check "$WS_ID" "$stored_pw"; then
  a2_ok=0
  note "A2 DecryptV1 failed for $WS_ID"
fi
if decrypt_check "$OTHER_WS_ID" "$stored_pw"; then
  a2_ok=0
  note "A2 DecryptV1 unexpectedly succeeded for other workspace id"
fi
if ! no_secrets_in "$OUT_A2"; then
  a2_ok=0
  note "A2 output contains a secret"
fi
# Q6 -> A: Registry write uses REGISTRY_DB_USER, not postgres
a2_argv="$(tail -n +"$((A2_LOG_START + 1))" "$ARGV_LOG")"
if ! grep -q "\[$REG_USER\]" <<<"$a2_argv"; then
  a2_ok=0
  note "A2 docker log did not show REGISTRY_DB_USER ($REG_USER) for Registry write"
fi

if [[ $a2_ok -eq 1 ]]; then
  pass "A2 AC-001.15 AC-001.24 writes the bluefin row with an encrypted password and prints no password"
else
  bad "A2 AC-001.15 AC-001.24 writes the bluefin row with an encrypted password and prints no password"
fi

# ---- A3 edge 77 ------------------------------------------------------------------------------------------------------
a3_ok=1
SNAP_A3="$(snap_registry_row)"
sed "s|^TSOLVE_DEMO_GOOGLE_DOMAIN=.*|TSOLVE_DEMO_GOOGLE_DOMAIN=BLUEFIN.EXAMPLE.TEST|" "$ENV_FILE" >"$WORK/a3.env"
out="$(run_script "$WORK_M/a3.env")"
rc=$?
if [[ $rc -ne 0 ]] || [[ "$(snap_registry_row)" != "$SNAP_A3" ]] || ! no_secrets_in "$out"; then
  a3_ok=0
  note "A3 rerun (upper-case domain): rc=$rc or row changed or secret printed"
fi

# Q4 -> A: new tenant changeset + different db_user on Registry row -> still exit 0, row byte-identical
sql_tenant "INSERT INTO databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype) VALUES ('055-new-change', 'core', 'db/changelog/changes/055-new-change.sql', now(), 4, 'EXECUTED');" >/dev/null
sql_reg "UPDATE tenant_workspace SET db_user = 'other_db_user' WHERE workspace_id = '$WS_ID';" >/dev/null
SNAP_A3_Q4="$(snap_registry_row)"
out="$(run_script)"
rc=$?
if [[ $rc -ne 0 ]] || [[ "$(snap_registry_row)" != "$SNAP_A3_Q4" ]] || ! no_secrets_in "$out"; then
  a3_ok=0
  note "A3 rerun (new changeset + changed db_user): rc=$rc or row changed or secret printed"
fi
if [[ "$(sql_reg "SELECT count(*) FROM tenant_workspace;")" != "1" ]] ||
   [[ "$(sql_reg "SELECT count(*) FROM workspace_email;")" != "0" ]]; then
  a3_ok=0
  note "A3 row counts wrong"
fi

if [[ $a3_ok -eq 1 ]]; then
  pass "A3 edge 77 a second run with the same values changes nothing"
else
  bad "A3 edge 77 a second run with the same values changes nothing"
fi

# ---- A4 edge 78 ------------------------------------------------------------------------------------------------------
a4_ok=1
reset_state
seed_tenant_workspace "https://bluefin.atlassian.net" "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
run_script >/dev/null
capture_stored_v1 >/dev/null

for case in \
  "site_name|TSOLVE_DEMO_SLUG|other-slug" \
  "google_domain|TSOLVE_DEMO_GOOGLE_DOMAIN|other.example.test" \
  "keycloak_organization_id|TSOLVE_DEMO_ORG_ID|33333333-3333-4333-8333-333333333333" \
  "db_name|DB_NAME|other_db"; do
  col="${case%%|*}"
  rest="${case#*|}"
  var="${rest%%|*}"
  val="${rest#*|}"
  snap="$(snap_registry_row)"
  sed "s|^$var=.*|$var=$val|" "$ENV_FILE" >"$WORK/a4.env"
  out="$(run_script "$WORK_M/a4.env")"
  rc=$?
  if [[ $rc -eq 0 ]] || { ! grep -q "$col" <<<"$out" && ! grep -q "$var" <<<"$out"; } ||
     [[ "$(snap_registry_row)" != "$snap" ]] || ! no_secrets_in "$out"; then
    a4_ok=0
    note "A4 mismatch $col: rc=$rc, last line: $(safe_tail "$out")"
  fi
done

# Jira mismatch: Registry row has no Jira site, tenant row has one (Q4 -> A)
sql_reg "UPDATE tenant_workspace SET jira_site_url = NULL, jira_cloud_id = NULL WHERE workspace_id = '$WS_ID';" >/dev/null
snap="$(snap_registry_row)"
out="$(run_script)"
rc=$?
if [[ $rc -eq 0 ]] || { ! grep -q "jira_site_url" <<<"$out" && ! grep -q "jira_cloud_id" <<<"$out"; } ||
   [[ "$(snap_registry_row)" != "$snap" ]] || ! no_secrets_in "$out"; then
  a4_ok=0
  note "A4 mismatch jira_site_url (Registry NULL, tenant set): rc=$rc, last line: $(safe_tail "$out")"
fi

if [[ $a4_ok -eq 1 ]]; then
  pass "A4 edge 78 an existing row with other values is refused, the field named, nothing changed"
else
  bad "A4 edge 78 an existing row with other values is refused, the field named, nothing changed"
fi

# ---- A5 edge 79 ------------------------------------------------------------------------------------------------------
a5_ok=1
# (a) both set, upper-case cloud id -> lower-cased
reset_state
seed_tenant_workspace "https://bluefin.atlassian.net" "ABCDEF12-3456-7890-ABCD-EF1234567890"
out="$(run_script)"
rc=$?
capture_stored_v1 >/dev/null
jira_a="$(sql_reg "SELECT coalesce(jira_site_url,'<NULL>') || '|' || coalesce(jira_cloud_id,'<NULL>') FROM tenant_workspace WHERE workspace_id = '$WS_ID';")"
if [[ $rc -ne 0 || "$jira_a" != "https://bluefin.atlassian.net|abcdef12-3456-7890-abcd-ef1234567890" ]]; then
  a5_ok=0
  note "A5(a): rc=$rc, jira=[$jira_a]"
fi

# (b) address only -> both NULL
reset_state
seed_tenant_workspace "https://bluefin.atlassian.net" ""
out="$(run_script)"
rc=$?
capture_stored_v1 >/dev/null
jira_b="$(sql_reg "SELECT coalesce(jira_site_url,'<NULL>') || '|' || coalesce(jira_cloud_id,'<NULL>') FROM tenant_workspace WHERE workspace_id = '$WS_ID';")"
if [[ $rc -ne 0 || "$jira_b" != "<NULL>|<NULL>" ]]; then
  a5_ok=0
  note "A5(b): rc=$rc, jira=[$jira_b]"
fi

# (c) cloud id only -> both NULL
reset_state
seed_tenant_workspace "" "ABCDEF12-3456-7890-ABCD-EF1234567890"
out="$(run_script)"
rc=$?
capture_stored_v1 >/dev/null
jira_c="$(sql_reg "SELECT coalesce(jira_site_url,'<NULL>') || '|' || coalesce(jira_cloud_id,'<NULL>') FROM tenant_workspace WHERE workspace_id = '$WS_ID';")"
if [[ $rc -ne 0 || "$jira_c" != "<NULL>|<NULL>" ]]; then
  a5_ok=0
  note "A5(c): rc=$rc, jira=[$jira_c]"
fi

if [[ $a5_ok -eq 1 ]]; then
  pass "A5 edge 79 a tenant row copies both Jira columns, the cloud id lower-case, or neither"
else
  bad "A5 edge 79 a tenant row copies both Jira columns, the cloud id lower-case, or neither"
fi

# ---- A6 edge 79 ------------------------------------------------------------------------------------------------------
a6_ok=1
reset_state
out="$(run_script)"
rc=$?
capture_stored_v1 >/dev/null
jira_empty="$(sql_reg "SELECT coalesce(jira_site_url,'<NULL>') || '|' || coalesce(jira_cloud_id,'<NULL>') FROM tenant_workspace WHERE workspace_id = '$WS_ID';")"
jira_lines="$(grep -ci "jira" <<<"$out" || true)"
if [[ $rc -ne 0 || "$jira_empty" != "<NULL>|<NULL>" || "$jira_lines" -ne 1 ]]; then
  a6_ok=0
  note "A6: rc=$rc, jira=[$jira_empty], jira_lines=$jira_lines"
fi

if [[ $a6_ok -eq 1 ]]; then
  pass "A6 edge 79 an empty tenant workspace table gives no Jira site and one line saying so"
else
  bad "A6 edge 79 an empty tenant workspace table gives no Jira site and one line saying so"
fi

# ---- A7 AC-001.17 AC-001.19 ------------------------------------------------------------------------------------------
a7_ok=1
reset_state
seed_tenant_workspace "https://bluefin.atlassian.net" "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
SNAP_DB_BEFORE="$(sql_su postgres "SELECT datname, pg_get_userbyid(datdba) FROM pg_database ORDER BY datname;")"
SNAP_ROLE_BEFORE="$(sql_su postgres "SELECT rolname, rolsuper, rolcanlogin FROM pg_authid ORDER BY rolname;")"
SNAP_CHLOG_BEFORE="$(sql_tenant "SELECT id, orderexecuted FROM databasechangelog ORDER BY orderexecuted;")"
SNAP_WS_BEFORE="$(sql_tenant "SELECT workspace_id, company_name, service_account_api_token FROM workspace;")"

out="$(run_script)"
rc=$?
capture_stored_v1 >/dev/null

if [[ $rc -ne 0 ]] ||
   [[ "$(sql_reg "SELECT count(*) FROM tenant_workspace;")" != "1" ]] ||
   [[ "$(sql_reg "SELECT count(*) FROM workspace_email;")" != "0" ]] ||
   [[ "$(sql_su postgres "SELECT datname, pg_get_userbyid(datdba) FROM pg_database ORDER BY datname;")" != "$SNAP_DB_BEFORE" ]] ||
   [[ "$(sql_su postgres "SELECT rolname, rolsuper, rolcanlogin FROM pg_authid ORDER BY rolname;")" != "$SNAP_ROLE_BEFORE" ]] ||
   [[ "$(sql_tenant "SELECT id, orderexecuted FROM databasechangelog ORDER BY orderexecuted;")" != "$SNAP_CHLOG_BEFORE" ]] ||
   [[ "$(sql_tenant "SELECT workspace_id, company_name, service_account_api_token FROM workspace;")" != "$SNAP_WS_BEFORE" ]]; then
  a7_ok=0
  note "A7 state changed outside tenant_workspace (rc=$rc)"
fi

row_text="$(snap_registry_row)"
if grep -qF -- "$COMPANY_MARKER" <<<"$row_text" || grep -qF -- "$SVC_TOKEN_MARKER" <<<"$row_text"; then
  a7_ok=0
  note "A7 business data or token marker copied into Registry row"
fi

if [[ $a7_ok -eq 1 ]]; then
  pass "A7 AC-001.17 AC-001.19 adopting creates nothing else and copies no business data"
else
  bad "A7 AC-001.17 AC-001.19 adopting creates nothing else and copies no business data"
fi

# ---- A8 edge 14 ------------------------------------------------------------------------------------------------------
a8_ok=1
if ! no_secrets_in "$(cat "$ARGV_LOG")"; then
  a8_ok=0
  note "A8 secret appeared in docker command line arguments"
fi
if ! no_secrets_in "$ALL_OUTPUTS"; then
  a8_ok=0
  note "A8 secret appeared in script output"
fi

if [[ $a8_ok -eq 1 ]]; then
  pass "A8 edge 14 no secret on any docker command line or in any output"
else
  bad "A8 edge 14 no secret on any docker command line or in any output"
fi

# ---- A9 edge 78 ------------------------------------------------------------------------------------------------------
a9_ok=1
reset_state
seed_tenant_workspace "https://bluefin.atlassian.net" "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
sql_tenant "UPDATE workspace SET workspace_id = '$OTHER_WS_ID';" >/dev/null
out="$(run_script)"
rc=$?
if [[ $rc -eq 0 ]] || ! grep -q "workspace_id" <<<"$out" ||
   [[ "$(sql_reg "SELECT count(*) FROM tenant_workspace;")" != "0" ]] || ! no_secrets_in "$out"; then
  a9_ok=0
  note "A9 tenant workspace_id mismatch: rc=$rc, last line: $(safe_tail "$out")"
fi

if [[ $a9_ok -eq 1 ]]; then
  pass "A9 edge 78 a tenant workspace_id other than TSOLVE_DEMO_WORKSPACE_ID is refused, nothing written"
else
  bad "A9 edge 78 a tenant workspace_id other than TSOLVE_DEMO_WORKSPACE_ID is refused, nothing written"
fi

# ---- result ----------------------------------------------------------------------------------------------------------
if [[ "$FAILURES" -ne 0 ]]; then
  echo "KAN-252 registry-adopt: $FAILURES check(s) failed"
  exit 1
fi
echo "KAN-252 registry-adopt: all checks passed"
