#!/usr/bin/env bash
# 首次部署：打包镜像 -> 执行迁移 -> 启动 redis/nginx/api-blue
set -euo pipefail
source "$(dirname "$0")/common.sh"

[[ -f .env ]] || { echo "请先复制 .env.example 为 .env 并填写" >&2; exit 1; }
[[ -f artifacts/patnest-api ]] || { echo "缺少 artifacts/patnest-api" >&2; exit 1; }
[[ -f web/dist/index.html ]] || { echo "缺少 web/dist" >&2; exit 1; }

db_password="$(env_value DB_PASSWORD)"
[[ -n "$db_password" && "$db_password" != 请* ]] || {
  echo "[install] .env 里的 DB_PASSWORD 为空或仍是占位符" >&2
  exit 1
}
jwt_secret="$(env_value JWT_SECRET)"
if [[ -z "$jwt_secret" || "$jwt_secret" == 请* || ${#jwt_secret} -lt 32 ]]; then
  echo "[install] 警告：JWT_SECRET 为空、仍是占位符或短于 32 位，请在 .env 中换成随机串" >&2
fi

VERSION="${APP_VERSION:-$(env_version)}"
VERSION="${VERSION:-1.0.0}"
PUBLIC_PORT="${PUBLIC_PORT:-$(env_value PUBLIC_PORT)}"
PUBLIC_PORT="${PUBLIC_PORT:-9200}"

mkdir -p data/redis
mkdir -p data/updates/{tmp,inbox,manual,queue,processing,failed,status,staging,logs,backups}
chmod -R a+rwX data/updates 2>/dev/null || true

echo "[install] 打包镜像 patnest-api:$VERSION / patnest-web:$VERSION"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app build --pull api-blue nginx

echo "[install] 初始化更新目录"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile tools run --rm updates-init

echo "[install] 执行数据库迁移"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile tools run --rm migrate

echo "[install] 启动 redis / nginx / api-blue"
APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile app up -d redis nginx api-blue

echo "[install] 等待 api-blue 健康检查"
require_probe_tool "[install]"
healthy=0
for _ in $(seq 1 "${HEALTH_RETRIES:-60}"); do
  if nginx_probe http://api-blue:9201/health >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
if [[ "$healthy" != "1" ]]; then
  "$ENGINE" logs --tail 80 patnest-api-blue || true
  echo "[install] api-blue 健康检查失败，请检查 .env 里的数据库配置" >&2
  exit 1
fi
nginx_probe http://api-blue:9201/health || true
echo

# ---- 更新代理 ----------------------------------------------------------
# AGENT_MODE:
#   container（默认）  容器化，compose 的 agent profile。挂 docker.sock，不碰宿主机进程
#   host              宿主机 nohup 脚本（仅在明确要求时使用）
#   none               不启动（系统更新页会显示离线，仅做文件上传与扫描）
# 两种模式互斥：切换时先停掉另一侧，避免两个消费者抢同一个 queue。
# 默认值刻意选 container：宿主机进程会随 shell/重启消失，容器有 restart 策略兜底。

set_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" .env 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}

stop_host_agent() {
  local pid_file="data/updates/agent.pid" old=""
  if [[ -f "$pid_file" ]]; then
    old="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$old" && -r "/proc/$old/cmdline" ]] &&
       tr '\0' ' ' < "/proc/$old/cmdline" | grep -q "update-agent"; then
      kill "$old" 2>/dev/null || true
      sleep 1
    fi
    rm -f "$pid_file"
  fi
  rmdir data/updates/agent.lock 2>/dev/null || true
}

stop_container_agent() {
  "${COMPOSE[@]}" --profile agent stop update-agent >/dev/null 2>&1 || true
}

start_host_agent() {
  if [[ ! -f scripts/update-agent.sh ]]; then
    echo "[install] 缺少 scripts/update-agent.sh，跳过更新代理" >&2
    return 0
  fi
  if [[ -f data/updates/agent.pid ]] &&
     kill -0 "$(cat data/updates/agent.pid 2>/dev/null)" 2>/dev/null; then
    echo "[install] 宿主机更新代理已在运行"
    return 0
  fi
  nohup bash scripts/update-agent.sh >> data/updates/agent.log 2>&1 &
  echo "[install] 宿主机更新代理已启动（PID $!）"
}

start_container_agent() {
  local pkg_path
  pkg_path="$(pwd -P)"
  if [[ "$pkg_path" == *" "* ]]; then
    echo "[install] 部署路径含空格（$pkg_path），compose 的 bind mount 解析不可靠，请换目录" >&2
    return 1
  fi
  if [[ "$ENGINE" != "docker" ]]; then
    echo "[install] 警告：容器化代理内部用 docker CLI，当前宿主机引擎是 $ENGINE；需要兼容的 docker.sock" >&2
  fi

  # 容器内固定挂在 /pkg，宿主机路径写进 .env 供 compose 做 bind mount；
  # 代理启动后会自己 docker inspect 反查 daemon 侧真实路径，不再要求同路径挂载。
  set_env PACKAGE_HOST_PATH "$pkg_path"

  echo "[install] 先停止可能存在的宿主机更新代理"
  stop_host_agent

  echo "[install] 打包更新代理镜像 patnest-update-agent:$VERSION"
  APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile agent build --pull update-agent || return 1

  echo "[install] 启动容器化更新代理"
  APP_VERSION="$VERSION" "${COMPOSE[@]}" --profile agent up -d update-agent || return 1

  # 容器起得来不代表代理活着（挂载缺失会在 entrypoint 直接退出），这里做一次存活确认。
  sleep 2
  if ! "$ENGINE" ps --format '{{.Names}}\t{{.Status}}' | grep -q "^patnest-update-agent"; then
    echo "[install] 容器未保持运行，最近日志：" >&2
    "$ENGINE" logs --tail 40 patnest-update-agent >&2 || true
    return 1
  fi
  echo "[install] 容器化更新代理已启动（$ENGINE logs -f patnest-update-agent）"
}

AGENT_MODE="${AGENT_MODE:-$(env_value AGENT_MODE)}"
AGENT_MODE="${AGENT_MODE:-container}"

case "$AGENT_MODE" in
  container|auto)
    if ! start_container_agent; then
      stop_container_agent
      echo "[install] 容器化更新代理启动失败。请按上面的日志修正后重跑：" >&2
      echo "[install]   bash scripts/install.sh" >&2
      echo "[install] 确实无法挂 docker.sock 时，可显式使用 AGENT_MODE=host（会回到宿主机进程）。" >&2
      exit 1
    fi
    ;;
  host)
    stop_container_agent
    start_host_agent
    ;;
  none)
    stop_container_agent
    stop_host_agent
    echo "[install] AGENT_MODE=none：未启动更新代理，系统更新页将显示离线"
    ;;
  *)
    echo "[install] 未知 AGENT_MODE='$AGENT_MODE'（container/host/none）" >&2
    exit 1
    ;;
esac

echo "[install] 完成，访问 http://<服务器IP>:${PUBLIC_PORT}/"
