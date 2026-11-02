#!/usr/bin/env bash
# 管理页面更新：替换 web/dist 后执行本脚本，重建 nginx 镜像并滚动重启
set -euo pipefail
source "$(dirname "$0")/common.sh"

VERSION="${1:-${APP_VERSION:-$(env_version)}}"
VERSION="${VERSION:-1.0.0}"

[[ -f web/dist/index.html ]] || { echo "缺少 web/dist/index.html" >&2; exit 1; }

echo "[web] 打包镜像 patnest-web:$VERSION"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app build --pull nginx

echo "[web] 重启 nginx 容器"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app up -d --no-deps --force-recreate nginx

"$ENGINE" exec patnest-nginx nginx -t
echo "[web] 完成，页面版本：$(sed -n 's/.*\"build_id\"[: ]*\"\\([^\"]*\\)\".*/\\1/p' web/dist/version.json 2>/dev/null || echo unknown)"
