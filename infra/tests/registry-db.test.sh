#!/usr/bin/env bash
# KAN-386 Task 3b: infra/scripts/registry-db-init.sh creates the Registry database and its owner on an EXISTING staging
# volume (the init script runs only on an empty one). Check names start with the id S1..S5.
#
#   S1 edge 73  refuses without POSTGRES_USER / POSTGRES_PASSWORD (or the REGISTRY_DB_* pair), names it, creates nothing
#   S2 edge 74, AC-001.15, AC-001.32  creates the database and the user: attributes, connects, other databases untouched, no password printed
#   S3 edge 75  a second run (even with another REGISTRY_DB_PASSWORD) changes nothing and never prints a password
#   S4 edge 74  the end state equals the one the init script gives on a fresh volume
#   S5 AC-008.5 compose.staging.yml passes, and staging.env.example lists, REGISTRY_DB_* and CATALOG_SECRET_KEY
#
# Run by hand (not in CI), Git Bash on Windows, Docker running:
#   MSYS_NO_PATHCONV=1 bash infra/tests/registry-db.test.sh
# The script runs INSIDE the db container, the way the runbook does: docker exec -i db sh -s < registry-db-init.sh
# (the db container holds REGISTRY_DB_* from compose). Password checks use a one-shot psql container on the same
# network, because inside the db container the local socket and loopback are trust.
set -u -o pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA="$(cd "$HERE/.." && pwd)"
SCRIPT="$INFRA/scripts/registry-db-init.sh"
INIT_DIR="$INFRA/postgres/init"
COMPOSE="$INFRA/compose.staging.yml"
ENV_EXAMPLE="$INFRA/staging.env.example"
IMAGE="pgvector/pgvector:pg16" # the image compose.staging.yml uses

SUFFIX="$$-$RANDOM"
NET="regdbtest-net-$SUFFIX"
DB="regdbtest-db-$SUFFIX"
VOL="regdbtest-vol-$SUFFIX"
WORK="$(mktemp -d)"

SUPER_PW="su-$RANDOM-$RANDOM-$RANDOM"
APP_PW="app-$RANDOM-$RANDOM-$RANDOM"
KC_PW="kc-$RANDOM-$RANDOM-$RANDOM"
JOBS_PW="jobs-$RANDOM-$RANDOM-$RANDOM"
REG_PW="reg-$RANDOM-$RANDOM-$RANDOM"
REG_PW2="reg2-$RANDOM-$RANDOM-$RANDOM"
REG_DB="tsolve_staging_registry"
REG_USER="tsolve_registry"

FAILURES=0
pass() { echo "ok   $1"; }
bad() {
  echo "FAIL $1"
  FAILURES=$((FAILURES + 1))
}
note() { printf '       %s\n' "$@"; }

cleanup() {
  docker rm -f "$DB" >/dev/null 2>&1
  docker volume rm -f "$VOL" >/dev/null 2>&1
  docker network rm "$NET" >/dev/null 2>&1
  rm -rf "$WORK"
}
trap cleanup EXIT

# ---- helpers ---------------------------------------------------------------------------------------------------------
# sql <database> <statement>: as the superuser inside the db container; unaligned, tuples only
sql() { docker exec "$DB" psql -U postgres -d "$1" -v ON_ERROR_STOP=1 -Atq -c "$2" 2>&1; }

# run_script [env options]: the script as the runbook runs it; options go to `env` (-u VAR, VAR=value, VAR=)
run_script() {
  if [[ ! -f "$SCRIPT" ]]; then
    echo "harness: $SCRIPT does not exist"
    return 127
  fi
  docker exec -i "$DB" env "$@" sh -s <"$SCRIPT" 2>&1
}

# psql_as <user> <password> <database> <statement>: from another container, so the password is checked
psql_as() {
  docker run --rm --network "$NET" -e PGPASSWORD="$2" "$IMAGE" \
    psql -h "$DB" -U "$1" -d "$3" -Atq -v ON_ERROR_STOP=1 -c "$4" 2>&1
}

