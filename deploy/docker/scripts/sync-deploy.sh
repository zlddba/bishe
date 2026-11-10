#!/usr/bin/env bash
# 把「部署定义」从解压后的更新包同步到运行目录。
#
# 为什么需要单独一步：内置的更新流程（update-agent.sh）只替换**制品**
# —— artifacts/patnest-api、db/migrations、web/dist。compose.yaml、Dockerfile、
# nginx 配置这些「部署定义」它不会碰。所以凡是改过端口映射、挂载、镜像构建
# 内容的版本，必须先用本脚本同步文件，再重建容器，否则改动不会生效
# （更糟的情况：新版 nginx 配置引用了尚未挂载的目录，容器直接起不来）。
#
# 用法：
#   bash scripts/sync-deploy.sh /path/to/解压后的包目录
#   bash scripts/sync-deploy.sh /path/to/pkg --dry-run
#
# 注意：不会覆盖 .env（里面是真实密码）和 nginx/upstream.conf（记录着当前
# 蓝绿活跃槽位，覆盖会让流量打到未启动的槽）。
set -euo pipefail

SRC="${1:?用法: sync-deploy.sh <解压后的包目录> [--dry-run]}"
DRY_RUN=0
[[ "${2:-}" == "--dry-run" ]] && DRY_RUN=1

[[ -d "$SRC" ]] || { echo "[sync] 目录不存在: $SRC" >&2; exit 1; }
[[ -f "$SRC/compose.yaml" ]] || { echo "[sync] 不是部署包根目录（缺 compose.yaml）: $SRC" >&2; exit 1; }

DST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log() { printf '\033[36m[sync]\033[0m %s\n' "$*"; }

copy_file() {
    local rel="$1"
    if [[ ! -f "$SRC/$rel" ]]; then
        log "跳过（包内没有）: $rel"
        return 0
    fi
    if [[ $DRY_RUN -eq 1 ]]; then
        log "[dry-run] 将覆盖: $rel"
        return 0
    fi
    mkdir -p "$DST/$(dirname "$rel")"
    cp -a "$SRC/$rel" "$DST/$rel"
    log "同步 $rel"
}

# 部署定义（顺序无关，都是覆盖）
for rel in \
    compose.yaml \
    .dockerignore \
    agent-entrypoint.sh \
    docker/Dockerfile.api \
    docker/Dockerfile.web \
    docker/Dockerfile.agent \
    docker/nginx/default.conf \
    docker/nginx/locations.conf \
    docker/nginx/tls-watch.sh
do
    copy_file "$rel"
done

# 证书目录：保留服务器上已有的证书，只补 https.conf 占位文件。
# 新版 default.conf 会 include 它，文件缺失 nginx 直接起不来。
if [[ ! -f "$DST/nginx/tls/https.conf" ]]; then
    if [[ $DRY_RUN -eq 1 ]]; then
        log "[dry-run] 将创建 nginx/tls/https.conf 占位"
    else
        mkdir -p "$DST/nginx/tls"
        cp "$SRC/nginx/tls/https.conf" "$DST/nginx/tls/https.conf"
        log "创建 nginx/tls/https.conf 占位"
    fi
else
    log "保留已有的 nginx/tls/https.conf"
fi

log "未覆盖 .env（含真实密码）与 nginx/upstream.conf（当前活跃槽位）"
log "下一步：docker compose --profile app up -d --build"
