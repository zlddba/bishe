#!/bin/sh
# Start nginx in the foreground, then reload it whenever the TLS certificate
# files change. The admin page writes new PEM files into the mounted TLS
# directory; nginx caches certificates at startup, so a reload is what makes a
# freshly uploaded certificate effective without restarting the container.

set -e

TLS_DIR="${TLS_DIR:-/etc/nginx/patnest/tls}"
CERT="$TLS_DIR/fullchain.pem"
KEY="$TLS_DIR/privkey.pem"
INTERVAL="${CERT_CHECK_SECONDS:-5}"

fingerprint() {
    cat "$CERT" "$KEY" 2>/dev/null | md5sum | cut -d' ' -f1
}

nginx -g 'daemon off;' &
NGINX_PID=$!

# PID 1 is this shell, so forward stop signals to nginx for a graceful shutdown
# instead of letting the runtime kill the container after a timeout.
shutdown() {
    nginx -s quit >/dev/null 2>&1 || true
    wait "$NGINX_PID" 2>/dev/null || true
    exit 0
}
trap shutdown TERM INT

(
    last="$(fingerprint)"
    while kill -0 "$NGINX_PID" 2>/dev/null; do
        sleep "$INTERVAL"
        cur="$(fingerprint)"
        if [ -z "$cur" ]; then
            continue
        fi
        if [ "$cur" = "$last" ]; then
            continue
        fi
        if nginx -t -q 2>/dev/null; then
            if nginx -s reload >/dev/null 2>&1; then
                echo "[nginx] TLS certificate changed, reloaded"
            fi
        else
            echo "[nginx] TLS certificate changed but the config test failed; keeping the old one" >&2
        fi
        last="$cur"
    done
) &

wait "$NGINX_PID"
