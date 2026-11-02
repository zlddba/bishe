#!/usr/bin/env bash
# 单独执行/查看迁移：./scripts/migrate.sh -status
set -euo pipefail
source "$(dirname "$0")/common.sh"

# compose run 会覆盖服务里的 command，所以这里显式补回 -dir
"${COMPOSE[@]}" --profile tools run --rm migrate -dir /app/migrations "$@"
