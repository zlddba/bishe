#!/usr/bin/env bash
# Sync deploy definitions (compose.yaml, Dockerfiles, nginx config) from an
# unpacked update package into the running directory. The built-in update
# flow only replaces artifacts, so run this before recreating containers
# whenever ports, mounts or image builds changed. .env, nginx/upstream.conf
# (active slot) and nginx/tls (certificates) are never overwritten.

#   bash scripts/sync-deploy.sh /path/to/pkg --dry-run
set -euo pipefail

SRC="${1:?usage: sync-deploy.sh <unpacked-package> [--dry-run]}"
DRY_RUN=0
[[ "${2:-}" == "--dry-run" ]] && DRY_RUN=1

[[ -d "$SRC" ]] || { echo "[sync] directory does not exist: $SRC" >&2; exit 1; }
[[ -f "$SRC/compose.yaml" ]] || { echo "[sync] not a package root (missing compose.yaml): $SRC" >&2; exit 1; }

DST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log() { printf '\033[36m[sync]\033[0m %s\n' "$*"; }

copy_file() {
    local rel="$1"
    if [[ ! -f "$SRC/$rel" ]]; then
        log "skip (not in package): $rel"
        return 0
    fi
    if [[ $DRY_RUN -eq 1 ]]; then
        log "[dry-run] would overwrite: $rel"
        return 0
    fi
    mkdir -p "$DST/$(dirname "$rel")"
    cp -a "$SRC/$rel" "$DST/$rel"
    log "sync $rel"
}

for rel in \
    compose.yaml \
    .dockerignore \
    agent-entrypoint.sh \
    docker/Dockerfile.api \
    docker/Dockerfile.web \
    docker/Dockerfile.agent \
    docker/nginx/default.conf \
    docker/nginx/locations.conf \
    docker/nginx/api_proxy.conf
do
    copy_file "$rel"
done


if [[ ! -f "$DST/nginx/api_proxy.conf" ]]; then
    if [[ $DRY_RUN -eq 1 ]]; then
        log "[dry-run] would create nginx/api_proxy.conf"
    else
        mkdir -p "$DST/nginx"
        cp "$SRC/nginx/api_proxy.conf" "$DST/nginx/api_proxy.conf"
        log "create nginx/api_proxy.conf"
    fi
else
    log "keep existing nginx/api_proxy.conf"
fi

log "kept .env (real passwords), nginx/upstream.conf (active slot), nginx/tls (certs)"
log "Next: docker compose --profile app up -d --build"