# the Registry database and role rows, without the oid and the password hash
reg_db_row() {
  sql postgres "select pg_get_userbyid(datdba), encoding, datcollate, datctype, datistemplate, datallowconn, datconnlimit, coalesce(datacl::text,'-') from pg_database where datname='$REG_DB'"
}
reg_role_row() {
  sql postgres "select rolname, rolsuper, rolinherit, rolcreaterole, rolcreatedb, rolcanlogin, rolreplication, rolconnlimit, rolbypassrls, coalesce(rolvaliduntil::text,'-'), coalesce(rolconfig::text,'-'), (select coalesce(string_agg(g.rolname, ',' order by g.rolname),'-') from pg_auth_members m join pg_roles g on g.oid=m.roleid where m.member=r.oid) from pg_roles r where rolname='$REG_USER'"
}
reg_schema_row() {
  sql "$REG_DB" "select pg_get_userbyid(nspowner), coalesce(nspacl::text,'-'), (select count(*) from pg_tables where schemaname not in ('pg_catalog','information_schema')) from pg_namespace where nspname='public'"
}
reg_state() { printf 'db: %s\nrole: %s\nschema: %s\n' "$(reg_db_row)" "$(reg_role_row)" "$(reg_schema_row)"; }

# every database row and every role row (with the password hash), and the marker rows: a change shows as a diff
snap_all() {
  sql postgres "select oid, datname, pg_get_userbyid(datdba), encoding, datcollate, datctype, coalesce(datacl::text,'-') from pg_database order by datname"
  sql postgres "select oid, rolname, rolsuper, rolcreaterole, rolcreatedb, rolcanlogin, rolreplication, rolbypassrls, coalesce(rolpassword,'-') from pg_authid order by rolname"
}
snap_others() { # everything except the Registry database and role, plus the markers in the other three databases
  sql postgres "select oid, datname, pg_get_userbyid(datdba), encoding, datcollate, datctype, coalesce(datacl::text,'-') from pg_database where datname <> '$REG_DB' order by datname"
  sql postgres "select oid, rolname, rolsuper, rolcreaterole, rolcreatedb, rolcanlogin, rolreplication, rolbypassrls, coalesce(rolpassword,'-') from pg_authid where rolname <> '$REG_USER' order by rolname"
  for d in tsolve_t keycloak tsolve_t_jobs; do
    printf '%s: ' "$d"
    sql "$d" "select string_agg(v, ',' order by v) from marker"
  done
}

has_registry() { # prints "db=<n> role=<n>"
  printf 'db=%s role=%s' "$(sql postgres "select count(*) from pg_database where datname='$REG_DB'")" \
    "$(sql postgres "select count(*) from pg_roles where rolname='$REG_USER'")"
}
drop_registry() {
  sql postgres "drop database if exists \"$REG_DB\"" >/dev/null
  sql postgres "drop role if exists \"$REG_USER\"" >/dev/null
}
no_password_in() { # <output>: none of the passwords appears
  local out="$1" pw
  for pw in "$SUPER_PW" "$REG_PW" "$REG_PW2"; do
    if grep -qF -- "$pw" <<<"$out"; then return 1; fi
  done
  return 0
}

