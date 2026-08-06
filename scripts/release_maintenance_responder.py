#!/usr/bin/env python3
"""Generate the JSON 503 responder used by single-scheduler cutovers."""
from __future__ import annotations

from release_maintenance_probe import (
    external_maintenance_retry_function,
    trap_safe_capture_functions,
)


def build_maintenance_responder_shell() -> str:
    return f'''maintenance_response_status() {{
  curl -sS --max-time 2 -o "$MAINTENANCE_DIR/response.json" -w '%{{http_code}}' \
    "http://127.0.0.1:$MAINTENANCE_PORT/actuator/health" 2>/dev/null || true
}}
external_maintenance_status() {{
  curl -sS --max-time 10 -o "$MAINTENANCE_DIR/external-response.json" -w '%{{http_code}}' \
    -- "$EXTERNAL_HEALTH_URL" 2>/dev/null || true
}}
{external_maintenance_retry_function()}
{trap_safe_capture_functions()}
start_maintenance_responder() {{
  secure_file_operation directory "$MAINTENANCE_DIR" 700 700 create-new
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ] || {{
    echo "maintenance port already has a listener: $MAINTENANCE_PORT" >&2; return 1;
  }}
  local server_source=""
  server_source="$(cat <<'PY'
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
)"
  MAINTENANCE_SERVER_SHA256="$(secure_file_operation write \
    "$MAINTENANCE_DIR/server.py" 600 600 create-new utf8 "$server_source")"
  MAINTENANCE_PYTHON_EXE="$(readlink -f "$(command -v python3)")"
  [ -x "$MAINTENANCE_PYTHON_EXE" ]
  nohup "$MAINTENANCE_PYTHON_EXE" "$MAINTENANCE_DIR/server.py" "$MAINTENANCE_PORT" \
    > "$MAINTENANCE_DIR/server.log" 2>&1 &
  MAINTENANCE_PID="$!"
  require_safe_pid "$MAINTENANCE_PID"
  local maintenance_status=""
  for _ in {{1..20}}; do
    maintenance_status="$(maintenance_response_status)"
    [ "$maintenance_status" = "503" ] && break
    sleep 1
  done
  [ "$maintenance_status" = "503" ]
  [ "$(exact_maintenance_listener_pid)" = "$MAINTENANCE_PID" ]
  python3 - "$MAINTENANCE_DIR/response.json" <<'PY'
import json, sys
payload = json.load(open(sys.argv[1], encoding="utf-8"))
assert payload == {{"status": 503, "message": "服务正在更新，请稍后重试"}}
PY
}}
stop_maintenance_responder() {{
  local pid="${{MAINTENANCE_PID:-}}"
  if [ -z "$pid" ]; then [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]; return; fi
  require_safe_pid "$pid" || return 1
  [ "$(exact_maintenance_listener_pid)" = "$pid" ] || return 1
  stop_pid "$pid" || return 1
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]
}}
switch_nginx_to_maintenance() {{
  switch_nginx_to_port "$MAINTENANCE_PORT" || return 1
  local maintenance_status=""
  maintenance_status="$(maintenance_response_status)"
  [ "$maintenance_status" = "503" ] || return 1
  local external_status=""
  capture_status external_status wait_for_external_maintenance || return 1
  [ "$external_status" = "503" ] || return 1
  MAINTENANCE_ROUTED=1
  emit NGINX_CURRENT_PORT "$MAINTENANCE_PORT"
  emit MAINTENANCE_EXTERNAL_STATUS "$external_status"
}}'''


__all__ = ["build_maintenance_responder_shell"]
