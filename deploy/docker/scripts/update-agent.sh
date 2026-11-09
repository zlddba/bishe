#!/usr/bin/env bash
# Host-side update agent (Linux). It only consumes data/updates/queue and calls
# the existing release scripts, so the API container never needs docker access.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

UPDATES="$ROOT_DIR/data/updates"
QUEUE="$UPDATES/queue"
PROCESSING="$UPDATES/processing"
FAILED="$UPDATES/failed"
STATUS_DIR="$UPDATES/status"
LOGS="$UPDATES/logs"
BACKUPS="$UPDATES/backups"
PID_FILE="$UPDATES/agent.pid"
HEARTBEAT="$UPDATES/agent.heartbeat"
POLL_SECONDS="${POLL_SECONDS:-3}"

mkdir -p "$UPDATES" "$QUEUE" "$PROCESSING" "$FAILED" "$STATUS_DIR" "$LOGS" "$BACKUPS"

# 容器化运行时：卷里残留的 agent.pid / agent.lock 属于上一代容器进程。
# 容器内 PID 会复用（常为 1），直接沿用会误判「已在运行」而静默退出，
# 表现为「容器 Up 但心跳不更新」。由 entrypoint 设置 PATNEST_AGENT_CONTAINER=1。
if [[ "${PATNEST_AGENT_CONTAINER:-0}" == "1" ]]; then
  rm -f "$PID_FILE"
  rm -rf "$UPDATES/agent.lock"
fi

# mkdir is atomic and works without flock on minimal installations.
if [[ -d "$UPDATES/agent.lock" ]]; then
  old_pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$old_pid" ]] && kill -0 "$old_pid" 2>/dev/null; then
    echo "[update-agent] already running (pid=$old_pid)" >&2
    exit 0
  fi
  rmdir "$UPDATES/agent.lock" 2>/dev/null || true
fi
if ! mkdir "$UPDATES/agent.lock" 2>/dev/null; then
  echo "[update-agent] already running" >&2
  exit 0
fi
trap 'rmdir "$UPDATES/agent.lock" 2>/dev/null || true; rm -f "$PID_FILE"' EXIT

echo "$$" > "$PID_FILE"

log() {
  local id="$1"; shift
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOGS/$id.log"
}

touch_heartbeat() {
  date -u '+%Y-%m-%dT%H:%M:%SZ' > "$HEARTBEAT"
}

job_get() {
  local file="$1" key="$2"
  sed -n "s/^${key}=//p" "$file" | head -n1
}

write_status() {
  local id="$1" state="$2" step="$3" message="$4" version="$5" targets="$6" source="$7"
  local created="$8" started="$9" finished="${10}"
  message="${message//$'\r'/ }"
  message="${message//$'\n'/ }"
  local tmp="$STATUS_DIR/$id.status.tmp"
  {
    printf 'id=%s\n' "$id"
    printf 'state=%s\n' "$state"
    printf 'step=%s\n' "$step"
    printf 'message=%s\n' "$message"
    printf 'version=%s\n' "$version"
    printf 'targets=%s\n' "$targets"
    printf 'source=%s\n' "$source"
    printf 'created_at=%s\n' "$created"
    printf 'started_at=%s\n' "$started"
    printf 'finished_at=%s\n' "$finished"
  } > "$tmp"
  mv -f "$tmp" "$STATUS_DIR/$id.status"
}

backup_current() {
  local id="$1" dest="$BACKUPS/$1"
  mkdir -p "$dest/artifacts" "$dest/db/migrations" "$dest/web"
  local name
  for name in patnest-api patnest-migrate; do
    [[ -f "$ROOT_DIR/artifacts/$name" ]] && cp -a "$ROOT_DIR/artifacts/$name" "$dest/artifacts/$name"
  done
  [[ -d "$ROOT_DIR/web/dist" ]] && cp -a "$ROOT_DIR/web/dist" "$dest/web/dist"
  if [[ -d "$ROOT_DIR/db/migrations" ]]; then
    cp -a "$ROOT_DIR/db/migrations/." "$dest/db/migrations/" 2>/dev/null || true
  fi
  printf '%s\n' "$dest"
}

