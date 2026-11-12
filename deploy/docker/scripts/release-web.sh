#!/usr/bin/env bash
# Rebuild the admin panel nginx image and restart it after replacing web/dist.

set -euo pipefail
source "$(dirname "$0")/common.sh"

VERSION="${1:-${APP_VERSION:-$(env_version)}}"
VERSION="${VERSION:-1.0.0}"

[[ -f web/dist/index.html ]] || { echo "missing web/dist/index.html" >&2; exit 1; }

assert_engine_running "[web]"

ensure_nginx_mount_files

echo "[web] build image patnest-web:$VERSION"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app build --pull nginx

echo "[web] restart nginx container"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app up -d --no-deps --force-recreate nginx

"$ENGINE" exec patnest-nginx nginx -t
echo "[web] done, page version: $(sed -n 's/.*\"build_id\"[: ]*\"\\([^\"]*\\)\".*/\\1/p' web/dist/version.json 2>/dev/null || echo unknown)"
