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
healthy=0
for _ in $(seq 1 "${HEALTH_RETRIES:-60}"); do
  if "$ENGINE" exec patnest-nginx wget -qO- http://api-blue:9201/health >/dev/null 2>&1; then
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
"$ENGINE" exec patnest-nginx wget -qO- http://api-blue:9201/health || true
echo

if [[ -f scripts/update-agent.sh ]]; then
  if [[ -f data/updates/agent.pid ]] && kill -0 "$(cat data/updates/agent.pid 2>/dev/null)" 2>/dev/null; then
    echo "[install] 更新代理已在运行"
  else
    nohup bash scripts/update-agent.sh >> data/updates/agent.log 2>&1 &
    echo "[install] 更新代理已启动（PID $!）"
  fi
fi

echo "[install] 完成，访问 http://<服务器IP>:${PUBLIC_PORT}/"