restore_backup() {
  local dest="$1"
  [[ -d "$dest" ]] || return 0
  local name
  for name in patnest-api patnest-migrate; do
    [[ -f "$dest/artifacts/$name" ]] && cp -a "$dest/artifacts/$name" "$ROOT_DIR/artifacts/$name"
  done
  if [[ -d "$dest/web/dist" ]]; then
    rm -rf "$ROOT_DIR/web/dist"
    cp -a "$dest/web/dist" "$ROOT_DIR/web/dist"
  fi
  if [[ -d "$dest/db/migrations" ]]; then
    cp -a "$dest/db/migrations/." "$ROOT_DIR/db/migrations/" 2>/dev/null || true
  fi
}

update_env_version() {
  local version="$1"
  [[ -f "$ROOT_DIR/.env" ]] || return 0
  if grep -q '^APP_VERSION=' "$ROOT_DIR/.env"; then
    sed -i "s/^APP_VERSION=.*/APP_VERSION=$version/" "$ROOT_DIR/.env"
  else
    printf 'APP_VERSION=%s\n' "$version" >> "$ROOT_DIR/.env"
  fi
}

run_release() {
  local id="$1" script="$2" version="$3"
  log "$id" "run $script $version"
  touch_heartbeat
  if ! bash "$ROOT_DIR/scripts/$script" "$version" >> "$LOGS/$id.log" 2>&1; then
    log "$id" "$script failed"
    return 1
  fi
  log "$id" "$script ok"
}