# ---- S5 needs no containers ------------------------------------------------------------------------------------------
check_s5() {
  local wdir envf out rc svc ok=1
  wdir="$(cygpath -m "$WORK" 2>/dev/null || echo "$WORK")" # docker compose is a Windows binary under Git Bash
  envf="$wdir/staging.env"
  COMPOSE="$(cygpath -m "$COMPOSE" 2>/dev/null || echo "$COMPOSE")"
  cat >"$envf" <<EOF
TSOLVE_DOMAIN=example.test
TSOLVE_SITE_HOSTS=demo.example.test
TSOLVE_DEMO_SLUG=demo
TSOLVE_DEMO_GOOGLE_DOMAIN=example.test
TSOLVE_DEMO_ORG_ID=00000000-0000-0000-0000-000000000001
GOOGLE_CLIENT_ID=g
GOOGLE_CLIENT_SECRET=g
KEYCLOAK_WEB_CLIENT_SECRET=k
KEYCLOAK_CONSOLE_CLIENT_SECRET=k
KEYCLOAK_ADMIN_CLIENT_SECRET=k
KEYCLOAK_DB_PASSWORD=k
POSTGRES_PASSWORD=p
DB_NAME=tsolve_t
DB_USER=u
DB_PASSWORD=p
JOBS_DB_USER=j
JOBS_DB_PASSWORD=p
REDIS_PASSWORD=r
OLLAMA_CHAT_MODEL=m
REGISTRY_DB_NAME=$REG_DB
REGISTRY_DB_USER=$REG_USER
REGISTRY_DB_PASSWORD=$REG_PW
REGISTRY_DB_URL=jdbc:postgresql://db:5432/$REG_DB
CATALOG_SECRET_KEY=test-only-catalog-key
EOF
  out="$(TSOLVE_ENV_FILE="$envf" docker compose -f "$COMPOSE" --env-file "$envf" config 2>&1)"
  rc=$?
  if [[ $rc -ne 0 ]]; then
    bad "S5 AC-008.5 compose config with every variable set failed (rc $rc)"
    note "$(tail -n 3 <<<"$out")"
    return
  fi
  svc="$(awk '/^  db:/{f=1;next} /^  [a-z][a-z-]*:/{f=0} f' <<<"$out")"
  for v in REGISTRY_DB_NAME REGISTRY_DB_USER REGISTRY_DB_PASSWORD; do
    grep -q "^ *$v:" <<<"$svc" || {
      ok=0
      note "db service gets no $v"
    }
  done
  svc="$(awk '/^  backend:/{f=1;next} /^  [a-z][a-z-]*:/{f=0} f' <<<"$out")"
  for v in REGISTRY_DB_URL CATALOG_SECRET_KEY; do
    grep -q "^ *$v:" <<<"$svc" || {
      ok=0
      note "backend service gets no $v"
    }
  done
  for v in REGISTRY_DB_USER REGISTRY_DB_PASSWORD; do # required: a missing one stops compose and names it
    grep -v "^$v=" "$WORK/staging.env" >"$WORK/missing.env"
    out="$(env -u "$v" TSOLVE_ENV_FILE="$wdir/missing.env" docker compose -f "$COMPOSE" --env-file "$wdir/missing.env" config 2>&1)"
    rc=$?
    if [[ $rc -eq 0 ]] || ! grep -q "$v" <<<"$out"; then
      ok=0
      note "compose without $v: rc $rc, does not stop naming it"
    fi
  done
  for v in REGISTRY_DB_NAME REGISTRY_DB_USER REGISTRY_DB_PASSWORD REGISTRY_DB_URL CATALOG_SECRET_KEY; do
    grep -q "^$v=" "$ENV_EXAMPLE" || {
      ok=0
      note "staging.env.example does not list $v"
    }
  done
  if [[ $ok -eq 1 ]]; then
    pass "S5 AC-008.5 compose passes, and staging.env.example lists, REGISTRY_DB_* and CATALOG_SECRET_KEY"
  else
    bad "S5 AC-008.5 the new variables are not set where staging reads them"
  fi
}

# ---- setup: a volume as staging has it, but without the Registry -----------------------------------------------------
if ! docker info >/dev/null 2>&1; then
  echo "harness: the docker daemon is not reachable; start Docker and run again"
  exit 2
fi

check_s5

docker network create "$NET" >/dev/null || exit 2
docker volume create "$VOL" >/dev/null || exit 2
docker run -d --name "$DB" --network "$NET" -v "$VOL:/var/lib/postgresql/data" -v "$INIT_DIR:/docker-entrypoint-initdb.d:ro" \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$SUPER_PW" \
  -e DB_NAME=tsolve_t -e DB_USER=tsolve_t_app -e DB_PASSWORD="$APP_PW" \
  -e KEYCLOAK_DB_PASSWORD="$KC_PW" \
  -e JOBS_DB_NAME=tsolve_t_jobs -e JOBS_DB_USER=tsolve_t_jobs -e JOBS_DB_PASSWORD="$JOBS_PW" \
  -e REGISTRY_DB_NAME="$REG_DB" -e REGISTRY_DB_USER="$REG_USER" -e REGISTRY_DB_PASSWORD="$REG_PW" \
  "$IMAGE" >/dev/null || exit 2

