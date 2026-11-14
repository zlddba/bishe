#!/usr/bin/env bash
# First install: build images, run migrations, start redis/nginx/api-blue,
# then start the update agent per AGENT_MODE (container/host/none).

set -euo pipefail

# Optional engine override. Priority: --engine, PATNEST_ENGINE env var,
# PATNEST_ENGINE in .env, then auto (docker first, podman fallback).
while [[ $# -gt 0 ]]; do
  case "$1" in
    --engine)
      [[ $# -ge 2 ]] || { echo "--engine needs a value: docker, podman or auto" >&2; exit 1; }
      export PATNEST_ENGINE="$2"
      shift 2
      ;;
    --engine=*)
      export PATNEST_ENGINE="${1#--engine=}"
      shift
      ;;
    -h|--help)
      echo "Usage: bash scripts/install.sh [--engine docker|podman|auto]"
      echo "  Default: docker, falling back to podman when docker is unavailable."
      echo "  Neither available: the script exits with an error."
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      echo "Usage: bash scripts/install.sh [--engine docker|podman|auto]" >&2
      exit 1
      ;;
  esac
done

source "$(dirname "$0")/common.sh"

[[ -f .env ]] || { echo "copy .env.example to .env and fill it in first" >&2; exit 1; }
[[ -f artifacts/patnest-api ]] || { echo "missing artifacts/patnest-api" >&2; exit 1; }
[[ -f web/dist/index.html ]] || { echo "missing web/dist" >&2; exit 1; }

db_password="$(env_value DB_PASSWORD)"
[[ -n "$db_password" && "$db_password" != CHANGE_ME* ]] || {
  echo "[install] DB_PASSWORD in .env is empty or still a placeholder" >&2
  exit 1
}
jwt_secret="$(env_value JWT_SECRET)"
if [[ -z "$jwt_secret" || "$jwt_secret" == CHANGE_ME* || ${#jwt_secret} -lt 32 ]]; then
  echo "[install] warning: JWT_SECRET empty, placeholder or shorter than 32 chars; set a random string in .env" >&2
fi

VERSION="${APP_VERSION:-$(env_version)}"
VERSION="${VERSION:-1.0.0}"
PUBLIC_PORT="${PUBLIC_PORT:-$(env_value PUBLIC_PORT)}"
PUBLIC_PORT="${PUBLIC_PORT:-9200}"

mkdir -p data/redis
mkdir -p data/updates/{tmp,inbox,manual,queue,processing,failed,status,staging,logs,backups}
chmod -R a+rwX data/updates 2>/dev/null || true

# The API container runs as uid/gid 10001 (see Dockerfile.api) and writes
# certificates uploaded from the admin page into nginx/tls. The bind-mount
# source is created by root, so hand it over or every upload fails with
# "permission denied".
mkdir -p nginx/tls
chown -R 10001:10001 nginx/tls 2>/dev/null || true
chmod 750 nginx/tls 2>/dev/null || true

# acme.sh runs inside the API container (uid 10001) and keeps its account
# keys plus the Cloudflare token under this directory. The bind-mount source
# is created by root, so hand it over or every issue attempt fails.
mkdir -p data/acme
chown -R 10001:10001 data/acme 2>/dev/null || true
chmod -R 700 data/acme 2>/dev/null || true

assert_engine_running "[install]"

ensure_nginx_mount_files

# nginx terminates TLS on 9200 and reads the certificate at startup, so the
# mounted directory must already hold a usable pair. A real certificate is
# uploaded from the admin page later; until then a self-signed one keeps the
# site running instead of crash-looping on a missing file.
if [[ ! -s nginx/tls/fullchain.pem || ! -s nginx/tls/privkey.pem ]]; then
  echo "[install] no TLS certificate yet, generating a self-signed placeholder"
  "$ENGINE" run --rm -v "$PWD/nginx/tls:/tls" docker.io/library/nginx:latest \
    openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
      -subj "/CN=patnest.local" \
      -keyout /tls/privkey.pem -out /tls/fullchain.pem >/dev/null 2>&1 ||
    echo "[install] warning: could not generate a placeholder certificate; nginx may fail to start" >&2
  chown 10001:10001 nginx/tls/fullchain.pem nginx/tls/privkey.pem 2>/dev/null || true
  chmod 600 nginx/tls/fullchain.pem nginx/tls/privkey.pem 2>/dev/null || true
fi

echo "[install] build images patnest-api:$VERSION / patnest-web:$VERSION"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app build --pull api-blue nginx

echo "[install] initialize update directories"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile tools run --rm updates-init

echo "[install] run database migrations"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile tools run --rm migrate

echo "[install] start redis / nginx / api-blue"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app up -d redis nginx api-blue

write_api_proxy >/dev/null

echo "[install] wait for api-blue health check"
require_probe_tool "[install]"
healthy=0
for _ in $(seq 1 "${HEALTH_RETRIES:-60}"); do
  if nginx_probe "$(api_health_url blue)" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
if [[ "$healthy" != "1" ]]; then
  "$ENGINE" logs --tail 80 patnest-api-blue || true
  echo "[install] api-blue health check failed; check database settings in .env" >&2
  exit 1
fi
nginx_probe "$(api_health_url blue)" || true
echo

# AGENT_MODE:

set_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" .env 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}

stop_host_agent() {
  local pid_file="data/updates/agent.pid" old=""
  if [[ -f "$pid_file" ]]; then
    old="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$old" && -r "/proc/$old/cmdline" ]] &&
       tr '\0' ' ' < "/proc/$old/cmdline" | grep -q "update-agent"; then
      kill "$old" 2>/dev/null || true
      sleep 1
    fi
    rm -f "$pid_file"
  fi
  rmdir data/updates/agent.lock 2>/dev/null || true
}

stop_container_agent() {
  "${COMPOSE[@]}" --profile agent stop update-agent >/dev/null 2>&1 || true
}

start_host_agent() {
  if [[ ! -f scripts/update-agent.sh ]]; then
    echo "[install] missing scripts/update-agent.sh; skipping update agent" >&2
    return 0
  fi
  if [[ -f data/updates/agent.pid ]] &&
     kill -0 "$(cat data/updates/agent.pid 2>/dev/null)" 2>/dev/null; then
    echo "[install] host update agent already running"
    return 0
  fi
  nohup bash scripts/update-agent.sh >> data/updates/agent.log 2>&1 &
  echo "[install] host update agent started (PID $!)"
}

start_container_agent() {
  local pkg_path
  pkg_path="$(pwd -P)"
  if [[ "$pkg_path" == *" "* ]]; then
    echo "[install] path contains spaces ($pkg_path); compose bind mounts are unreliable, use another directory" >&2
    return 1
  fi
  if [[ "$ENGINE" != "docker" ]]; then
    echo "[install] warning: $ENGINE selected, but the update agent container uses the docker CLI." >&2
    echo "  Set DOCKER_SOCKET to the podman compat socket (e.g. /run/podman/podman.sock)." >&2
    echo "  If docker inspect cannot resolve /pkg, set PACKAGE_HOST_ROOT to the absolute package path." >&2
  fi

  set_env PACKAGE_HOST_PATH "$pkg_path"

  echo "[install] stop any existing host update agent first"
  stop_host_agent

  echo "[install] build update agent image patnest-update-agent:$VERSION"
  APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile agent build --pull update-agent || return 1

  echo "[install] start containerized update agent"
  APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile agent up -d update-agent || return 1

  sleep 2
  if ! "$ENGINE" ps --format '{{.Names}}\t{{.Status}}' | grep -q "^patnest-update-agent"; then
    echo "[install] container did not stay up; recent logs:" >&2
    "$ENGINE" logs --tail 40 patnest-update-agent >&2 || true
    return 1
  fi
  echo "[install] containerized update agent started ($ENGINE logs -f patnest-update-agent)"
}

AGENT_MODE="${AGENT_MODE:-$(env_value AGENT_MODE)}"
AGENT_MODE="${AGENT_MODE:-container}"

case "$AGENT_MODE" in
  container|auto)
    if ! start_container_agent; then
      stop_container_agent
      echo "[install] containerized update agent failed to start. Fix per logs above and rerun:" >&2
      echo "[install]   bash scripts/install.sh" >&2
      echo "[install] if docker.sock cannot be mounted, use AGENT_MODE=host (host process mode)." >&2
      exit 1
    fi
    ;;
  host)
    stop_container_agent
    start_host_agent
    ;;
  none)
    stop_container_agent
    stop_host_agent
    echo "[install] AGENT_MODE=none: update agent not started; system update page shows offline"
    ;;
  *)
    echo "[install] unknown AGENT_MODE='$AGENT_MODE' (container/host/none)" >&2
    exit 1
    ;;
esac

echo "[install] done, open http://<SERVER_IP>:${PUBLIC_PORT}/"
