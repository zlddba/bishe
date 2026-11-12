#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"

"${COMPOSE[@]}" --profile tools run --rm migrate -dir /app/migrations "$@"