# The init run starts a temporary server on the socket only and restarts it once: wait on TCP, which only the final
# server opens. A bounded loop, no sleep: each docker exec takes a moment.
ready=0
for _ in $(seq 1 400); do
  if docker exec "$DB" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then
    ready=1
    break
  fi
done
if [[ $ready -ne 1 ]]; then
  echo "harness: the db container did not become ready"
  docker logs "$DB" 2>&1 | tail -n 5
  exit 2
fi

REG_INIT="$(reg_state)" # what the init script gives on a fresh volume (the S4 baseline)
if [[ "$(has_registry)" != "db=1 role=1" ]]; then
  echo "harness: the init script did not create the Registry ($(has_registry)); cannot build the baseline"
  exit 2
fi
for pair in "tsolve_t:tsolve_t_app" "keycloak:keycloak" "tsolve_t_jobs:tsolve_t_jobs"; do # a marker row in each other database
  d="${pair%%:*}"
  u="${pair##*:}"
  docker exec "$DB" psql -U "$u" -d "$d" -v ON_ERROR_STOP=1 -q -c "create table marker(v text); insert into marker values ('$d-row')" >/dev/null || exit 2
done
drop_registry # now it is the volume of a stack made before Task 3a
if [[ "$(has_registry)" != "db=0 role=0" ]]; then
  echo "harness: could not drop the Registry"
  exit 2
fi
BEFORE_OTHERS="$(snap_others)"

# ---- S1 edge 73 ------------------------------------------------------------------------------------------------------
s1_ok=1
for case in "-u POSTGRES_USER|POSTGRES_USER" "-u POSTGRES_PASSWORD|POSTGRES_PASSWORD" "POSTGRES_PASSWORD=|POSTGRES_PASSWORD" \
  "-u REGISTRY_DB_USER|REGISTRY_DB_USER" "-u REGISTRY_DB_PASSWORD|REGISTRY_DB_PASSWORD"; do
  opts="${case%%|*}"
  var="${case##*|}"
  # shellcheck disable=SC2086 # the options are split on purpose
  out="$(run_script $opts)"
  rc=$?
  if [[ $rc -eq 0 ]] || ! grep -q "$var" <<<"$out" || ! no_password_in "$out"; then
    s1_ok=0
    note "run with [$opts]: rc $rc, last line: $(tail -n 1 <<<"$out")"
  fi
done
if [[ "$(has_registry)" != "db=0 role=0" ]]; then
  s1_ok=0
  note "a refused run created something: $(has_registry)"
fi
if [[ $s1_ok -eq 1 ]]; then
  pass "S1 edge 73 refuses without the superuser (or Registry) variables, naming the missing one, and creates nothing"
else
  bad "S1 edge 73 refuses without the superuser (or Registry) variables, naming the missing one, and creates nothing"
fi

# ---- S2 edge 74 ------------------------------------------------------------------------------------------------------
OUT2="$(run_script)"
rc=$?
s2_ok=1
[[ $rc -eq 0 ]] || {
  s2_ok=0
  note "first run: rc $rc, last line: $(tail -n 1 <<<"$OUT2")"
}
[[ "$(reg_db_row | cut -d'|' -f1)" == "$REG_USER" ]] || {
  s2_ok=0
  note "database $REG_DB missing or not owned by $REG_USER: $(reg_db_row)"
}
role_attrs="$(sql postgres "select rolcanlogin, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole, rolreplication from pg_roles where rolname='$REG_USER'")"
[[ "$role_attrs" == "t|f|f|f|f|f" ]] || {
  s2_ok=0
  note "role attributes (login, super, bypassrls, createdb, createrole, replication) = [$role_attrs], want [t|f|f|f|f|f]"
}
[[ "$(psql_as "$REG_USER" "$REG_PW" "$REG_DB" 'select 1')" == "1" ]] || {
  s2_ok=0
  note "the Registry user cannot connect with its password"
}
[[ "$(psql_as "$REG_USER" "$REG_PW" "$REG_DB" 'create table t_probe(i int); insert into t_probe values (7); select i from t_probe; drop table t_probe' | head -n 1)" == "7" ]] || {
  s2_ok=0
  note "the Registry user cannot create and drop a table in its own database"
}
[[ "$(snap_others)" == "$BEFORE_OTHERS" ]] || {
  s2_ok=0
  note "the app, Keycloak or Jobs databases or roles changed"
}
no_password_in "$OUT2" || {
  s2_ok=0
  note "the output holds a password"
}
if [[ $s2_ok -eq 1 ]]; then
  pass "S2 edge 74 AC-001.15 AC-001.32 creates the Registry database and user on an existing volume, others untouched, no password printed"
