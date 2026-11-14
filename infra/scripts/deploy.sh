#!/usr/bin/env bash
# Staging deploy, run by the CD workflow over SSH (KAN-14). See infra/README.md, "Continuous deploy (CD)".
#
# Installed BY HAND to /usr/local/sbin/tsolve-deploy (root:root, mode 755, outside /srv/tsolve) and named in the deploy
# key's authorized_keys line as its forced command. A merged PR therefore cannot change what the key runs;
# after this file changes, a person reinstalls it (README).
#
# Usage: the commit SHA (40 lowercase hex) arrives in SSH_ORIGINAL_COMMAND, or as $1 when you run it by hand.
# The TSOLVE_* variables override the paths for a dry run by hand; they are ignored when SSH_ORIGINAL_COMMAND is set.
# Exit codes: 0 deployed, 2 bad argument, 3 another deploy is running, 1 any failed step (named in the output).
# Never prints values from the env file; never runs `docker compose down -v`.
set -Eeuo pipefail

cfg() { # cfg <VAR> <default>: the override only when this is not an SSH forced-command run
  if [[ -n "${SSH_ORIGINAL_COMMAND:-}" ]]; then printf '%s' "$2"; else printf '%s' "${!1:-$2}"; fi
}
SRC="$(cfg TSOLVE_SRC /srv/tsolve/src)"
STATE="$(cfg TSOLVE_STATE_DIR /srv/tsolve/staging)"
ENV_FILE="$(cfg TSOLVE_ENV_FILE "$STATE/.env")"
LOG="$(cfg TSOLVE_DEPLOY_LOG "$STATE/deploy.log")"
LOCK="$(cfg TSOLVE_DEPLOY_LOCK "$STATE/deploy.lock")"
DEPLOYED="$(cfg TSOLVE_DEPLOYED_FILE "$STATE/DEPLOYED")"
# Docker's own config and buildx state live in a folder only this script uses, so root-owned files in ~/.docker (left by
# an earlier `sudo docker ...`) cannot break the build (BUG-207). Under SSH it is fixed (a DOCKER_CONFIG in the
# environment is ignored; by hand it follows TSOLVE_STATE_DIR).
export DOCKER_CONFIG="$STATE/.docker"
COMPOSE_FILE="$SRC/infra/compose.staging.yml"
ENV_EXAMPLE="$SRC/infra/staging.env.example"
API_URL="$(cfg TSOLVE_SMOKE_API https://api.tsolve.io.vn)"
SITE_URL="$(cfg TSOLVE_SMOKE_SITE https://bluefin.tsolve.io.vn)"
# Files the stack mounts or reads once that CD does not apply by itself (a person does, README section 12).
MANUAL_RE='^infra/(compose\.staging\.yml|keycloak/(staging/)?realm-[^/]*\.json|keycloak/themes/|postgres/init/)'

STEP="start"
log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$LOG"; }
step() { STEP="$1"; log "STEP $1"; }
fail() { log "FAILED at step '$STEP'"; exit 1; }
trap 'fail' ERR

