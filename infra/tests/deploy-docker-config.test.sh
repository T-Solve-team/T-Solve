#!/usr/bin/env bash
# BUG-207 (KAN-14): the deploy script uses its own DOCKER_CONFIG and checks, before the build, that the deploy user can
# write to it. Test names below start with BUG-207.
#
# Runs infra/scripts/deploy.sh as a non-root user inside a throwaway Linux container (needs docker and the ubuntu image).
# Proves:
#   (a) a file under DOCKER_CONFIG owned by another user makes the script fail at step "docker config" with the chown hint;
#   (b) a clean folder passes that step (the script goes on to the next step);
#   (c) under SSH_ORIGINAL_COMMAND a DOCKER_CONFIG (or TSOLVE_STATE_DIR) from the environment is ignored.
# The script is stopped on purpose at its next step ("fetch": no git repository in the container), so no docker or curl
# stub is needed: nothing reaches the build.
# Usage: bash infra/tests/deploy-docker-config.test.sh
set -Eeuo pipefail

if [[ -z "${INSIDE_CONTAINER:-}" ]]; then
  HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  SCRIPT="$HERE/../scripts/deploy.sh"
  # MSYS_NO_PATHCONV: Git Bash on Windows must not rewrite the container paths.
  MSYS_NO_PATHCONV=1 docker run --rm -e INSIDE_CONTAINER=1 \
    -v "$SCRIPT:/deploy.sh:ro" -v "$HERE/deploy-docker-config.test.sh:/test.sh:ro" \
    ubuntu:24.04 bash /test.sh
  exit $?
fi

# ---- inside the container, as root ---------------------------------------------------------------------------------
FAILURES=0
pass() { echo "ok   BUG-207 $1"; }
bad() {
  echo "FAIL BUG-207 $1"
  FAILURES=$((FAILURES + 1))
}

SHA="$(printf 'a%.0s' {1..40})"
useradd -m deploy
useradd -m other
mkdir -p /srv/tsolve/staging /work
chown deploy:deploy /srv/tsolve/staging /work
install -m 755 /deploy.sh /usr/local/sbin/tsolve-deploy

# run_deploy <mode> <extra env...>: runs the script as `deploy`; prints the deploy log. mode: ssh | hand (state in /work)
run_deploy() {
  local mode="$1"
  shift
  local state=/work/state
  [[ "$mode" == ssh ]] && state=/srv/tsolve/staging
  mkdir -p "$state"
  chown deploy:deploy "$state"
  : >"$state/deploy.log"
  chown deploy:deploy "$state/deploy.log"
  if [[ "$mode" == ssh ]]; then
    su deploy -s /bin/bash -c "env -i PATH=\"$PATH\" HOME=/home/deploy $* SSH_ORIGINAL_COMMAND=$SHA /usr/local/sbin/tsolve-deploy" >/dev/null 2>&1 || true
  else
    su deploy -s /bin/bash -c "env -i PATH=\"$PATH\" HOME=/home/deploy TSOLVE_STATE_DIR=$state $* /usr/local/sbin/tsolve-deploy $SHA" >/dev/null 2>&1 || true
  fi
  cat "$state/deploy.log"
}

# A stub docker: `docker compose version` and `docker buildx version` succeed unless /work/no-<plugin> exists.
cat >/usr/local/bin/docker <<'STUB'
#!/bin/sh
[ -e "/work/no-$1" ] && exit 1
exit 0
STUB
chmod 755 /usr/local/bin/docker

# (a) a root-owned file (buildx/.lock) in a folder the user owns: only the `find` clause can trip
rm -rf /work/state
mkdir -p /work/state/.docker/buildx
touch /work/state/.docker/buildx/.lock
chown -R deploy:deploy /work/state/.docker
chown root:root /work/state/.docker/buildx/.lock
OUT="$(run_deploy hand)"
if grep -q "STEP docker config" <<<"$OUT" && grep -q "FAILED: /work/state/.docker not writable by deploy" <<<"$OUT" &&
  grep -q "run: sudo chown -R deploy:deploy /work/state/.docker" <<<"$OUT" && ! grep -q "STEP fetch" <<<"$OUT"; then
  pass "(a) a file owned by another user fails at step 'docker config' with the chown hint"
else
  bad "(a) expected a failure at 'docker config' with the chown hint; log was:"
  echo "$OUT"
fi

# (a2) the folder itself owned by root and not writable
rm -rf /work/state
mkdir -p /work/state/.docker
chown deploy:deploy /work/state
chown root:root /work/state/.docker
OUT="$(run_deploy hand)"
if grep -q "FAILED: /work/state/.docker not writable by deploy" <<<"$OUT" && ! grep -q "STEP fetch" <<<"$OUT"; then
  pass "(a2) a root-owned folder fails at step 'docker config' with the chown hint"
else
  bad "(a2) expected a failure at 'docker config'; log was:"
  echo "$OUT"
fi

# (d) a missing compose or buildx plugin fails at step 'docker config' with the system-wide hint
for plugin in compose buildx; do
  rm -rf /work/state
  mkdir -p /work/state/.docker
  chown -R deploy:deploy /work/state
  touch "/work/no-$plugin"
  OUT="$(run_deploy hand)"
  rm -f "/work/no-$plugin"
  if grep -q "FAILED: the docker $plugin plugin is not installed system-wide" <<<"$OUT" && ! grep -q "STEP fetch" <<<"$OUT"; then
    pass "(d) a missing $plugin plugin fails at step 'docker config' with the hint"
  else
    bad "(d) expected a failure for the missing $plugin plugin; log was:"
    echo "$OUT"
  fi
done

# (b) a clean folder (and a missing one, which the script creates with mode 700)
rm -rf /work/state
mkdir -p /work/state/.docker
chown -R deploy:deploy /work/state
OUT="$(run_deploy hand)"
if grep -q "STEP docker config" <<<"$OUT" && grep -q "STEP fetch" <<<"$OUT" && ! grep -q "not writable" <<<"$OUT"; then
  pass "(b) a clean folder passes step 'docker config'"
else
  bad "(b) expected the script to pass 'docker config'; log was:"
  echo "$OUT"
fi
rm -rf /work/state
OUT="$(run_deploy hand)"
MODE="$(stat -c %a /work/state/.docker 2>/dev/null || echo missing)"
if grep -q "STEP fetch" <<<"$OUT" && [[ "$MODE" == 700 ]]; then
  pass "(b) a missing folder is created with mode 700 (mode $MODE)"
else
  bad "(b) expected the folder to be created with mode 700 (mode $MODE); log was:"
  echo "$OUT"
fi

# (c) under SSH_ORIGINAL_COMMAND the environment cannot change the folder
rm -rf /srv/tsolve/staging/.docker
OUT="$(run_deploy ssh DOCKER_CONFIG=/work/evil TSOLVE_STATE_DIR=/work/state)"
if grep -q "docker config dir: /srv/tsolve/staging/.docker" <<<"$OUT" && [[ ! -e /work/evil ]]; then
  pass "(c) DOCKER_CONFIG cannot be overridden under SSH_ORIGINAL_COMMAND"
else
  bad "(c) expected /srv/tsolve/staging/.docker and no /work/evil; log was:"
  echo "$OUT"
fi

if [[ "$FAILURES" -ne 0 ]]; then
  echo "BUG-207: $FAILURES check(s) failed"
  exit 1
fi
echo "BUG-207: all checks passed"
