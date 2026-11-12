#!/usr/bin/env bash
# Update agent container entrypoint.
# The docker CLI inside a container forwards build context and bind mount
# source paths to the host daemon verbatim, so /pkg is invisible to the daemon.
# Mount the package at /pkg, resolve its real host path with docker inspect,
# use it as compose --project-directory, and symlink it back to /pkg.
# On Windows (Docker Desktop), drive paths such as D:/docker/pkg are
# normalized to /run/desktop/mnt/host/d/docker/pkg to avoid "too many colons".

set -euo pipefail

PKG=/pkg

fail() {
  echo "[update-agent] $*" >&2
  exit 1
}

[[ -d "$PKG" ]] ||
  fail "$PKG missing inside container: package not mounted. compose needs '- \${PACKAGE_HOST_PATH}:${PKG}'."
[[ -f "$PKG/compose.yaml" ]] ||
  fail "compose.yaml not found under $PKG; mount is not the package root."
[[ -f "$PKG/scripts/update-agent.sh" ]] ||
  fail "scripts/update-agent.sh not found under $PKG; package incomplete."

DOCKER_SOCK="${DOCKER_SOCKET:-/var/run/docker.sock}"
[[ -S "$DOCKER_SOCK" ]] ||
  fail "docker socket ($DOCKER_SOCK) not mounted; cannot reach host daemon. If the path differs, set DOCKER_SOCKET in .env."

command -v docker >/dev/null 2>&1 || fail "docker CLI missing in image."
if ! docker compose version >/dev/null 2>&1; then
  for candidate in \
    /usr/libexec/docker/cli-plugins/docker-compose \
    /usr/lib/docker/cli-plugins/docker-compose \
    /usr/bin/docker-compose; do
    if [[ -x "$candidate" ]]; then
      mkdir -p /usr/local/lib/docker/cli-plugins
      ln -sf "$candidate" /usr/local/lib/docker/cli-plugins/docker-compose
      break
    fi
  done
fi
docker compose version >/dev/null 2>&1 || fail "docker compose plugin unavailable in image."

to_daemon_path() {
  local p="$1"
  if [[ ! "$p" =~ ^([A-Za-z]):[\\/](.*)$ ]]; then
    printf '%s' "$p"
    return 0
  fi
  local drive rest
  drive="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')"
  rest="${BASH_REMATCH[2]//\\//}"
  printf '/run/desktop/mnt/host/%s/%s' "$drive" "$rest"
}

HOST_ROOT="${PACKAGE_HOST_ROOT:-}"
if [[ -z "$HOST_ROOT" ]]; then
  self_id="$(cat /etc/hostname 2>/dev/null || hostname)"
  HOST_ROOT="$(docker inspect \
      --format '{{range .Mounts}}{{if eq .Destination "/pkg"}}{{.Source}}{{end}}{{end}}' \
      "$self_id" 2>/dev/null || true)"
fi
[[ -n "$HOST_ROOT" ]] ||
  fail "cannot resolve host path of the package (docker inspect failed). Set PACKAGE_HOST_ROOT in .env."

HOST_ROOT="$(to_daemon_path "$HOST_ROOT")"

if [[ "$HOST_ROOT" != "$PKG" && ! -f "$HOST_ROOT/compose.yaml" ]]; then
  mkdir -p "$(dirname "$HOST_ROOT")" 2>/dev/null || true
  ln -sfn "$PKG" "$HOST_ROOT" 2>/dev/null ||
    fail "cannot create symlink $HOST_ROOT -> $PKG (permission?). Set PACKAGE_HOST_ROOT=$PKG in .env to skip."
fi
[[ -f "$HOST_ROOT/compose.yaml" ]] ||
  fail "compose.yaml not visible under host path $HOST_ROOT; mount source is not the package root."

export PACKAGE_ROOT="$PKG"
export PATNEST_AGENT_CONTAINER=1
export PACKAGE_HOST_PATH="$HOST_ROOT"
export PATNEST_COMPOSE_PROJECT_DIR="$HOST_ROOT"

echo "[update-agent] container mode"
echo "[update-agent]   package (container): $PKG"
echo "[update-agent]   package (daemon):    $HOST_ROOT"

cd "$PKG"
exec bash "$PKG/scripts/update-agent.sh"
