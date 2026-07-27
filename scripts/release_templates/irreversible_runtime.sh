JAR_NAME=nuono-next-backend-0.0.1-SNAPSHOT.jar
ACTIVE_RUN_DIR="$APP_DIR/blue-green/$ACTIVE_SLOT"
ACTIVE_JAR="$ACTIVE_RUN_DIR/$JAR_NAME"
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="$APP_DIR/backups/$RELEASE_NAME-$STAMP/irreversible-schema"
MAINTENANCE_DIR="$WORK_DIR/maintenance"
MIGRATION_DIR="$WORK_DIR/migrations"
MYSQL_CNF="$WORK_DIR/mysql.cnf"
MIGRATION_182="$MIGRATION_DIR/182_product_barcode_psku_identity.sql"
MIGRATION_206="$MIGRATION_DIR/206_product_barcode_store_uniqueness.sql"
MAINTENANCE_PID=""
ACTIVE_PID=""
NEW_PID=""
MAINTENANCE_STARTED=0
MAINTENANCE_ROUTED=0
RUNTIME_STOPPED=0
IRREVERSIBLE_STARTED=0
FAILURE_HANDLER_RUNNING=0

emit() { printf '%s=%s\n' "$1" "$2"; }
sha256_file() { sha256sum "$1" | awk '{print $1}'; }
cleanup_mysql_client() { [ ! -f "$MYSQL_CNF" ] || rm -f -- "$MYSQL_CNF"; }
trap cleanup_mysql_client EXIT
pid_for_port() { lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -n 1 || true; }
backend_jvm_count() {
  ps -eo args= |
    awk -v jar_prefix="-jar $APP_DIR/" \
      '$1 ~ /(^|\/)java$/ && index($0, jar_prefix) > 0 {
         count += 1
       }
       END { print count + 0 }'
}
health_status() {
  local body=""
  body="$(curl -fsS --max-time 5 "http://127.0.0.1:$1/actuator/health" 2>/dev/null || true)"
  [ -n "$body" ] || { printf UNAVAILABLE; return 0; }
  printf '%s' "$body" |
    sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' |
    head -n 1
}
wait_for_health() {
  local attempt
  for attempt in $(seq 1 80); do
    [ "$(health_status "$1")" = UP ] && { printf '%s' "$attempt"; return 0; }
    sleep 1
  done
  return 1
}
stop_pid() {
  [ -z "$1" ] || kill "$1" 2>/dev/null || true
  for _ in $(seq 1 45); do
    [ -z "$1" ] || ! kill -0 "$1" 2>/dev/null && return 0
    sleep 1
  done
  return 1
}
process_uses_jar() { ps -p "$1" -o args= 2>/dev/null | grep -F -q -- "-jar $2"; }
current_upstream_port() {
  grep -Eo '127\.0\.0\.1:[0-9]+' "$NGINX_UPSTREAM_FILE" |
    head -n 1 |
    cut -d: -f2
}
write_upstream_port() {
  python3 - "$NGINX_UPSTREAM_FILE" "$1" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
original = path.read_text(encoding="utf-8")
updated = re.sub(r"127\.0\.0\.1:[0-9]+", f"127.0.0.1:{sys.argv[2]}", original, count=1)
if updated == original:
    raise SystemExit("no managed loopback upstream found")
path.write_text(updated, encoding="utf-8")
PY
}
switch_nginx_to_port() {
  write_upstream_port "$1"
  nginx -t
  nginx -s reload
  sleep 1
  [ "$(current_upstream_port)" = "$1" ]
}
maintenance_response_status() {
  curl -sS --max-time 2 -o "$MAINTENANCE_DIR/response.json" -w '%{http_code}'     "http://127.0.0.1:$MAINTENANCE_PORT/actuator/health" 2>/dev/null || true
}
external_maintenance_status() {
  curl -sS --max-time 10 -o "$MAINTENANCE_DIR/external-response.json" -w '%{http_code}'     "$EXTERNAL_HEALTH_URL" 2>/dev/null || true
}
start_maintenance_responder() {
  mkdir -p "$MAINTENANCE_DIR"
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]
  cat > "$MAINTENANCE_DIR/server.py" <<'PY'
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import sys