else
  bad "S2 edge 74 AC-001.15 AC-001.32 creates the Registry database and user on an existing volume, others untouched, no password printed"
fi

# ---- S3 edge 75 ------------------------------------------------------------------------------------------------------
s3_ok=1
SNAP_A="$(snap_all)"
for opts in "REGISTRY_DB_PASSWORD=$REG_PW" "REGISTRY_DB_PASSWORD=$REG_PW2"; do
  out="$(run_script "$opts")"
  rc=$?
  [[ $rc -eq 0 ]] || {
    s3_ok=0
    note "second run [$opts]: rc $rc, last line: $(tail -n 1 <<<"$out")"
  }
  grep -q "already exists" <<<"$out" && grep -q "$REG_USER" <<<"$out" && grep -q "$REG_DB" <<<"$out" || {
    s3_ok=0
    note "second run [$opts]: no 'already exists' line naming $REG_USER and $REG_DB"
  }
  no_password_in "$out" || {
    s3_ok=0
    note "second run [$opts]: the output holds a password"
  }
  [[ "$(snap_all)" == "$SNAP_A" ]] || {
    s3_ok=0
    note "second run [$opts]: a database or role row (or the password hash) changed"
  }
done
[[ "$(psql_as "$REG_USER" "$REG_PW" "$REG_DB" 'select 1')" == "1" ]] || {
  s3_ok=0
  note "the original password no longer connects"
}
if psql_as "$REG_USER" "$REG_PW2" "$REG_DB" 'select 1' >/dev/null; then
  s3_ok=0
  note "the new password connects: the role was altered"
fi
# partial state: only the database is gone; the run recreates it for the existing role and leaves the role as it is
ROLE_BEFORE="$(sql postgres "select oid, rolpassword from pg_authid where rolname='$REG_USER'")"
sql postgres "drop database \"$REG_DB\"" >/dev/null
out="$(run_script)"
rc=$?
[[ $rc -eq 0 && "$(reg_db_row | cut -d'|' -f1)" == "$REG_USER" ]] || {
  s3_ok=0
  note "partial state (database gone, role kept): rc $rc, database not recreated for $REG_USER"
}
[[ "$(sql postgres "select oid, rolpassword from pg_authid where rolname='$REG_USER'")" == "$ROLE_BEFORE" ]] || {
  s3_ok=0
  note "partial state: the role changed"
}
no_password_in "$out" || {
  s3_ok=0
  note "partial state: the output holds a password"
}
if [[ $s3_ok -eq 1 ]]; then
  pass "S3 edge 75 a second run, even with another REGISTRY_DB_PASSWORD, changes nothing and prints no password"
else
  bad "S3 edge 75 a second run, even with another REGISTRY_DB_PASSWORD, changes nothing and prints no password"
fi

# ---- S4 edge 74 ------------------------------------------------------------------------------------------------------
drop_registry
out="$(run_script)"
rc=$?
AFTER="$(reg_state)"
if [[ $rc -eq 0 && "$AFTER" == "$REG_INIT" ]]; then
  pass "S4 edge 74 the end state equals the init script's on a fresh volume"
else
  bad "S4 edge 74 the end state equals the init script's on a fresh volume (rc $rc)"
  diff <(echo "$REG_INIT") <(echo "$AFTER") | sed 's/^/       /' | head -n 12
  [[ $rc -eq 0 ]] || note "last line: $(tail -n 1 <<<"$out")"
fi

# ---- result ----------------------------------------------------------------------------------------------------------
if [[ "$FAILURES" -ne 0 ]]; then
  echo "KAN-386 registry-db: $FAILURES check(s) failed"
  exit 1
fi
echo "KAN-386 registry-db: all checks passed"
