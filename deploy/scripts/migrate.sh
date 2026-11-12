#!/usr/bin/env bash
# Run or inspect database migrations on demand.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

APP_DIR="${APP_DIR:-/opt/patnest/api}"
SHARED_DIR="$APP_DIR/shared"
BIN="$APP_DIR/current/patnest-migrate"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-$ROOT_DIR/server/patnestServer/serverdb/migrations}"

[[ -x "$BIN" ]] || { echo "$BIN not found; run a backend release first to build the migrate tool" >&2; exit 1; }
[[ -d "$MIGRATIONS_DIR" ]] || { echo "migrations directory does not exist: $MIGRATIONS_DIR" >&2; exit 1; }

cd "$SHARED_DIR"
exec "$BIN" -dir "$MIGRATIONS_DIR" "$@"