# --- argument: exactly 40 lowercase hex characters, never evaluated -------------------------------------------------
ARG="${SSH_ORIGINAL_COMMAND:-${1:-}}"
if [[ ! "$ARG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "usage: deploy.sh <40-hex commit sha> (via SSH_ORIGINAL_COMMAND or \$1)" >&2
  exit 2
fi
SHA="$ARG"

# --- one deploy at a time: fail fast when the lock is held ----------------------------------------------------------
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "another deploy is running (lock $LOCK)" >&2
  exit 3
fi

log "deploy requested: $SHA"

# --- docker config: must belong to, and be writable by, the user who runs this (no sudo for docker, ever) ---------------
step "docker config"
log "docker config dir: $DOCKER_CONFIG"
ME="$(id -un)"
if [[ ! -w "$STATE" ]]; then
  log "FAILED: $STATE not writable by $ME — run: sudo chown -R $ME:$ME $STATE"
  fail
fi
if [[ ! -d "$DOCKER_CONFIG" ]]; then
  mkdir -p "$DOCKER_CONFIG"
  chmod 700 "$DOCKER_CONFIG"
fi
if [[ ! -w "$DOCKER_CONFIG" || -n "$(find "$DOCKER_CONFIG" \( ! -user "$ME" -o ! -writable \) -print -quit)" ]]; then
  log "FAILED: $DOCKER_CONFIG not writable by $ME — run: sudo chown -R $ME:$ME $DOCKER_CONFIG"
  fail
fi
# The compose and buildx plugins must be found with this DOCKER_CONFIG: per-user plugins in ~/.docker/cli-plugins are not.
for plugin in compose buildx; do
  if ! docker "$plugin" version >/dev/null 2>&1; then
    log "FAILED: the docker $plugin plugin is not installed system-wide (/usr/libexec/docker/cli-plugins or /usr/local/lib/docker/cli-plugins); per-user plugins in ~/.docker/cli-plugins are not seen"
    fail
  fi
done

step "fetch"
git -C "$SRC" fetch origin '+refs/heads/main:refs/remotes/origin/main'

step "check the commit is on origin/main"
git -C "$SRC" cat-file -e "$SHA^{commit}"
if ! git -C "$SRC" merge-base --is-ancestor "$SHA" origin/main; then
  log "refused: $SHA is not an ancestor of origin/main"
  exit 1
fi

# PREVIOUS is the last SUCCESSFUL deploy (the DEPLOYED file), not the checkout's HEAD: a failed deploy leaves HEAD on
# the failed commit. Missing or invalid: unknown, so Caddy is recreated and every manual file is listed.
PREVIOUS=""
if [[ -r "$DEPLOYED" ]]; then
  PREVIOUS="$(head -n 1 "$DEPLOYED" | tr -d '[:space:]')"
  if [[ ! "$PREVIOUS" =~ ^[0-9a-f]{40}$ ]] || ! git -C "$SRC" cat-file -e "$PREVIOUS^{commit}" 2>/dev/null; then
    PREVIOUS=""
  fi
fi
if [[ -n "$PREVIOUS" ]]; then
  log "previous: $PREVIOUS (to roll back: redeploy $PREVIOUS)"
else
  log "previous: unknown (no valid $DEPLOYED)"
fi

step "checkout"
git -C "$SRC" checkout --detach "$SHA"

# --- the env file must have every variable name the template lists (names only, never values) -----------------------
step "env file names"
names() { grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$1" | tr -d '=' | sort -u; }
MISSING="$(comm -23 <(names "$ENV_EXAMPLE") <(names "$ENV_FILE") | tr '\n' ' ')"
if [[ -n "${MISSING// /}" ]]; then
  log "env file $ENV_FILE is missing these names: $MISSING"
  fail
fi

DC=(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE")
export TSOLVE_ENV_FILE="$ENV_FILE"

step "compose file check"
if ! "${DC[@]}" config -q 2>/dev/null; then
  # Compose can echo a malformed .env line in its error: keep it out of the output.
  log "compose config is not valid (details hidden; run 'docker compose ... config -q' by hand)"
  fail
fi

step "build backend web"
"${DC[@]}" build backend web

step "up backend worker web"
"${DC[@]}" up -d --wait --no-deps backend worker web

# --- files that need a person ---------------------------------------------------------------------------------------
if [[ -n "$PREVIOUS" ]]; then
  CHANGED="$(git -C "$SRC" diff --name-only "$PREVIOUS" "$SHA")"
  RECREATE_CADDY=0
  grep -q '^infra/caddy/' <<<"$CHANGED" && RECREATE_CADDY=1
else
  CHANGED="$(git -C "$SRC" ls-tree -r --name-only "$SHA" infra)"
  RECREATE_CADDY=1
fi
if [[ "$RECREATE_CADDY" == 1 ]]; then
  step "recreate caddy"
  # --force-recreate: the Caddyfile is a single-file bind mount that git replaces, and the service definition is the same
  "${DC[@]}" up -d --no-deps --force-recreate --wait caddy
fi
MANUAL="$(grep -E "$MANUAL_RE" <<<"$CHANGED" || true)"
if [[ -n "$MANUAL" ]]; then
  log "WARNING manual step needed: these files changed and are NOT applied by CD: $(tr '\n' ' ' <<<"$MANUAL")"
fi

# --- smoke checks, with retries -------------------------------------------------------------------------------------
retry() { # retry <what> <command...>: up to 12 tries, 5 s apart
  local what="$1"
  shift
  for _ in $(seq 1 12); do
    if "$@"; then
      log "SMOKE ok: $what"
      return 0
    fi
    sleep 5
  done
  log "SMOKE FAILED: $what"
  return 1
}
check_health() { curl -fsS --max-time 10 "$API_URL/actuator/health" | grep -q '"status":"UP"'; }
check_site() { [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$SITE_URL/")" == "200" ]]; }
check_me() {
  local body code ok=1
  body="$(mktemp)"
  code="$(curl -s -o "$body" -w '%{http_code}' --max-time 10 "$SITE_URL/api/v1/me" || true)"
  if [[ "$code" == "401" ]] && grep -Eq '"code"[[:space:]]*:[[:space:]]*"MSG07"' "$body"; then
    ok=0
  fi
  rm -f "$body"
  return "$ok"
}

step "smoke: api health"
retry "$API_URL/actuator/health is UP" check_health
step "smoke: web index"
retry "$SITE_URL/ answers 200" check_site
step "smoke: /api/v1/me"
retry "$SITE_URL/api/v1/me answers 401 MSG07" check_me

step "prune images"
docker image prune -f >/dev/null || log "image prune failed (ignored)"

step "record"
printf '%s\n' "$SHA" >"$DEPLOYED"
log "DEPLOYED $SHA (previous: ${PREVIOUS:-unknown})"
