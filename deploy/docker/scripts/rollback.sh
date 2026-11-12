#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"

VERSION="${1:-}"
[[ -n "$VERSION" ]] || { echo "usage: bash scripts/rollback.sh <version>; available versions: $ENGINE images | grep patnest-api" >&2; exit 1; }
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
DRAIN_SECONDS="${DRAIN_SECONDS:-5}"

assert_engine_running "[rollback]"

ensure_nginx_mount_files

if ! "$ENGINE" image inspect "patnest-api:$VERSION" >/dev/null 2>&1; then
  echo "image patnest-api:$VERSION not found locally; cannot roll back" >&2
  exit 1
fi

current="$(current_slot)"
if [[ "$current" == "blue" ]]; then target=green; else target=blue; fi

echo "[rollback] use patnest-api:$VERSION, $current -> $target"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app up -d --no-deps "api-$target"

require_probe_tool "[rollback]"
healthy=0
for _ in $(seq 1 "$HEALTH_RETRIES"); do
  if nginx_probe "$(api_health_url "$target")" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
if [[ "$healthy" != "1" ]]; then
  "$ENGINE" logs --tail 60 "patnest-api-$target" || true
  APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app stop "api-$target" || true
  echo "[rollback] target failed health check; traffic unchanged" >&2
  exit 1
fi

printf 'server api-%s:9201 max_fails=2 fail_timeout=5s;\n' "$target" > nginx/upstream.conf
write_api_proxy >/dev/null
"$ENGINE" exec patnest-nginx nginx -t
"$ENGINE" exec patnest-nginx nginx -s reload
sleep "$DRAIN_SECONDS"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app stop "api-$current" || true

echo "[rollback] rolled back to $VERSION"
