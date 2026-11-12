#!/usr/bin/env bash
# Admin panel release: stage a new directory, switch the current symlink
# atomically, then reload nginx.

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

[[ -f "$DIST_DIR/index.html" ]] || die "cannot find build output: $DIST_DIR/index.html (run npm run build first)"
[[ -f "$DIST_DIR/version.json" ]] || die "build output lacks version.json"

build_id="$(sed -n 's/.*"build_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$DIST_DIR/version.json" | head -n1)"
[[ -n "$build_id" ]] || build_id="$(date +%Y%m%d%H%M%S)"

mkdir -p "$RELEASES_DIR"
release_dir="$RELEASES_DIR/$(date +%Y%m%d%H%M%S)"
mkdir -p "$release_dir"
cp -a "$DIST_DIR"/. "$release_dir"/
log "build $build_id ready: $release_dir"

ln -sfn "$release_dir" "$CURRENT_LINK.tmp"
mv -Tf "$CURRENT_LINK.tmp" "$CURRENT_LINK"

if ! nginx -t -q; then
  die "nginx config check failed; current points to new dir, fix and reload"
fi
systemctl reload nginx
log "current -> $release_dir"

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
    || warn "failed to write release record (does not affect release)"
fi

log "admin panel release done: $build_id"
