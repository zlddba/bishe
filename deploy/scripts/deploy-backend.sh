#!/usr/bin/env bash
# Blue/green backend release: build -> migrate -> start target slot ->
# health check -> switch nginx upstream -> stop old slot.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

APP_DIR="${APP_DIR:-/opt/patnest/api}"
SHARED_DIR="$APP_DIR/shared"
CURRENT_LINK="$APP_DIR/current"
RELEASES_DIR="$APP_DIR/releases"
UPSTREAM_FILE="${UPSTREAM_FILE:-/etc/nginx/patnest/upstream.conf}"
API_PROXY_FILE="${API_PROXY_FILE:-/etc/nginx/patnest/api_proxy.conf}"
API_SCHEME="${API_SCHEME:-http}"
if [[ "$API_SCHEME" != "http" && "$API_SCHEME" != "https" ]]; then
  die "API_SCHEME must be http or https, got: $API_SCHEME"
fi
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
  command -v "$cmd" >/dev/null 2>&1 || die "missing command: $cmd"
done
[[ -f "$UPSTREAM_FILE" ]] || die "cannot find upstream config: $UPSTREAM_FILE"
[[ -f "$SHARED_DIR/.env" ]] || die "cannot find backend config: $SHARED_DIR/.env"

current_port="$(grep -oE '127\.0\.0\.1:[0-9]+' "$UPSTREAM_FILE" | head -n1 | cut -d: -f2 || true)"
previous_target="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
case "$current_port" in
  "$BLUE_PORT")  current_slot=blue;  target_slot=green; target_port="$GREEN_PORT" ;;
  "$GREEN_PORT") current_slot=green; target_slot=blue;  target_port="$BLUE_PORT" ;;
  *)             current_slot="";    target_slot=blue;  target_port="$BLUE_PORT" ;;
esac
log "current slot: ${current_slot:-unset}(${current_port:-none}) -> target slot: $target_slot($target_port)"

cleanup_on_error() {
  local code=$?
  [[ $code -eq 0 ]] && return
  warn "release failed (exit $code); restoring old slot"
  systemctl stop "patnest-api@$target_slot" >/dev/null 2>&1 || true
  if [[ -n "${previous_target:-}" && -d "$previous_target" ]]; then
    ln -sfn "$previous_target" "$CURRENT_LINK.tmp" && mv -Tf "$CURRENT_LINK.tmp" "$CURRENT_LINK" || true
  fi
  if [[ -n "$current_slot" ]]; then
    printf 'server 127.0.0.1:%s max_fails=2 fail_timeout=5s;\n' "$current_port" > "$UPSTREAM_FILE"
    if nginx -t -q; then systemctl reload nginx || true; fi
    log "traffic stays on $current_slot ($current_port)"
  fi
}
trap cleanup_on_error EXIT

mkdir -p "$RELEASES_DIR" "$SHARED_DIR"
release_dir="$RELEASES_DIR/$VERSION"
mkdir -p "$release_dir"

log "build backend $VERSION ($COMMIT)"
ldflags="-s -w \
  -X patnestServer/internal/version.Version=$VERSION \
  -X patnestServer/internal/version.Commit=$COMMIT \
  -X patnestServer/internal/version.BuildTime=$BUILD_TIME"
( cd "$SRC_DIR" && CGO_ENABLED=0 go build -trimpath -ldflags "$ldflags" -o "$release_dir/patnest-api" ./cmd/api )
( cd "$SRC_DIR" && CGO_ENABLED=0 go build -trimpath -ldflags "$ldflags" -o "$release_dir/patnest-migrate" ./cmd/migrate )
chmod +x "$release_dir/patnest-api" "$release_dir/patnest-migrate"
chown -R patnest:patnest "$release_dir" 2>/dev/null || warn "chown failed; ensure user patnest can execute the new binary"

if [[ "$SKIP_MIGRATE" != "1" ]]; then
  log "run online database migration"
  ( cd "$SHARED_DIR" && "$release_dir/patnest-migrate" -dir "$SRC_DIR/serverdb/migrations" )
else
  warn "migration skipped (SKIP_MIGRATE=1)"
fi

ln -sfn "$release_dir" "$CURRENT_LINK.tmp"
mv -Tf "$CURRENT_LINK.tmp" "$CURRENT_LINK"

log "start slot $target_slot"
systemctl enable "patnest-api@$target_slot" >/dev/null 2>&1 || true
systemctl restart "patnest-api@$target_slot"

healthy=0
for _ in $(seq 1 "$HEALTH_RETRIES"); do
  if [[ "$API_SCHEME" == "https" ]]; then
    if curl -fsSk --max-time 3 "https://127.0.0.1:$target_port/health" >/dev/null 2>&1; then
      healthy=1
      break
    fi
  else
    if curl -fsS --max-time 3 "http://127.0.0.1:$target_port/health" >/dev/null 2>&1; then
      healthy=1
      break
    fi
  fi
  sleep 1
done
if [[ "$healthy" != "1" ]]; then
  journalctl -u "patnest-api@$target_slot" -n 50 --no-pager || true
  die "new instance failed health check; keeping old slot"
fi
log "health check passed"

log "switch nginx traffic to $target_port"
printf 'server 127.0.0.1:%s max_fails=2 fail_timeout=5s;\n' "$target_port" > "$UPSTREAM_FILE"
printf 'proxy_pass %s://patnest_api;\n' "$API_SCHEME" > "$API_PROXY_FILE"
if ! nginx -t -q; then
  if [[ -n "$current_slot" ]]; then
    printf 'server 127.0.0.1:%s max_fails=2 fail_timeout=5s;\n' "$current_port" > "$UPSTREAM_FILE"
  fi
  die "nginx config check failed; original upstream restored"
fi
systemctl reload nginx

log "wait ${DRAIN_SECONDS}s to drain old connections"
sleep "$DRAIN_SECONDS"
if [[ -n "$current_slot" && "$current_slot" != "$target_slot" ]]; then
  systemctl stop "patnest-api@$current_slot" || warn "failed to stop old slot; check manually"
fi

DATABASE_URL="${DATABASE_URL:-}"
if [[ -z "$DATABASE_URL" ]]; then
  set +u
  # shellcheck disable=SC1090
  . "$SHARED_DIR/.env" 2>/dev/null || warn "could not parse .env; skip release record"
  set -u
  if [[ -n "${DB_HOST:-}" && -n "${DB_USER:-}" ]]; then
    DATABASE_URL="postgres://${DB_USER}:${DB_PASSWORD:-}@${DB_HOST}:${DB_PORT:-5432}/${DB_NAME:-patnest_db}?sslmode=disable"
  fi
fi
if [[ -n "$DATABASE_URL" ]] && command -v psql >/dev/null 2>&1; then
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -q -c \
    "INSERT INTO system_release (app, version, commit_sha, slot, note)
     VALUES ('api', '$VERSION', '$COMMIT', '$target_slot', 'blue-green deploy')" \
    || warn "failed to write release record (does not affect release)"
fi

current_target="$(readlink -f "$CURRENT_LINK" || true)"
while IFS= read -r old; do
  [[ -z "$old" ]] && continue
  [[ "$RELEASES_DIR/$old" == "$current_target" ]] && continue
  rm -rf "${RELEASES_DIR:?}/$old"
done < <(ls -1 "$RELEASES_DIR" 2>/dev/null | sort -r | tail -n +$((KEEP_RELEASES + 1)))

trap - EXIT
log "release done: $VERSION"
curl -fsS "http://127.0.0.1:$target_port/api/v1/version" || true
echo
