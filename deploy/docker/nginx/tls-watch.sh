#!/bin/sh
# 证书热加载守护。
#
# 后端只写文件（证书 + https.conf.new + reload.request），真正的
# nginx -t / reload 在这里做。原因是让 API 直接操作 nginx 容器需要挂
# docker.sock，等于把宿主机 root 交给 Web 进程，为一张证书不值得。
#
# 关键行为：新配置先落成 .new，本脚本校验通过才替换；校验失败自动回滚，
# 站点保持原样可用 —— 后台填错一行配置不会把线上搞挂。
#
# ⚠️ 本文件被复制到 /docker-entrypoint.d/ 下，官方 entrypoint 是 **source**
# 执行的。所以这里不能写 `exit`（会直接结束整个入口，nginx 起不来），
# 变量和函数也统一加前缀，避免污染 entrypoint 的命名空间。

patnest_tls_apply_once() {
    tls_dir="$1"
    log="$2"
    req="$tls_dir/reload.request"
    [ -f "$req" ] || return 0

    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    rm -f "$req"

    new="$tls_dir/https.conf.new"
    if [ -f "$new" ]; then
        [ -f "$tls_dir/https.conf" ] && cp "$tls_dir/https.conf" "$tls_dir/https.conf.bak" 2>/dev/null
        mv "$new" "$tls_dir/https.conf"
    fi

    if nginx -t >>"$log" 2>&1; then
        if nginx -s reload >>"$log" 2>&1; then
            printf '{"ok":true,"at":"%s"}' "$ts" > "$tls_dir/reload.status"
            return 0
        fi
        reason="reload 失败，详见容器日志"
    else
        # 只取最后几行：整份 nginx -t 输出太长，塞进状态里没法看
        reason=$(tail -n 3 "$log" | tr '\n' ' ' | sed 's/"/\\"/g')
        if [ -f "$tls_dir/https.conf.bak" ]; then
            cp "$tls_dir/https.conf.bak" "$tls_dir/https.conf"
            nginx -s reload >>"$log" 2>&1
        fi
    fi
    printf '{"ok":false,"at":"%s","message":"%s"}' "$ts" "$reason" > "$tls_dir/reload.status"
}

# 后台跑，不能阻塞 entrypoint 后续的 nginx 启动
(
    patnest_tls_dir="${TLS_DIR:-/etc/nginx/patnest/tls}"
    while true; do
        patnest_tls_apply_once "$patnest_tls_dir" /tmp/patnest-tls-watch.log
        sleep 3
    done
) &
