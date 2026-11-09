#!/usr/bin/env bash
# 公共函数：识别容器引擎与 compose 命令
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${PATNEST_AGENT_CONTAINER:-0}" == "1" ]]; then
  # 容器化更新代理：镜像里只有 docker CLI + 挂载的 docker.sock，
  # release-api.sh / release-web.sh 会 source 本文件并继承 COMPOSE / ENGINE。
  if ! command -v docker >/dev/null 2>&1; then
    echo "容器内缺少 docker CLI，无法执行发布" >&2
    exit 1
  fi
  # 容器内的 /pkg 在宿主机 daemon 眼里不存在。entrypoint 已解析出宿主机真实路径
  # 并软链到 /pkg，这里把 compose 的项目目录钉到那条路径上，build context 与
  # 相对 bind mount 才会被解析成 daemon 能访问的绝对路径。
  _compose_opts=()
  if [[ -n "${PATNEST_COMPOSE_PROJECT_DIR:-}" ]]; then
    _compose_opts+=(--project-directory "$PATNEST_COMPOSE_PROJECT_DIR")
  fi
  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose "${_compose_opts[@]}")
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose "${_compose_opts[@]}")
  else
    echo "容器内缺少 docker compose 插件" >&2
    exit 1
  fi
  ENGINE=docker
elif command -v podman-compose >/dev/null 2>&1; then
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
