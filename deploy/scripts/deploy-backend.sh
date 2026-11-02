#!/usr/bin/env bash
# 后端蓝绿热更新：构建 -> 迁移 -> 新槽位启动 -> 健康检查 -> 切流量 -> 停旧槽位
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

APP_DIR="${APP_DIR:-/opt/patnest/api}"
SHARED_DIR="$APP_DIR/shared"
CURRENT_LINK="$APP_DIR/current"
RELEASES_DIR="$APP_DIR/releases"
UPSTREAM_FILE="${UPSTREAM_FILE:-/etc/nginx/patnest/upstream.conf}"
BLUE_PORT="${BLUE_PORT:-9200}"
GREEN_PORT="${GREEN_PORT:-9201}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
DRAIN_SECONDS="${DRAIN_SECONDS:-8}"
SKIP_MIGRATE="${SKIP_MIGRATE:-0}"

SRC_DIR="${SRC_DIR:-$ROOT_DIR/server/patnestServer}"
VERSION="${VERSION:-$(date +%Y.%m.%d.%H%M%S)}"
COMMIT="${COMMIT:-$(git -C "$SRC_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)}"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

log()  { printf '\033[36m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[deploy] %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[31m[deploy] %s\033[0m\n' "$*" >&2; exit 1; }

for cmd in go curl nginx systemctl git; do
  command -v "$cmd" >/dev/null 2>&1 || die "缺少命令: $cmd"
done
[[ -f "$UPSTREAM_FILE" ]] || die "找不到 upstream 配置: $UPSTREAM_FILE"
[[ -f "$SHARED_DIR/.env" ]] || die "找不到后端配置: $SHARED_DIR/.env"

current_port="$(grep -oE '127\.0\.0\.1:[0-9]+' "$UPSTREAM_FILE" | head -n1 | cut -d: -f2 || true)"
previous_target="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
case "$current_port" in
  "$BLUE_PORT")  current_slot=blue;  target_slot=green; target_port="$GREEN_PORT" ;;
  "$GREEN_PORT") current_slot=green; target_slot=blue;  target_port="$BLUE_PORT" ;;
  *)             current_slot="";    target_slot=blue;  target_port="$BLUE_PORT" ;;
esac
log "当前槽位: ${current_slot:-未配置}(${current_port:-无}) -> 目标槽位: $target_slot($target_port)"

cleanup_on_error() {
  local code=$?
  [[ $code -eq 0 ]] && return
  warn "发布失败（退出码 $code），尝试恢复旧槽位"
  systemctl stop "patnest-api@$target_slot" >/dev/null 2>&1 || true
  if [[ -n "${previous_target:-}" && -d "$previous_target" ]]; then
    ln -sfn "$previous_target" "$CURRENT_LINK.tmp" && mv -Tf "$CURRENT_LINK.tmp" "$CURRENT_LINK" || true
  fi
  if [[ -n "$current_slot" ]]; then
    printf 'server 127.0.0.1:%s max_fails=2 fail_timeout=5s;\n' "$current_port" > "$UPSTREAM_FILE"
    if nginx -t -q; then systemctl reload nginx || true; fi
    log "流量保持在 $current_slot 槽位（$current_port）"
  fi
}
trap cleanup_on_error EXIT

mkdir -p "$RELEASES_DIR" "$SHARED_DIR"
release_dir="$RELEASES_DIR/$VERSION"
mkdir -p "$release_dir"

log "构建后端 $VERSION（$COMMIT）"
ldflags="-s -w \
  -X patnestServer/internal/version.Version=$VERSION \
  -X patnestServer/internal/version.Commit=$COMMIT \
  -X patnestServer/internal/version.BuildTime=$BUILD_TIME"
