#!/usr/bin/env bash
# One-click builder: frontend dist + Go binaries + container package zip.
# Usage: bash build.sh [--version 1.0.0] [--arch amd64|arm64] [--skip-web] [--skip-go] [--no-zip]
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEB_DIR="$ROOT_DIR/webadmin/patnestServerAdminWeb"
SRC_DIR="$ROOT_DIR/server/patnestServer"
TPL_DIR="$ROOT_DIR/deploy/docker"
RELEASE_DIR="$ROOT_DIR/release"

VERSION=""
ARCH="amd64"
SKIP_WEB=0
SKIP_GO=0
NO_ZIP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    -v|--version) VERSION="${2:-}"; shift 2 ;;
    -a|--arch)    ARCH="${2:-}"; shift 2 ;;
    --skip-web)   SKIP_WEB=1; shift ;;
    --skip-go)    SKIP_GO=1; shift ;;
    --no-zip)     NO_ZIP=1; shift ;;
    -h|--help)
      echo "Usage: bash build.sh [--version 1.0.0] [--arch amd64|arm64] [--skip-web] [--skip-go] [--no-zip]"
      exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

case "$ARCH" in
  amd64|arm64) ;;
  *) echo "Unsupported --arch: $ARCH (amd64|arm64)" >&2; exit 1 ;;
esac

