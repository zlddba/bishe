#!/usr/bin/env bash
# 公共函数：识别容器引擎与 compose 命令
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if command -v podman-compose >/dev/null 2>&1; then
  COMPOSE=(podman-compose)
  ENGINE=podman
elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
  COMPOSE=(podman compose)
  ENGINE=podman
elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
  ENGINE=docker
else
  echo "未找到可用的 compose 命令（podman-compose / podman compose / docker compose）" >&2
  exit 1
fi

env_value() {
  sed -n "s/^$1=//p" .env 2>/dev/null | head -n1 | tr -d '\r'
}

env_version() {
  env_value APP_VERSION
}

# 只读取生效的 server 指令；注释里同时出现 blue/green，不能直接 grep 第一个匹配。
current_slot() {
  local slot
  slot="$(grep -E '^[[:space:]]*server[[:space:]]+api-(blue|green):' nginx/upstream.conf 2>/dev/null \
    | head -n1 | grep -oE 'api-(blue|green)' | sed 's/api-//' || true)"
  printf '%s\n' "${slot:-blue}"
}
