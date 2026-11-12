#!/usr/bin/env bash
# Shared helpers for Linux deployment scripts. ASCII only, because the
# Windows counterpart is read by PowerShell 5.1 as ANSI when BOM-less.
# Detects engine/compose, keeps nginx bind-mount sources valid, checks
# engine liveness, and derives backend protocol and health probe settings.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${PATNEST_AGENT_CONTAINER:-0}" == "1" ]]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "docker CLI missing in container; cannot run release" >&2
    exit 1
  fi
  _compose_opts=()
  _proj="${PATNEST_COMPOSE_PROJECT_DIR:-}"
  if [[ -n "$_proj" ]]; then
    if [[ "$_proj" =~ ^([A-Za-z]):[\\/](.*)$ ]]; then
      _drive="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')"
      _rest="${BASH_REMATCH[2]//\\//}"
      _proj="/run/desktop/mnt/host/${_drive}/${_rest}"
      if [[ ! -f "$_proj/compose.yaml" ]]; then
        mkdir -p "$(dirname "$_proj")" 2>/dev/null || true
        ln -sfn "$ROOT_DIR" "$_proj" 2>/dev/null || true
      fi
      PATNEST_COMPOSE_PROJECT_DIR="$_proj"
    fi
    _compose_opts+=(--project-directory "$PATNEST_COMPOSE_PROJECT_DIR")
  fi
  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose "${_compose_opts[@]}")
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose "${_compose_opts[@]}")
  else
    echo "docker compose plugin missing in container" >&2
    exit 1
  fi
  ENGINE=docker
elif command -v podman-compose >/dev/null 2>&1; then
  COMPOSE=(podman-compose)
  ENGINE=podman
elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
  COMPOSE=(podman compose)
  ENGINE=podman
elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
  ENGINE=docker
else
  echo "no usable compose command (podman-compose / podman compose / docker compose)" >&2
  exit 1
fi

env_value() {
    sed -n "s/^$1=//p" .env 2>/dev/null | head -n1 | tr -d '\r'
}

#   error mounting ".../nginx/api_proxy.conf" ... not a directory:
#   Are you trying to mount a directory onto a file (or vice-versa)?
ensure_nginx_mount_files() {
    local rel content
    mkdir -p nginx 2>/dev/null || true

    rel="nginx/api_proxy.conf"
    content="proxy_pass http://patnest_api;"
    if [[ -d "$rel" ]]; then
        if [[ -n "$(ls -A "$rel" 2>/dev/null)" ]]; then
            echo "[fix] $rel is a non-empty directory; check manually: $PWD/$rel" >&2
        else
            rmdir "$rel" && echo "[fix] removed directory created by Docker at $rel" >&2
        fi
    fi
    if [[ ! -f "$rel" ]]; then
        printf '%s\n' "$content" > "$rel"
        echo "[fix] created missing mount source file $rel" >&2
    fi

    rel="nginx/upstream.conf"
    content="server api-blue:9201 max_fails=2 fail_timeout=5s;"
    if [[ -d "$rel" ]]; then
        if [[ -n "$(ls -A "$rel" 2>/dev/null)" ]]; then
            echo "[fix] $rel is a non-empty directory; check manually: $PWD/$rel" >&2
        else
            rmdir "$rel" && echo "[fix] removed directory created by Docker at $rel" >&2
        fi
    fi
    if [[ ! -f "$rel" ]]; then
        printf '%s\n' "$content" > "$rel"
        echo "[fix] created missing mount source file $rel" >&2
    fi
}

assert_engine_running() {
    local step="${1:-[install]}"
    if "$ENGINE" info >/dev/null 2>&1; then
        return 0
    fi
    echo >&2
    echo "${step} container engine is not running; script stopped." >&2
    if [[ "$ENGINE" == "docker" ]]; then
        echo "  Docker daemon is not running (or still starting)." >&2
        echo "  Start Docker, wait until it is fully ready, then rerun this script." >&2
        echo "  If it still fails, the engine likely crashed: systemctl restart docker and retry." >&2
    else
        echo "  Cannot reach $ENGINE (socket down or insufficient permissions)." >&2
        echo "  Check: systemctl status podman, or verify podman.sock path and permissions." >&2
    fi
    echo >&2
    exit 1
}

env_version() {
    env_value APP_VERSION
}

api_scheme() {
    local s
    s="$(env_value SERVER_SCHEME | tr '[:upper:]' '[:lower:]')"
    if [[ "$s" == "https" ]]; then printf 'https\n'; else printf 'http\n'; fi
}

write_api_proxy() {
    local scheme
    scheme="$(api_scheme)"
    printf 'proxy_pass %s://patnest_api;\n' "$scheme" > nginx/api_proxy.conf
    printf '%s\n' "$scheme"
}

api_health_url() {
    local slot="${1:-blue}"
    printf '%s://api-%s:9201/health\n' "$(api_scheme)" "$slot"
}

NGINX_CONTAINER="${NGINX_CONTAINER:-patnest-nginx}"

_NGINX_PROBE_TOOL=""

nginx_probe_tool() {
  if [[ -z "$_NGINX_PROBE_TOOL" ]]; then
    if "$ENGINE" exec "$NGINX_CONTAINER" sh -c 'command -v curl >/dev/null 2>&1' 2>/dev/null; then
      _NGINX_PROBE_TOOL=curl
    elif "$ENGINE" exec "$NGINX_CONTAINER" sh -c 'command -v wget >/dev/null 2>&1' 2>/dev/null; then
      _NGINX_PROBE_TOOL=wget
    else
      _NGINX_PROBE_TOOL=none
    fi
  fi
  printf '%s\n' "$_NGINX_PROBE_TOOL"
}

nginx_probe() {
    local url="$1"
    local insecure=0
    [[ "$url" == https://* ]] && insecure=1
    case "$(nginx_probe_tool)" in
        curl)
            if [[ "$insecure" == "1" ]]; then
                "$ENGINE" exec "$NGINX_CONTAINER" curl -fsSk --max-time 5 "$url"
            else
                "$ENGINE" exec "$NGINX_CONTAINER" curl -fsS --max-time 5 "$url"
            fi ;;
        wget)
            if [[ "$insecure" == "1" ]]; then
                "$ENGINE" exec "$NGINX_CONTAINER" wget -q --no-check-certificate -T 5 -O - "$url"
            else
                "$ENGINE" exec "$NGINX_CONTAINER" wget -q -T 5 -O - "$url"
            fi ;;
        *)    return 127 ;;
    esac
}

require_probe_tool() {
  local tool
  tool="$(nginx_probe_tool)"
  if [[ "$tool" == "none" ]]; then
    echo "${1:-[release]} container $NGINX_CONTAINER has neither curl nor wget; cannot run health checks" >&2
    exit 1
  fi
}

current_slot() {
  local slot
  slot="$(grep -E '^[[:space:]]*server[[:space:]]+api-(blue|green):' nginx/upstream.conf 2>/dev/null \
    | head -n1 | grep -oE 'api-(blue|green)' | sed 's/api-//' || true)"
  printf '%s\n' "${slot:-blue}"
}