( cd "$SRC_DIR" && CGO_ENABLED=0 go build -trimpath -ldflags "$ldflags" -o "$release_dir/patnest-api" ./cmd/api )
( cd "$SRC_DIR" && CGO_ENABLED=0 go build -trimpath -ldflags "$ldflags" -o "$release_dir/patnest-migrate" ./cmd/migrate )
chmod +x "$release_dir/patnest-api" "$release_dir/patnest-migrate"
chown -R patnest:patnest "$release_dir" 2>/dev/null || warn "chown 失败，请确认 patnest 用户可执行新版本"

if [[ "$SKIP_MIGRATE" != "1" ]]; then
  log "执行数据库在线迁移"
  ( cd "$SHARED_DIR" && "$release_dir/patnest-migrate" -dir "$SRC_DIR/serverdb/migrations" )
else
  warn "已跳过迁移（SKIP_MIGRATE=1）"
fi

ln -sfn "$release_dir" "$CURRENT_LINK.tmp"
mv -Tf "$CURRENT_LINK.tmp" "$CURRENT_LINK"

log "启动 $target_slot 槽位"
systemctl enable "patnest-api@$target_slot" >/dev/null 2>&1 || true
systemctl restart "patnest-api@$target_slot"

healthy=0
for _ in $(seq 1 "$HEALTH_RETRIES"); do
  if curl -fsS --max-time 3 "http://127.0.0.1:$target_port/health" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
if [[ "$healthy" != "1" ]]; then
  journalctl -u "patnest-api@$target_slot" -n 50 --no-pager || true
  die "新实例健康检查未通过，保持旧槽位"
fi
log "健康检查通过"

log "切换 Nginx 流量到 $target_port"
printf 'server 127.0.0.1:%s max_fails=2 fail_timeout=5s;\n' "$target_port" > "$UPSTREAM_FILE"
if ! nginx -t -q; then
  if [[ -n "$current_slot" ]]; then
    printf 'server 127.0.0.1:%s max_fails=2 fail_timeout=5s;\n' "$current_port" > "$UPSTREAM_FILE"
  fi
  die "nginx 配置校验失败，已恢复原 upstream"
fi
systemctl reload nginx

log "等待 $DRAIN_SECONDS 秒排空旧连接"
sleep "$DRAIN_SECONDS"
if [[ -n "$current_slot" && "$current_slot" != "$target_slot" ]]; then
  systemctl stop "patnest-api@$current_slot" || warn "停止旧槽位失败，请手工检查"
fi

DATABASE_URL="${DATABASE_URL:-}"
if [[ -z "$DATABASE_URL" ]]; then
  set +u
  # shellcheck disable=SC1090
  . "$SHARED_DIR/.env" 2>/dev/null || warn "解析 .env 失败，跳过发布记录写入"
  set -u
  if [[ -n "${DB_HOST:-}" && -n "${DB_USER:-}" ]]; then
    DATABASE_URL="postgres://${DB_USER}:${DB_PASSWORD:-}@${DB_HOST}:${DB_PORT:-5432}/${DB_NAME:-patnest_db}?sslmode=disable"
  fi
fi
if [[ -n "$DATABASE_URL" ]] && command -v psql >/dev/null 2>&1; then
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -q -c \
    "INSERT INTO system_release (app, version, commit_sha, slot, note)
     VALUES ('api', '$VERSION', '$COMMIT', '$target_slot', 'blue-green deploy')" \
    || warn "写入发布记录失败（不影响发布结果）"
fi

current_target="$(readlink -f "$CURRENT_LINK" || true)"
while IFS= read -r old; do
  [[ -z "$old" ]] && continue
  [[ "$RELEASES_DIR/$old" == "$current_target" ]] && continue
  rm -rf "${RELEASES_DIR:?}/$old"
done < <(ls -1 "$RELEASES_DIR" 2>/dev/null | sort -r | tail -n +$((KEEP_RELEASES + 1)))

trap - EXIT
log "发布完成: $VERSION"
curl -fsS "http://127.0.0.1:$target_port/api/v1/version" || true
echo
