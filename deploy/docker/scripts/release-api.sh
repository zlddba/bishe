#!/usr/bin/env bash
# 后端蓝绿热更新：替换 artifacts/patnest-api 后执行本脚本
set -euo pipefail
source "$(dirname "$0")/common.sh"

VERSION="${1:-${APP_VERSION:-$(date +%Y.%m.%d.%H%M%S)}}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
DRAIN_SECONDS="${DRAIN_SECONDS:-8}"

current="$(current_slot)"
if [[ "$current" == "blue" ]]; then target=green; else target=blue; fi

echo "[release] $current -> $target，版本 $VERSION"

echo "[release] 打包镜像 patnest-api:$VERSION"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app build --pull api-blue

echo "[release] 执行数据库迁移"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile tools run --rm migrate

echo "[release] 启动 api-$target"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app up -d --no-deps "api-$target"

require_probe_tool "[release]"
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
  echo "[release] 新槽位健康检查失败，保持 $current" >&2
  exit 1
fi

printf 'server api-%s:9201 max_fails=2 fail_timeout=5s;\n' "$target" > nginx/upstream.conf
"$ENGINE" exec patnest-nginx nginx -t
"$ENGINE" exec patnest-nginx nginx -s reload

echo "[release] 流量已切到 $target，${DRAIN_SECONDS}s 后停止旧槽位"
sleep "$DRAIN_SECONDS"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app stop "api-$current" || true

echo "[release] 完成：$VERSION"
