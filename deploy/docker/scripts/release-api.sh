#!/usr/bin/env bash
# Blue/green backend release: rebuild api image, migrate, health check,
# switch nginx upstream, then stop the old slot.

set -euo pipefail
source "$(dirname "$0")/common.sh"

VERSION="${1:-${APP_VERSION:-$(date +%Y.%m.%d.%H%M%S)}}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
DRAIN_SECONDS="${DRAIN_SECONDS:-8}"

current="$(current_slot)"
if [[ "$current" == "blue" ]]; then target=green; else target=blue; fi

echo "[release] $current -> $target, version $VERSION"

assert_engine_running "[release]"

ensure_nginx_mount_files

echo "[release] build image patnest-api:$VERSION"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app build --pull api-blue

echo "[release] run database migrations"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile tools run --rm migrate

echo "[release] start api-$target"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app up -d --no-deps "api-$target"

require_probe_tool "[release]"
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
  echo "[release] new slot failed health check; keeping $current" >&2
  exit 1
fi

printf 'server api-%s:9201 max_fails=2 fail_timeout=5s;\n' "$target" > nginx/upstream.conf
write_api_proxy >/dev/null
"$ENGINE" exec patnest-nginx nginx -t
"$ENGINE" exec patnest-nginx nginx -s reload

echo "[release] traffic switched to $target; stop old slot in ${DRAIN_SECONDS}s"
sleep "$DRAIN_SECONDS"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app stop "api-$current" || true

echo "[release] done: $VERSION"