BODY = json.dumps({"status": 503, "message": "服务正在更新，请稍后重试"}, ensure_ascii=False).encode()
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
  nohup python3 "$MAINTENANCE_DIR/server.py" "$MAINTENANCE_PORT"     > "$MAINTENANCE_DIR/server.log" 2>&1 &
  MAINTENANCE_PID="$!"
  MAINTENANCE_STARTED=1
  printf '%s\n' "$MAINTENANCE_PID" > "$MAINTENANCE_DIR/server.pid"
  local status=""
  for _ in $(seq 1 20); do
    status="$(maintenance_response_status)"
    [ "$status" = 503 ] && break
    sleep 1
  done
  [ "$status" = 503 ]
}
stop_maintenance_responder() {
  local pid="${MAINTENANCE_PID:-}"
  [ -n "$pid" ] || pid="$(cat "$MAINTENANCE_DIR/server.pid" 2>/dev/null || true)"
  [ -z "$pid" ] || kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 10); do
    [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null && break
    sleep 1
  done
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]
}
switch_nginx_to_maintenance() {
  switch_nginx_to_port "$MAINTENANCE_PORT"
  [ "$(maintenance_response_status)" = 503 ]
  local external_status
  external_status="$(external_maintenance_status)"
  [ "$external_status" = 503 ]
  grep -F -q "服务正在更新，请稍后重试" "$MAINTENANCE_DIR/external-response.json"
  MAINTENANCE_ROUTED=1
  emit NGINX_CURRENT_PORT "$MAINTENANCE_PORT"
  emit MAINTENANCE_EXTERNAL_STATUS "$external_status"
}
switch_nginx_to_active() {
  switch_nginx_to_port "$ACTIVE_PORT"
  [ "$(health_status "$ACTIVE_PORT")" = UP ]
  local external_health
  external_health="$(curl -fsS --max-time 10 "$EXTERNAL_HEALTH_URL" |
    sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' |
    head -n 1)"
  [ "$external_health" = UP ]
}
ensure_repair_forward_maintenance() {
  if [ "$(maintenance_response_status)" != 503 ]; then
    MAINTENANCE_PID=""
    start_maintenance_responder || true
  fi
  [ "$(maintenance_response_status)" = 503 ] || return 1
  switch_nginx_to_maintenance || true
  [ "$(current_upstream_port)" = "$MAINTENANCE_PORT" ] || return 1
  [ "$(maintenance_response_status)" = 503 ] || return 1
  local external_status
  external_status="$(external_maintenance_status)"
  [ "$external_status" = 503 ] || return 1
  grep -F -q "服务正在更新，请稍后重试" \
    "$MAINTENANCE_DIR/external-response.json" || return 1
  MAINTENANCE_ROUTED=1
}
restart_same_new_runtime() {
  [ -f "$ACTIVE_JAR" ]
  [ "$(sha256_file "$ACTIVE_JAR")" = "$EXPECTED_JAR_SHA256" ]
  cd "$ACTIVE_RUN_DIR"
  rm -f nuono-next.pid
  NUONO_NEXT_APP_DIR="$ACTIVE_RUN_DIR" NUONO_NEXT_PORT="$ACTIVE_PORT" ./start-nuono-next-test.sh
  wait_for_health "$ACTIVE_PORT" >/dev/null
  NEW_PID="$(pid_for_port "$ACTIVE_PORT")"
  [ -n "$NEW_PID" ]
  process_uses_jar "$NEW_PID" "$ACTIVE_JAR"
  [ "$(sha256_file "$ACTIVE_JAR")" = "$EXPECTED_JAR_SHA256" ]
  [ -z "$(pid_for_port "$STANDBY_PORT")" ]
  [ "$(backend_jvm_count)" = 1 ]
  RUNTIME_STOPPED=0
}
handle_irreversible_failure() {
  local original_status="$?"
  trap - ERR
  set +e
  [ "$FAILURE_HANDLER_RUNNING" = 0 ] || exit "$original_status"
  FAILURE_HANDLER_RUNNING=1
  if [ "$IRREVERSIBLE_STARTED" = 1 ]; then
    emit IRREVERSIBLE_SCHEMA_RESULT REPAIR_FORWARD_REQUIRED
    if (set -Eeuo pipefail; ensure_repair_forward_maintenance); then
      emit MAINTENANCE_STATUS HELD
    else
      emit MAINTENANCE_STATUS ROUTE_REPAIR_REQUIRED
    fi
    emit SAFE_OLD_JAR_ROLLBACK FORBIDDEN
    exit "$original_status"
  fi
  if [ "$RUNTIME_STOPPED" = 1 ]; then
    restart_same_new_runtime || true
  fi
  if [ "$(health_status "$ACTIVE_PORT")" = UP ] &&
     (set -Eeuo pipefail; switch_nginx_to_active); then
    [ "$MAINTENANCE_STARTED" = 0 ] ||
      (set -Eeuo pipefail; stop_maintenance_responder) || true
  fi
  emit IRREVERSIBLE_SCHEMA_RESULT FAILED_BEFORE_206
  emit SAME_NEW_JAR_RESTORE "$([ "$(health_status "$ACTIVE_PORT")" = UP ] && printf PASS || printf FAILED)"
  exit "$original_status"
}
