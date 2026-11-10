#!/usr/bin/env bash
# 回滚到本机已有的某个后端镜像：./scripts/rollback.sh 1.0.0
set -euo pipefail
source "$(dirname "$0")/common.sh"

VERSION="${1:-}"
[[ -n "$VERSION" ]] || { echo "用法: bash scripts/rollback.sh <版本号>，可用版本见 $ENGINE images | grep patnest-api" >&2; exit 1; }
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
DRAIN_SECONDS="${DRAIN_SECONDS:-5}"

if ! "$ENGINE" image inspect "patnest-api:$VERSION" >/dev/null 2>&1; then
  echo "本机不存在镜像 patnest-api:$VERSION，无法回滚" >&2
  exit 1
fi

current="$(current_slot)"
if [[ "$current" == "blue" ]]; then target=green; else target=blue; fi

echo "[rollback] 使用 patnest-api:$VERSION，$current -> $target"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app up -d --no-deps "api-$target"

require_probe_tool "[rollback]"
healthy=0
for _ in $(seq 1 "$HEALTH_RETRIES"); do
  if nginx_probe "http://api-$target:9201/health" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
if [[ "$healthy" != "1" ]]; then
  "$ENGINE" logs --tail 60 "patnest-api-$target" || true
  APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app stop "api-$target" || true
  echo "[rollback] 目标版本健康检查失败，未切换流量" >&2
  exit 1
fi

printf 'server api-%s:9201 max_fails=2 fail_timeout=5s;\n' "$target" > nginx/upstream.conf
"$ENGINE" exec patnest-nginx nginx -t
"$ENGINE" exec patnest-nginx nginx -s reload
sleep "$DRAIN_SECONDS"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app stop "api-$current" || true

echo "[rollback] 已回滚到 $VERSION"