if [[ -z "$VERSION" ]]; then
  VERSION="$(sed -n 's/^  "version": "\([^"]*\)".*/\1/p' "$WEB_DIR/package.json" | head -n1)"
fi
VERSION="${VERSION:-1.0.0}"

COMMIT="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

PKG_NAME="patnest-docker-$VERSION"
if [[ "$ARCH" != "amd64" ]]; then
  PKG_NAME="$PKG_NAME-$ARCH"
fi
PKG_DIR="$RELEASE_DIR/$PKG_NAME"

log() { printf '\033[36m[build]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[build] %s\033[0m\n' "$*" >&2; exit 1; }

command -v go >/dev/null 2>&1 || die "go not found in PATH"

log "version=$VERSION commit=$COMMIT arch=$ARCH"

STASH_DIR=""
if [[ $SKIP_GO -eq 1 && -f "$PKG_DIR/artifacts/patnest-api" ]]; then
  STASH_DIR="$RELEASE_DIR/.artifacts-$VERSION-$ARCH"
  rm -rf "$STASH_DIR"
  mkdir -p "$STASH_DIR"
  cp -a "$PKG_DIR/artifacts/." "$STASH_DIR/"
fi

if [[ -d "$PKG_DIR" ]]; then
  [[ "$PKG_DIR" == "$RELEASE_DIR/"* ]] || die "refuse to clean unexpected path: $PKG_DIR"
  rm -rf "$PKG_DIR"
fi
mkdir -p "$PKG_DIR/artifacts" "$PKG_DIR/web" "$PKG_DIR/db/migrations" "$PKG_DIR/docker/nginx" "$PKG_DIR/nginx" "$PKG_DIR/scripts"

if [[ $SKIP_WEB -eq 0 ]]; then
  log "Building admin panel (vite) ..."
  if [[ -x "$WEB_DIR/node_modules/.bin/vite" ]]; then
    ( cd "$WEB_DIR" && GIT_COMMIT="$COMMIT" APP_VERSION="$VERSION" ./node_modules/.bin/vite build )
  elif command -v npm >/dev/null 2>&1; then
    ( cd "$WEB_DIR" && [[ -d node_modules ]] || npm ci --no-audit --no-fund )
    ( cd "$WEB_DIR" && GIT_COMMIT="$COMMIT" APP_VERSION="$VERSION" npm run build )
  else
    die "neither local vite nor npm found; run npm ci in $WEB_DIR first"
  fi
else
  log "Skip web build, reuse existing dist"
fi
[[ -f "$WEB_DIR/dist/index.html" ]] || die "missing web dist: $WEB_DIR/dist"

if [[ $SKIP_GO -eq 0 ]]; then
  log "Building Go binaries for linux/$ARCH ..."
  LDFLAGS="-s -w \
    -X patnestServer/internal/version.Version=$VERSION \
    -X patnestServer/internal/version.Commit=$COMMIT \
    -X patnestServer/internal/version.BuildTime=$BUILD_TIME"
  (
    cd "$SRC_DIR"
    CGO_ENABLED=0 GOOS=linux GOARCH="$ARCH" go build -trimpath -ldflags "$LDFLAGS" \
      -o "$PKG_DIR/artifacts/patnest-api" ./cmd/api
    CGO_ENABLED=0 GOOS=linux GOARCH="$ARCH" go build -trimpath -ldflags "$LDFLAGS" \
      -o "$PKG_DIR/artifacts/patnest-migrate" ./cmd/migrate
  )
else
  log "Skip Go build, reuse existing artifacts"
  SRC_ART=""
  if [[ -n "$STASH_DIR" && -f "$STASH_DIR/patnest-api" ]]; then
    SRC_ART="$STASH_DIR"
  else
    PREV="$(ls -1d "$RELEASE_DIR"/patnest-docker-* 2>/dev/null | sort -V | tail -n1 || true)"
    [[ -n "$PREV" && -f "$PREV/artifacts/patnest-api" ]] || die "no previous artifacts to reuse"
    SRC_ART="$PREV/artifacts"
  fi
  cp -a "$SRC_ART/." "$PKG_DIR/artifacts/"
  if [[ -n "$STASH_DIR" ]]; then
    rm -rf "$STASH_DIR"
  fi
fi

log "Assembling container package ..."
cp "$TPL_DIR/compose.yaml" "$TPL_DIR/.env.example" "$TPL_DIR/.dockerignore" "$TPL_DIR/README.md" "$PKG_DIR/"
cp "$TPL_DIR/Dockerfile.api" "$TPL_DIR/Dockerfile.web" "$TPL_DIR/Dockerfile.agent" "$PKG_DIR/docker/"
# 入口脚本必须落在包根：Dockerfile.agent 里的 COPY 路径相对构建上下文（部署包根目录），
# 放进 docker/ 子目录会导致 build 时 "agent-entrypoint.sh: not found"。
cp "$TPL_DIR/agent-entrypoint.sh" "$PKG_DIR/"
cp "$TPL_DIR/nginx/default.conf" "$PKG_DIR/docker/nginx/"
cp "$TPL_DIR/nginx/upstream.conf" "$PKG_DIR/nginx/"
cp "$TPL_DIR"/scripts/*.sh "$PKG_DIR/scripts/"
if compgen -G "$TPL_DIR/scripts/*.ps1" >/dev/null; then
  cp "$TPL_DIR"/scripts/*.ps1 "$PKG_DIR/scripts/"
fi
cp -a "$WEB_DIR/dist" "$PKG_DIR/web/dist"
cp "$SRC_DIR/serverdb/init.sql" "$PKG_DIR/db/"
cp "$SRC_DIR"/serverdb/migrations/*.sql "$PKG_DIR/db/migrations/"
chmod +x "$PKG_DIR/scripts/"*.sh "$PKG_DIR/artifacts/"*

log "Writing update manifest ..."
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}
file_size() { wc -c < "$1" | tr -d '[:space:]'; }
manifest_files=""
for rel in artifacts/patnest-api artifacts/patnest-migrate web/dist/index.html web/dist/version.json; do
  if [[ -f "$PKG_DIR/$rel" ]]; then
    [[ -n "$manifest_files" ]] && manifest_files="$manifest_files,"
    manifest_files="$manifest_files
    \"$rel\": {\"size\": $(file_size "$PKG_DIR/$rel"), \"sha256\": \"$(sha256_of "$PKG_DIR/$rel")\"}"
  fi
done
cat > "$PKG_DIR/update-manifest.json" <<EOF
{
  "schema": 1,
  "version": "$VERSION",
  "commit": "$COMMIT",
  "build_time": "$BUILD_TIME",
  "arch": "linux/$ARCH",
  "targets": ["api", "web", "db"],
  "files": {$manifest_files
  }
}
EOF

log "Package directory: $PKG_DIR"

if [[ $NO_ZIP -eq 0 ]]; then
  if command -v zip >/dev/null 2>&1; then
    rm -f "$RELEASE_DIR/$PKG_NAME.zip"
    ( cd "$RELEASE_DIR" && zip -qr "$PKG_NAME.zip" "$PKG_NAME" )
    OUTPUT="$RELEASE_DIR/$PKG_NAME.zip"
  else
    tar -czf "$RELEASE_DIR/$PKG_NAME.tar.gz" -C "$RELEASE_DIR" "$PKG_NAME"
    OUTPUT="$RELEASE_DIR/$PKG_NAME.tar.gz"
  fi
  log "Archive: $OUTPUT ($(du -h "$OUTPUT" | cut -f1))"
fi

cat <<EOF

Done.
  version : $VERSION
  commit  : $COMMIT
  arch    : linux/$ARCH
  package : $PKG_DIR

Server side:
  unzip $PKG_NAME.zip && cd $PKG_NAME
  cp .env.example .env      # fill DB / MinIO / JWT / Redis
  bash scripts/install.sh

Windows server side (PowerShell):
  Expand-Archive $PKG_NAME.zip -DestinationPath .
  cd $PKG_NAME
  Copy-Item .env.example .env
  Set-ExecutionPolicy -Scope Process Bypass
  .\scripts\install.ps1
EOF
