#!/usr/bin/env bash
# 单独执行/查看数据库迁移：./migrate.sh -status
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

APP_DIR="${APP_DIR:-/opt/patnest/api}"
SHARED_DIR="$APP_DIR/shared"
BIN="$APP_DIR/current/patnest-migrate"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-$ROOT_DIR/server/patnestServer/serverdb/migrations}"

[[ -x "$BIN" ]] || { echo "未找到 $BIN，请先执行一次后端发布以生成迁移工具" >&2; exit 1; }
[[ -d "$MIGRATIONS_DIR" ]] || { echo "迁移目录不存在: $MIGRATIONS_DIR" >&2; exit 1; }

cd "$SHARED_DIR"
exec "$BIN" -dir "$MIGRATIONS_DIR" "$@"
