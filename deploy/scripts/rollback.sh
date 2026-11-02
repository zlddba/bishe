#!/usr/bin/env bash
# 回滚到上一个 release 目录：rollback.sh [release目录名]
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/patnest/api}"
CURRENT_LINK="$APP_DIR/current"
RELEASES_DIR="$APP_DIR/releases"
UPSTREAM_FILE="${UPSTREAM_FILE:-/etc/nginx/patnest/upstream.conf}"
BLUE_PORT="${BLUE_PORT:-9200}"
GREEN_PORT="${GREEN_PORT:-9201}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
DRAIN_SECONDS="${DRAIN_SECONDS:-5}"

log() { printf '\033[36m[rollback]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[rollback] %s\033[0m\n' "$*" >&2; exit 1; }

[[ -d "$RELEASES_DIR" ]] || die "找不到 releases 目录: $RELEASES_DIR"

target_release="${1:-}"
if [[ -z "$target_release" ]]; then
  target_release="$(ls -1 "$RELEASES_DIR" | sort -r | sed -n '2p')"
fi
[[ -n "$target_release" ]] || die "没有可回滚的历史版本"
release_dir="$RELEASES_DIR/$target_release"
[[ -x "$release_dir/patnest-api" ]] || die "版本不可执行: $release_dir/patnest-api"

current_port="$(grep -oE '127\.0\.0\.1:[0-9]+' "$UPSTREAM_FILE" | head -n1 | cut -d: -f2 || true)"
case "$current_port" in
  "$BLUE_PORT")  current_slot=blue;  target_slot=green; target_port="$GREEN_PORT" ;;
  "$GREEN_PORT") current_slot=green; target_slot=blue;  target_port="$BLUE_PORT" ;;
  *) die "无法从 $UPSTREAM_FILE 识别当前槽位" ;;
esac

log "回滚到 $target_release，目标槽位 $target_slot($target_port)"
previous_target="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
ln -sfn "$release_dir" "$CURRENT_LINK.tmp"
mv -Tf "$CURRENT_LINK.tmp" "$CURRENT_LINK"
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
  if [[ -n "${previous_target:-}" && -d "$previous_target" ]]; then
    ln -sfn "$previous_target" "$CURRENT_LINK.tmp" && mv -Tf "$CURRENT_LINK.tmp" "$CURRENT_LINK" || true
  fi
  die "回滚版本健康检查失败，未切换流量"
fi

printf 'server 127.0.0.1:%s max_fails=2 fail_timeout=5s;\n' "$target_port" > "$UPSTREAM_FILE"
nginx -t -q || die "nginx 配置校验失败"
systemctl reload nginx
sleep "$DRAIN_SECONDS"
systemctl stop "patnest-api@$current_slot" || log "旧槽位停止失败，请手工检查"

log "回滚完成: $target_release"
curl -fsS "http://127.0.0.1:$target_port/api/v1/version" || true
echo
