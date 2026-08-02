#!/usr/bin/env python3
"""Generate the single-scheduler cutover with a loopback JSON 503 bridge."""
from __future__ import annotations
import shlex
from release_maintenance_probe import external_maintenance_retry_function
def _q(value: str | int) -> str:
    return shlex.quote(str(value))
def build_single_scheduler_cutover_script(
    *,
    staged_jar: str,
    expected_jar_sha256: str,
    expected_active_jar_sha256: str,
    active_slot: str,
    target_slot: str,
    active_port: int,
    target_port: int,
    maintenance_port: int,
    nginx_upstream_file: str,
    release_name: str,
    external_health_url: str,
    app_dir: str,
    allow_unhealthy_active: bool = False,
) -> str:
    """Return the locked remote cutover body."""
    values = {
        "APP_DIR": app_dir,
        "STAGED_JAR": staged_jar,
        "EXPECTED_JAR_SHA256": expected_jar_sha256,
        "EXPECTED_ACTIVE_JAR_SHA256": expected_active_jar_sha256,
        "ACTIVE_SLOT": active_slot,
        "TARGET_SLOT": target_slot,
        "ACTIVE_PORT": active_port,
        "TARGET_PORT": target_port,
        "MAINTENANCE_PORT": maintenance_port,
        "NGINX_UPSTREAM_FILE": nginx_upstream_file,
        "RELEASE_NAME": release_name,
        "EXTERNAL_HEALTH_URL": external_health_url,
        "ALLOW_UNHEALTHY_ACTIVE": "1" if allow_unhealthy_active else "0",
    }
    assignments = "\n".join(f"{key}={_q(value)}" for key, value in values.items())
    return f"""#!/usr/bin/env bash
set -Eeuo pipefail
{assignments}
JAR_NAME=nuono-next-backend-0.0.1-SNAPSHOT.jar
ACTIVE_SLOT_DIR="$APP_DIR/blue-green/$ACTIVE_SLOT"
TARGET_SLOT_DIR="$APP_DIR/blue-green/$TARGET_SLOT"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$APP_DIR/backups/$RELEASE_NAME-$STAMP"
MAINTENANCE_DIR="$BACKUP_DIR/maintenance"
MAINTENANCE_PID=""
ACTIVE_PID=""
NEW_PID=""
ACTIVE_RUN_DIR=""
ACTIVE_RUNTIME_KIND=""
UPSTREAM_BACKUP=""
MAINTENANCE_ROUTED=0
OLD_STOPPED=0
NEW_START_ATTEMPTED=0
ROLLBACK_RUNNING=0

emit() {{ printf '%s=%s\\n' "$1" "$2"; }}
sha256_file() {{ sha256sum "$1" | awk '{{print $1}}'; }}
pid_for_port() {{ lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -n 1 || true; }}
slot_pid() {{ [ -f "$1/nuono-next.pid" ] && grep -Eo '^[0-9]+' "$1/nuono-next.pid" | head -n 1 || true; }}
health_status() {{
  local body=""
  body="$(curl -fsS --max-time 5 "http://127.0.0.1:$1/actuator/health" 2>/dev/null || true)"
  [ -n "$body" ] || {{ printf UNAVAILABLE; return 0; }}
  printf '%s' "$body" | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\\([^"]*\\)".*/\\1/p' | head -n 1
}}
wait_for_health() {{
  local attempt
  for attempt in $(seq 1 80); do
    [ "$(health_status "$1")" = UP ] && {{ READY_ATTEMPT="$attempt"; return 0; }}
    sleep 1
  done
  return 1
}}
current_upstream_port() {{
  grep -Eo '127\\.0\\.0\\.1:[0-9]+' "$NGINX_UPSTREAM_FILE" | head -n 1 | cut -d: -f2
}}
write_upstream_port() {{
  python3 - "$NGINX_UPSTREAM_FILE" "$1" <<'PY'
from pathlib import Path
import re, sys
path = Path(sys.argv[1])
original = path.read_text()
updated, replacements = re.subn(r"127\\.0\\.0\\.1:[0-9]+", f"127.0.0.1:{{sys.argv[2]}}", original, count=1)
if replacements != 1:
    raise SystemExit("no managed loopback upstream found")
path.write_text(updated)
PY
}}
switch_nginx_to_port() {{
  write_upstream_port "$1"
  nginx -t
  nginx -s reload
  sleep 1
  [ "$(current_upstream_port)" = "$1" ]
}}
maintenance_response_status() {{
  curl -sS --max-time 2 -o "$MAINTENANCE_DIR/response.json" -w '%{{http_code}}' \
    "http://127.0.0.1:$MAINTENANCE_PORT/actuator/health" 2>/dev/null || true
}}
external_maintenance_status() {{
  curl -sS --max-time 10 -o "$MAINTENANCE_DIR/external-response.json" -w '%{{http_code}}' \
    "$EXTERNAL_HEALTH_URL" 2>/dev/null || true
}}
{external_maintenance_retry_function()}
start_maintenance_responder() {{
  mkdir -p "$MAINTENANCE_DIR"
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ] || {{
    echo "maintenance port already has a listener: $MAINTENANCE_PORT" >&2; return 1;
  }}
  cat > "$MAINTENANCE_DIR/server.py" <<'PY'
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json, sys
BODY = json.dumps({{"status": 503, "message": "服务正在更新，请稍后重试"}}, ensure_ascii=False).encode()
class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def respond(self):
        self.send_response(503, "Service Unavailable")
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(BODY)))
        self.send_header("Retry-After", "5")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(BODY)
    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_OPTIONS = respond
    def log_message(self, *_): pass
ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
PY
  nohup python3 "$MAINTENANCE_DIR/server.py" "$MAINTENANCE_PORT" \
    > "$MAINTENANCE_DIR/server.log" 2>&1 &
  MAINTENANCE_PID="$!"
  echo "$MAINTENANCE_PID" > "$MAINTENANCE_DIR/server.pid"
  local maintenance_status=""
  for _ in $(seq 1 20); do
    maintenance_status="$(maintenance_response_status)"
    [ "$maintenance_status" = "503" ] && break
    sleep 1
  done
  [ "$maintenance_status" = "503" ]
  python3 - "$MAINTENANCE_DIR/response.json" <<'PY'
import json, sys
payload = json.load(open(sys.argv[1], encoding="utf-8"))
assert payload == {{"status": 503, "message": "服务正在更新，请稍后重试"}}
PY
}}
stop_maintenance_responder() {{
  local pid="${{MAINTENANCE_PID:-}}"
  [ -n "$pid" ] || pid="$(cat "$MAINTENANCE_DIR/server.pid" 2>/dev/null || true)"
  [ -z "$pid" ] || kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 10); do
    [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null && break
    sleep 1
  done
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]
}}
switch_nginx_to_maintenance() {{
  switch_nginx_to_port "$MAINTENANCE_PORT" || return 1
  local maintenance_status=""
  maintenance_status="$(maintenance_response_status)"
  [ "$maintenance_status" = "503" ] || return 1
  local external_status=""
  if ! external_status="$(wait_for_external_maintenance)"; then
    return 1
  fi
  [ "$external_status" = "503" ] || return 1
  MAINTENANCE_ROUTED=1
  emit NGINX_CURRENT_PORT "$MAINTENANCE_PORT"
  emit MAINTENANCE_EXTERNAL_STATUS "$external_status"
}}
stop_pid() {{
  [ -z "$1" ] || kill "$1" 2>/dev/null || true
  for _ in $(seq 1 45); do
    [ -z "$1" ] || ! kill -0 "$1" 2>/dev/null && return 0
    sleep 1
  done
  return 1
}}
start_runtime() {{
  cd "$1"
  NUONO_NEXT_APP_DIR="$1" NUONO_NEXT_PORT="$2" ./start-nuono-next-test.sh
}}
stop_target_runtime() {{
  local pid="${{NEW_PID:-}}"
  [ -n "$pid" ] || pid="$(slot_pid "$TARGET_SLOT_DIR")"
  [ -n "$pid" ] || pid="$(pid_for_port "$TARGET_PORT")"
  stop_pid "$pid" || return 1
  [ -z "$(pid_for_port "$TARGET_PORT")" ]
}}
process_uses_jar() {{ ps -p "$1" -o args= 2>/dev/null | grep -F -q -- "-jar $2"; }}
restart_old_runtime() {{
  [ -n "$(pid_for_port "$ACTIVE_PORT")" ] || start_runtime "$ACTIVE_RUN_DIR" "$ACTIVE_PORT"
}}
restore_nginx_to_active() {{
  cp "$UPSTREAM_BACKUP" "$NGINX_UPSTREAM_FILE"
  nginx -t
  nginx -s reload
  sleep 1
  [ "$(current_upstream_port)" = "$ACTIVE_PORT" ]
}}
rollback_cutover() {{
  local original_status="$?"
  trap - ERR
  set +e
  [ "$ROLLBACK_RUNNING" = 0 ] || exit "$original_status"
  ROLLBACK_RUNNING=1
  if [ "$MAINTENANCE_ROUTED" = 1 ] && ! switch_nginx_to_maintenance; then
    emit ROLLBACK_RESULT BLOCKED_MAINTENANCE_UNAVAILABLE
    emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
    exit "$original_status"
  fi
  [ "$NEW_START_ATTEMPTED" = 0 ] || stop_target_runtime || true
  if [ "$OLD_STOPPED" = 1 ] || [ -z "$(pid_for_port "$ACTIVE_PORT")" ]; then
    restart_old_runtime || true
    wait_for_health "$ACTIVE_PORT" >/dev/null || true
  fi
  if [ -n "$UPSTREAM_BACKUP" ] && [ -f "$UPSTREAM_BACKUP" ] &&
     [ "$(health_status "$ACTIVE_PORT")" = UP ]; then
    restore_nginx_to_active || true
  fi
  [ "$(health_status "$ACTIVE_PORT")" != UP ] || stop_maintenance_responder || true
  rollback_external="$(curl -fsS --max-time 10 "$EXTERNAL_HEALTH_URL" 2>/dev/null |
    sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\\([^"]*\\)".*/\\1/p' | head -n 1 || true)"
  if [ "$(health_status "$ACTIVE_PORT")" = UP ] && [ "$rollback_external" = UP ] &&
     [ "$(current_upstream_port)" = "$ACTIVE_PORT" ] &&
     [ -z "$(pid_for_port "$TARGET_PORT")" ] && [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]; then
    emit ROLLBACK_RESULT PASS; emit CUTOVER_RESULT FAILED_ROLLED_BACK; exit 0
  fi
  emit ROLLBACK_RESULT FAILED; emit CUTOVER_RESULT FAILED_ROLLBACK_ATTEMPTED
  exit "$original_status"
}}
validate_cutover() {{
  [ "$ACTIVE_SLOT" != "$TARGET_SLOT" ]
  [ "$ACTIVE_PORT" != "$TARGET_PORT" ]
  [ "$MAINTENANCE_PORT" != "$ACTIVE_PORT" ]
  [ "$MAINTENANCE_PORT" != "$TARGET_PORT" ]
  command -v lsof >/dev/null
  grep -F -q NUONO_BLUE_GREEN_MANAGED "$NGINX_UPSTREAM_FILE"
  [ "$(current_upstream_port)" = "$ACTIVE_PORT" ]
  [ -z "$(pid_for_port "$TARGET_PORT")" ]
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]
  [ -f "$STAGED_JAR" ]
  [ "$(sha256_file "$STAGED_JAR")" = "$EXPECTED_JAR_SHA256" ]
}}

validate_cutover
ACTIVE_PID="$(pid_for_port "$ACTIVE_PORT")"
[ -n "$ACTIVE_PID" ]
initial_health="$(health_status "$ACTIVE_PORT")"
[ "$initial_health" = UP ] || [ "$ALLOW_UNHEALTHY_ACTIVE" = 1 ]
ACTIVE_JAR_PATH="$(ps -p "$ACTIVE_PID" -o args= | sed -n 's/.*-jar[[:space:]]\\([^[:space:]]*\\).*/\\1/p')"
[ "$(sha256_file "$ACTIVE_JAR_PATH")" = "$EXPECTED_ACTIVE_JAR_SHA256" ]
case "$ACTIVE_JAR_PATH" in
  "$ACTIVE_SLOT_DIR/$JAR_NAME") ACTIVE_RUN_DIR="$ACTIVE_SLOT_DIR"; ACTIVE_RUNTIME_KIND=slot ;;
  "$APP_DIR/$JAR_NAME") ACTIVE_RUN_DIR="$APP_DIR"; ACTIVE_RUNTIME_KIND=canonical ;;
  *) echo "unexpected active Jar path: $ACTIVE_JAR_PATH" >&2; exit 20 ;;
esac
mkdir -p "$TARGET_SLOT_DIR" "$BACKUP_DIR"
cp "$APP_DIR/.env" "$TARGET_SLOT_DIR/.env"
printf '\\nNUONO_NEXT_APP_DIR=%s\\nNUONO_NEXT_PORT=%s\\nNUONO_NEXT_JAR=%s/%s\\nNUONO_NEXT_LOG_FILE=%s/nuono-next.log\\nNUONO_NEXT_PID_FILE=%s/nuono-next.pid\\nNUONO_NEXT_AUTH_SESSION_SECRET_FILE=%s/.auth-session-secret\\n' \
  "$TARGET_SLOT_DIR" "$TARGET_PORT" "$TARGET_SLOT_DIR" "$JAR_NAME" "$TARGET_SLOT_DIR" "$TARGET_SLOT_DIR" "$TARGET_SLOT_DIR" >> "$TARGET_SLOT_DIR/.env"
cp "$APP_DIR/start-nuono-next-test.sh" "$TARGET_SLOT_DIR/start-nuono-next-test.sh"
cp "$STAGED_JAR" "$TARGET_SLOT_DIR/$JAR_NAME"
[ "$(sha256_file "$TARGET_SLOT_DIR/$JAR_NAME")" = "$EXPECTED_JAR_SHA256" ]
UPSTREAM_BACKUP="$BACKUP_DIR/$(basename "$NGINX_UPSTREAM_FILE").before"
cp "$NGINX_UPSTREAM_FILE" "$UPSTREAM_BACKUP"
trap rollback_cutover ERR
start_maintenance_responder
switch_nginx_to_maintenance
kill "$ACTIVE_PID"
stop_pid "$ACTIVE_PID"
[ -z "$(pid_for_port "$ACTIVE_PORT")" ]
OLD_STOPPED=1
rm -f "$TARGET_SLOT_DIR/nuono-next.pid"
NEW_START_ATTEMPTED=1
start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"
wait_for_health "$TARGET_PORT"
NEW_PID="$(pid_for_port "$TARGET_PORT")"
[ -n "$NEW_PID" ]
process_uses_jar "$NEW_PID" "$TARGET_SLOT_DIR/$JAR_NAME"
[ -z "$(pid_for_port "$ACTIVE_PORT")" ]
switch_nginx_to_port "$TARGET_PORT"
[ "$(health_status "$TARGET_PORT")" = UP ]
external_health="$(curl -fsS --max-time 10 "$EXTERNAL_HEALTH_URL" |
  sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\\([^"]*\\)".*/\\1/p' | head -n 1)"
[ "$external_health" = UP ]
stop_maintenance_responder
trap - ERR
emit CUTOVER_RESULT PASS
emit SINGLE_SCHEDULER_GUARD PASS
emit TARGET_READY_ATTEMPT "$READY_ATTEMPT"
emit TARGET_PID "$NEW_PID"
emit ACTIVE_PORT "$TARGET_PORT"
emit ACTIVE_JAR_PATH "$ACTIVE_JAR_PATH"
emit ACTIVE_RUNTIME_KIND "$ACTIVE_RUNTIME_KIND"
emit EXTERNAL_HEALTH "$external_health"
"""