recover_interrupted() {
  local file id job started backup
  for file in "$PROCESSING"/*.job; do
    [[ -e "$file" ]] || continue
    id="$(basename "$file" .job)"
    [[ "$id" =~ ^[0-9]{8}-[0-9]{6}-[0-9a-f]{8}$ ]] || continue
    job="$file"
    state="$(sed -n 's/^state=//p' "$STATUS_DIR/$id.status" 2>/dev/null | head -n1)"
    if [[ "$state" == "success" ]]; then
      mv -f "$file" "$FAILED/$id.job" 2>/dev/null || true
      continue
    fi
    started="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    log "$id" "agent restarted while job was running"
    backup="$BACKUPS/$id"
    [[ -d "$backup" ]] && restore_backup "$backup"
    write_status "$id" "failed" "interrupted" \
      "更新代理重启，任务已中断并恢复旧文件" \
      "$(job_get "$job" version)" "$(job_get "$job" targets)" \
      "$(job_get "$job" source)" "$(job_get "$job" created_at)" "$started" "$started"
    mv -f "$file" "$FAILED/$id.job" 2>/dev/null || true
  done
}

process_job() {
  local path="$1" id claimed
  id="$(basename "$path" .job)"
  if [[ ! "$id" =~ ^[0-9]{8}-[0-9]{6}-[0-9a-f]{8}$ ]]; then
    echo "[update-agent] invalid job filename: $id" >&2
    return
  fi
  claimed="$PROCESSING/$id.job"
  mv "$path" "$claimed" 2>/dev/null || return 0

  local version targets source created staging started finished backup
  version="$(job_get "$claimed" version)"
  targets="$(job_get "$claimed" targets)"
  source="$(job_get "$claimed" source)"
  created="$(job_get "$claimed" created_at)"
  staging="$(job_get "$claimed" staging)"
  started="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

  if [[ ! "$version" =~ ^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$ ]]; then
    write_status "$id" "failed" "failed" "版本号无效" "$version" "$targets" "$source" "$created" "$started" "$started"
    mv -f "$claimed" "$FAILED/$id.job" 2>/dev/null || true
    return
  fi

  local staging_root real_staging
  staging_root="$(realpath "$UPDATES/staging")"
  if [[ "$staging" == /* ]]; then
    real_staging=""
  else
    real_staging="$(realpath "$UPDATES/$staging" 2>/dev/null || true)"
  fi
  if [[ -z "$real_staging" || "$real_staging" != "$staging_root/"* ]]; then
    write_status "$id" "failed" "failed" "暂存目录越界或不存在" "$version" "$targets" "$source" "$created" "$started" "$started"
    mv -f "$claimed" "$FAILED/$id.job" 2>/dev/null || true
    return
  fi

  log "$id" "job $version targets=$targets"
  touch_heartbeat
  write_status "$id" "running" "backup" "备份当前版本" "$version" "$targets" "$source" "$created" "$started" ""
  backup="$(backup_current "$id")"

  write_status "$id" "running" "copy" "复制更新文件" "$version" "$targets" "$source" "$created" "$started" ""
  if [[ ",$targets," == *",api,"* ]]; then
    if [[ ! -s "$real_staging/artifacts/patnest-api" || ! -s "$real_staging/artifacts/patnest-migrate" ]]; then
      finished="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
      write_status "$id" "failed" "failed" "更新包缺少后端程序" "$version" "$targets" "$source" "$created" "$started" "$finished"
      mv -f "$claimed" "$FAILED/$id.job" 2>/dev/null || true
      return
    fi
  fi
  if [[ ",$targets," == *",web,"* && ! -s "$real_staging/web/dist/index.html" ]]; then
    finished="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    write_status "$id" "failed" "failed" "更新包缺少管理页面" "$version" "$targets" "$source" "$created" "$started" "$finished"
    mv -f "$claimed" "$FAILED/$id.job" 2>/dev/null || true
    return
  fi
  if [[ ",$targets," == *",api,"* ]]; then
    cp -a "$real_staging/artifacts/patnest-api" "$ROOT_DIR/artifacts/patnest-api"
    cp -a "$real_staging/artifacts/patnest-migrate" "$ROOT_DIR/artifacts/patnest-migrate"
  fi
  if [[ -d "$real_staging/db/migrations" ]]; then
    find "$real_staging/db/migrations" -maxdepth 1 -name '*.sql' -exec cp -a {} "$ROOT_DIR/db/migrations/" \;
  fi
  if [[ ",$targets," == *",web,"* ]]; then
    rm -rf "$ROOT_DIR/web/dist"
    mkdir -p "$ROOT_DIR/web/dist"
    cp -a "$real_staging/web/dist/." "$ROOT_DIR/web/dist/"
  fi

  if [[ ",$targets," == *",api,"* ]]; then
    write_status "$id" "running" "release-api" "构建后端并执行蓝绿发布" "$version" "$targets" "$source" "$created" "$started" ""
    if ! run_release "$id" release-api.sh "$version"; then
      restore_backup "$backup"
      finished="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
      write_status "$id" "failed" "failed" "后端发布失败，请查看日志" "$version" "$targets" "$source" "$created" "$started" "$finished"
      mv -f "$claimed" "$FAILED/$id.job" 2>/dev/null || true
      return
    fi
  fi

  if [[ ",$targets," == *",web,"* ]]; then
    write_status "$id" "running" "release-web" "重建管理页面" "$version" "$targets" "$source" "$created" "$started" ""
    if ! run_release "$id" release-web.sh "$version"; then
      restore_backup "$backup"
      finished="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
      write_status "$id" "failed" "failed" "页面发布失败，请查看日志" "$version" "$targets" "$source" "$created" "$started" "$finished"
      mv -f "$claimed" "$FAILED/$id.job" 2>/dev/null || true
      return
    fi
  fi

  update_env_version "$version"
  finished="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  write_status "$id" "success" "done" "更新完成" "$version" "$targets" "$source" "$created" "$started" "$finished"
  log "$id" "success"
  rm -f "$claimed"
  rm -rf "$real_staging"
}

touch_heartbeat
echo "[update-agent] watching $QUEUE (pid=$$)"
recover_interrupted

if [[ "${ONCE:-0}" == "1" ]]; then
  for job in "$QUEUE"/*.job; do
    [[ -e "$job" ]] || continue
    process_job "$job"
  done
  exit 0
fi

while true; do
  touch_heartbeat
  for job in "$QUEUE"/*.job; do
    [[ -e "$job" ]] || continue
    process_job "$job"
  done
  sleep "$POLL_SECONDS"
done
