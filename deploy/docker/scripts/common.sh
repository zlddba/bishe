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
  _proj="${PATNEST_COMPOSE_PROJECT_DIR:-}"
  if [[ -n "$_proj" ]]; then
    # Windows：inspect 给的是盘符路径 D:/dockers/...，在容器里不是绝对路径，
    # compose 会拼成 /pkg/D:/... 再发给 daemon，bind 源冒号过多直接报
    # "too many colons"。统一换成 Docker Desktop（WSL2）的绝对形式。
    # 老版本 entrypoint 只会给盘符形式建软链，这里顺手补一条，保证 compose
    # 客户端能读到 project-directory 下的 compose.yaml。
    if [[ "$_proj" =~ ^([A-Za-z]):[\\/](.*)$ ]]; then
      _drive="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')"
      _rest="${BASH_REMATCH[2]//\\//}"
      _proj="/run/desktop/mnt/host/${_drive}/${_rest}"
      if [[ ! -f "$_proj/compose.yaml" ]]; then
        mkdir -p "$(dirname "$_proj")" 2>/dev/null || true
        ln -sfn "$ROOT_DIR" "$_proj" 2>/dev/null || true
      fi
      PATNEST_COMPOSE_PROJECT_DIR="$_proj"
    fi
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

# 探活用的 nginx 容器名。compose 里固定 container_name，允许环境变量覆盖。
NGINX_CONTAINER="${NGINX_CONTAINER:-patnest-nginx}"

# 从 nginx 容器内部探测上游健康状态。
#
# 不能写死某一条命令：patnest-web 镜像基于 Debian（只有 curl，没有 wget），
# 早期 alpine 版本反过来（只有 wget）。之前写死 `wget -qO-`，在 Debian 镜像里
# 每次 exec 都以 127「command not found」瞬间失败，30 次重试空转 30 秒后被
# 误判成「新槽位健康检查失败」，蓝绿发布永远切不过去（rollback.sh 同理会卡死）。
# 所以在容器内探测一次能力并缓存，后续重试不再重复 exec。
_NGINX_PROBE_TOOL=""

nginx_probe_tool() {
  if [[ -z "$_NGINX_PROBE_TOOL" ]]; then
    if "$ENGINE" exec "$NGINX_CONTAINER" sh -c 'command -v curl >/dev/null 2>&1' 2>/dev/null; then
      _NGINX_PROBE_TOOL=curl
    elif "$ENGINE" exec "$NGINX_CONTAINER" sh -c 'command -v wget >/dev/null 2>&1' 2>/dev/null; then
      _NGINX_PROBE_TOOL=wget
    else
      _NGINX_PROBE_TOOL=none
    fi
  fi
  printf '%s\n' "$_NGINX_PROBE_TOOL"
}

# 用 nginx 容器内可用的工具请求 url；HTTP 2xx 才算成功。
nginx_probe() {
  local url="$1"
  case "$(nginx_probe_tool)" in
    curl) "$ENGINE" exec "$NGINX_CONTAINER" curl -fsS --max-time 5 "$url" ;;
    wget) "$ENGINE" exec "$NGINX_CONTAINER" wget -q -T 5 -O - "$url" ;;
    *)    return 127 ;;
  esac
}

# 探活工具缺失时直接给出明确结论，不必空转 30 次重试。
require_probe_tool() {
  local tool
  tool="$(nginx_probe_tool)"
  if [[ "$tool" == "none" ]]; then
    echo "${1:-[release]} 容器 $NGINX_CONTAINER 里既没有 curl 也没有 wget，无法做健康检查" >&2
    exit 1
  fi
}

# 只读取生效的 server 指令；注释里同时出现 blue/green，不能直接 grep 第一个匹配。
current_slot() {
  local slot
  slot="$(grep -E '^[[:space:]]*server[[:space:]]+api-(blue|green):' nginx/upstream.conf 2>/dev/null \
    | head -n1 | grep -oE 'api-(blue|green)' | sed 's/api-//' || true)"
  printf '%s\n' "${slot:-blue}"
}
