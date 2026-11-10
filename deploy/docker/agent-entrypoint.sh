#!/usr/bin/env bash
# 更新代理容器入口。
#
# 这里解决容器化代理最麻烦的一件事：容器里的 docker CLI 把 build context、
# bind mount 源路径「原样」发给宿主机 daemon，daemon 只认宿主机上的真实路径。
# 容器内的 /pkg 在 daemon 眼里并不存在，所以不能直接 `docker compose build`。
#
# 做法：挂载时把部署包挂到固定的 /pkg，进容器后用 docker inspect 反查这个挂载
# 在宿主机（daemon）侧的真实路径，把它作为 compose 的 --project-directory；
# 同时在容器内给该路径建一个指向 /pkg 的软链，让 compose 客户端读 .env /
# compose.yaml 时也能打开同一份文件。这样 Linux 和 Windows（Docker Desktop）
# 都不再要求「宿主机与容器同路径挂载」。
set -euo pipefail

PKG=/pkg

fail() {
  echo "[update-agent] $*" >&2
  exit 1
}

[[ -d "$PKG" ]] ||
  fail "容器内没有 $PKG：部署包没有挂进来。compose 需要 '- \${PACKAGE_HOST_PATH}:${PKG}'。"
[[ -f "$PKG/compose.yaml" ]] ||
  fail "$PKG 下找不到 compose.yaml，说明挂载的不是部署包根目录。"
[[ -f "$PKG/scripts/update-agent.sh" ]] ||
  fail "$PKG 下找不到 scripts/update-agent.sh，部署包不完整。"

DOCKER_SOCK="${DOCKER_SOCKET:-/var/run/docker.sock}"
[[ -S "$DOCKER_SOCK" ]] ||
  fail "未挂载 docker socket（$DOCKER_SOCK），容器内无法调用宿主机 daemon。若 socket 路径不同，请在 .env 设置 DOCKER_SOCKET。"

command -v docker >/dev/null 2>&1 || fail "镜像内缺少 docker CLI。"
if ! docker compose version >/dev/null 2>&1; then
  # Alpine 的 docker-cli-compose 把 plugin 装在 /usr/libexec 下，某些 docker CLI 版本
  # 只在 /usr/local/lib 下找；这里做一次兜底软链。
  for candidate in \
    /usr/libexec/docker/cli-plugins/docker-compose \
    /usr/lib/docker/cli-plugins/docker-compose \
    /usr/bin/docker-compose; do
    if [[ -x "$candidate" ]]; then
      mkdir -p /usr/local/lib/docker/cli-plugins
      ln -sf "$candidate" /usr/local/lib/docker/cli-plugins/docker-compose
      break
    fi
  done
fi
docker compose version >/dev/null 2>&1 || fail "镜像内 docker compose 插件不可用。"

# ---- 解析部署包在宿主机的真实路径 ------------------------------------------
# PACKAGE_HOST_ROOT 可由 .env 显式指定（inspect 拿不到时的逃生口）；
# 否则查自己这个容器的 /pkg 挂载源。容器的 hostname 默认就是容器 ID。
#
# 注意：Windows（Docker Desktop）上 inspect 给的是盘符路径 D:/dockers/patnest-docker。
# 这个字符串在 Linux 容器里"不是绝对路径"，compose 会按当前目录拼成
# /pkg/D:/dockers/... 再发给 daemon，bind 源里多个冒号，直接报
# "too many colons"。Docker Desktop（WSL2）里宿主机盘符对应的绝对路径是
# /run/desktop/mnt/host/<盘符小写>/<余下路径>，两边都认，所以统一换成这个形式。
to_daemon_path() {
  local p="$1"
  # 不是盘符路径（Linux 部署）就原样返回
  if [[ ! "$p" =~ ^([A-Za-z]):[\\/](.*)$ ]]; then
    printf '%s' "$p"
    return 0
  fi
  local drive rest
  drive="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')"
  rest="${BASH_REMATCH[2]//\\//}"
  printf '/run/desktop/mnt/host/%s/%s' "$drive" "$rest"
}

HOST_ROOT="${PACKAGE_HOST_ROOT:-}"
if [[ -z "$HOST_ROOT" ]]; then
  self_id="$(cat /etc/hostname 2>/dev/null || hostname)"
  HOST_ROOT="$(docker inspect \
      --format '{{range .Mounts}}{{if eq .Destination "/pkg"}}{{.Source}}{{end}}{{end}}' \
      "$self_id" 2>/dev/null || true)"
fi
[[ -n "$HOST_ROOT" ]] ||
  fail "无法解析部署包在宿主机上的路径（docker inspect 失败）。可在 .env 里设置 PACKAGE_HOST_ROOT 指定。"

HOST_ROOT="$(to_daemon_path "$HOST_ROOT")"

# 让容器内也能通过 HOST_ROOT 打开同一份文件（compose 客户端按 project-directory 拼路径，
# build context 也要由客户端读出来再打包传给 daemon）。
if [[ "$HOST_ROOT" != "$PKG" && ! -f "$HOST_ROOT/compose.yaml" ]]; then
  mkdir -p "$(dirname "$HOST_ROOT")" 2>/dev/null || true
  ln -sfn "$PKG" "$HOST_ROOT" 2>/dev/null ||
    fail "无法建立软链 $HOST_ROOT -> $PKG（权限不足？）。可在 .env 设置 PACKAGE_HOST_ROOT=$PKG 跳过。"
fi
[[ -f "$HOST_ROOT/compose.yaml" ]] ||
  fail "宿主机路径 $HOST_ROOT 下看不到 compose.yaml，挂载源与部署包根目录不一致。"

export PACKAGE_ROOT="$PKG"
export PATNEST_AGENT_CONTAINER=1
# compose 里 ${PACKAGE_HOST_PATH} 也走这条 daemon 视角的路径：容器内 /pkg 换成
# 宿主机上的实际目录后，重建 update-agent 自身时挂载源依然合法。
export PACKAGE_HOST_PATH="$HOST_ROOT"
# common.sh 用它给 docker compose 加 --project-directory，确保 build context 与
# 相对 bind mount 都被解析成 daemon 能访问的绝对路径。
export PATNEST_COMPOSE_PROJECT_DIR="$HOST_ROOT"

echo "[update-agent] container mode"
echo "[update-agent]   package (container): $PKG"
echo "[update-agent]   package (daemon):    $HOST_ROOT"

# 工作目录固定在 /pkg：脚本里的 cp / mv / 写 upstream.conf 都走容器内绝对路径，
# 只有 compose 的 --project-directory 用 daemon 视角的 HOST_ROOT。
cd "$PKG"
exec bash "$PKG/scripts/update-agent.sh"
