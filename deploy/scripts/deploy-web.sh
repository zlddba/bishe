#!/usr/bin/env bash
# 管理面板原子发布：新目录就位后一次性切换 current 软链接，Nginx 只做 reload
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

WEB_DIR="${WEB_DIR:-/opt/patnest/web}"
DIST_DIR="${1:-$ROOT_DIR/webadmin/patnestServerAdminWeb/dist}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
CURRENT_LINK="$WEB_DIR/current"
RELEASES_DIR="$WEB_DIR/releases"

log()  { printf '\033[36m[web]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[web] %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[31m[web] %s\033[0m\n' "$*" >&2; exit 1; }

[[ -f "$DIST_DIR/index.html" ]] || die "找不到构建产物: $DIST_DIR/index.html（先执行 npm run build）"
[[ -f "$DIST_DIR/version.json" ]] || die "构建产物缺少 version.json"

build_id="$(sed -n 's/.*"build_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$DIST_DIR/version.json" | head -n1)"
[[ -n "$build_id" ]] || build_id="$(date +%Y%m%d%H%M%S)"

mkdir -p "$RELEASES_DIR"
release_dir="$RELEASES_DIR/$(date +%Y%m%d%H%M%S)"
mkdir -p "$release_dir"
cp -a "$DIST_DIR"/. "$release_dir"/
log "构建版本 $build_id 已就位: $release_dir"

ln -sfn "$release_dir" "$CURRENT_LINK.tmp"
mv -Tf "$CURRENT_LINK.tmp" "$CURRENT_LINK"

if ! nginx -t -q; then
  die "nginx 配置校验失败，current 已指向新目录，请修复后 reload"
fi
systemctl reload nginx
log "已切换 current -> $release_dir"

current_target="$(readlink -f "$CURRENT_LINK" || true)"
while IFS= read -r old; do
  [[ -z "$old" ]] && continue
  [[ "$RELEASES_DIR/$old" == "$current_target" ]] && continue
  rm -rf "${RELEASES_DIR:?}/$old"
done < <(ls -1 "$RELEASES_DIR" 2>/dev/null | sort -r | tail -n +$((KEEP_RELEASES + 1)))

if [[ -n "${DATABASE_URL:-}" ]] && command -v psql >/dev/null 2>&1; then
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -q -c \
    "INSERT INTO system_release (app, version, commit_sha, slot, note)
     VALUES ('web', '$build_id', '', '', 'atomic switch')" \
    || warn "写入发布记录失败（不影响发布结果）"
fi

log "管理面板发布完成: $build_id"
