#!/usr/bin/env python3
from __future__ import annotations
import shlex
from urllib.parse import urlsplit
from release_dp10_openapi_probe import build_dp10_openapi_probe_shell
from release_dp_report_download_probe import build_dp_report_download_probe_shell
from release_dp_runtime_contract_evidence import build_dp_runtime_contract_evidence_shell
from release_dp_runtime_cutover import build_dp_runtime_cutover_shell
from release_dp_runtime_manifest import build_dp_runtime_manifest_shell
from release_dp_runtime_bootstrap import build_dp_runtime_bootstrap_shell
from release_maintenance_probe import external_maintenance_retry_function, trap_safe_capture_functions, trap_safe_health_function
from release_nginx_upstream import build_nginx_upstream_shell
from release_predecessor_rollback import build_predecessor_rollback_shell
from release_runtime_readiness import build_runtime_readiness_shell
def _q(value: str | int) -> str: return shlex.quote(str(value))
def _validated_external_health_url(value: str) -> str:
    parsed = urlsplit(value)
    allowed = {("https", "www.nuoon.com", None, "/ai/actuator/health"),
               ("http", "123.60.15.70", None, "/ai/actuator/health")}
    identity = (parsed.scheme, parsed.hostname, parsed.port, parsed.path)
    if parsed.username or parsed.password or parsed.query or parsed.fragment or identity not in allowed:
        raise ValueError("external health URL is outside the governed allowlist")
    return value
def build_single_scheduler_cutover_script(
    *,
    staged_jar: str, expected_jar_sha256: str, expected_commit: str,
    expected_active_jar_sha256: str, expected_active_pid: int,
    expected_nginx_upstream_sha256: str, expected_topology_cas_sha256: str,
    active_slot: str, target_slot: str,
    active_port: int, target_port: int, maintenance_port: int,
    nginx_upstream_file: str, release_name: str,
    external_health_url: str, app_dir: str,
    allow_unhealthy_active: bool = False,
) -> str:
    values = {
        "APP_DIR": app_dir, "STAGED_JAR": staged_jar,
        "EXPECTED_JAR_SHA256": expected_jar_sha256, "EXPECTED_COMMIT": expected_commit,
        "EXPECTED_ACTIVE_JAR_SHA256": expected_active_jar_sha256,
        "EXPECTED_ACTIVE_PID": expected_active_pid,
        "EXPECTED_NGINX_UPSTREAM_SHA256": expected_nginx_upstream_sha256,
        "EXPECTED_TOPOLOGY_CAS_SHA256": expected_topology_cas_sha256,
        "ACTIVE_SLOT": active_slot, "TARGET_SLOT": target_slot,
        "ACTIVE_PORT": active_port, "TARGET_PORT": target_port,
        "MAINTENANCE_PORT": maintenance_port, "NGINX_UPSTREAM_FILE": nginx_upstream_file,
        "RELEASE_NAME": release_name,
        "EXTERNAL_HEALTH_URL": _validated_external_health_url(external_health_url),
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
DP10_PROBE_DIR="$BACKUP_DIR/dp10-openapi-probe"
DP10_PROBE_EVIDENCE_FILE="$DP10_PROBE_DIR/evidence.json"
DP_REPORT_PROBE_DIR="$BACKUP_DIR/dp-report-download-probe" DP_REPORT_PROBE_EVIDENCE_FILE="$DP_REPORT_PROBE_DIR/evidence.json"
MAINTENANCE_PID="" MAINTENANCE_PYTHON_EXE="" MAINTENANCE_SERVER_SHA256=""
ACTIVE_PID="" NEW_PID="" ACTIVE_RUN_DIR="" ACTIVE_ENV_SHA256="" ACTIVE_START_SCRIPT_SHA256=""
ACTIVE_RUNTIME_KIND="" UPSTREAM_BACKUP="" TARGET_ENV_SHA256="" SOURCE_START_SCRIPT_SHA256=""
NGINX_UPSTREAM_SHA256="" NGINX_UPSTREAM_ORIGINAL_SHA256="" NGINX_UPSTREAM_BACKUP_SHA256=""
DP10_SLOT_EVIDENCE_FILE="" DP10_RUNTIME_ENV_ATTESTATION_FILE=""
DP_RUNTIME_CONTRACT_SOURCE_FILE="" DP_RUNTIME_CONTRACT_SLOT_FILE="" DP_RUNTIME_CONTRACT_EVIDENCE_SHA256=""
DP_REPORT_PROBE_SOURCE_FILE="" DP_REPORT_PROBE_SOURCE_SHA256="" DP_REPORT_PROBE_NONCE="" DP_REPORT_PROBE_NONCE_SHA256="" DP_REPORT_PROBE_EVIDENCE_SHA256=""
LSOF_BIN=""
MAINTENANCE_ROUTED=0 OLD_STOPPED=0 NEW_START_ATTEMPTED=0 ROLLBACK_RUNNING=0

emit() {{ printf '%s=%s\\n' "$1" "$2"; }}
sha256_file() {{ sha256sum "$1" | awk '{{print $1}}'; }}
{build_dp10_openapi_probe_shell()}
{build_dp_report_download_probe_shell()}
{build_dp_runtime_contract_evidence_shell()}
{build_dp_runtime_cutover_shell()}
{build_dp_runtime_manifest_shell()}
{build_dp_runtime_bootstrap_shell()}
{trap_safe_health_function()}
wait_for_health() {{
  local attempt
  for attempt in {{1..80}}; do
    [ "$(health_status "$1")" = UP ] && {{ READY_ATTEMPT="$attempt"; return 0; }}
    sleep 1
  done
  return 1
}}
{build_nginx_upstream_shell()}
switch_nginx_to_port() {{
  write_upstream_port "$1"
  nginx -t
  nginx -s reload
  sleep 1
  local current_port=""; capture_status current_port current_upstream_port
  [ "$current_port" = "$1" ]
}}
maintenance_response_status() {{
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
}}
stop_pid() {{
  require_safe_pid "$1" || return 1
  kill -TERM -- "$1" 2>/dev/null || true
  for _ in {{1..45}}; do
    ! kill -0 -- "$1" 2>/dev/null && return 0
    sleep 1
  done
  return 1
}}
start_runtime() {{
  require_real_runtime_directory "$1"
  runtime_env_has_forbidden_injection "$1/.env"
  cd "$1"
  /usr/bin/env -i PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LANG=C LC_ALL=C NUONO_NEXT_APP_DIR="$1" NUONO_NEXT_PORT="$2" \
    NUONO_MANAGED_DP_RELEASE=1 \
    /bin/bash --noprofile --norc "$1/start-nuono-next-test.sh"
}}
{build_predecessor_rollback_shell()}
{build_runtime_readiness_shell()}
validate_cutover() {{
  [ "$ACTIVE_SLOT" != "$TARGET_SLOT" ]
  [ "$ACTIVE_PORT" != "$TARGET_PORT" ]
  [ "$MAINTENANCE_PORT" != "$ACTIVE_PORT" ]
  [ "$MAINTENANCE_PORT" != "$TARGET_PORT" ]
  bind_trusted_lsof
  bind_nginx_upstream "$ACTIVE_PORT"
  [ "$NGINX_UPSTREAM_ORIGINAL_SHA256" = "$EXPECTED_NGINX_UPSTREAM_SHA256" ]
  [ -z "$(pid_for_port "$TARGET_PORT")" ]
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]
  [ "$(secure_file_operation verify "$STAGED_JAR" "600,640,644" \
    "$EXPECTED_JAR_SHA256")" = "$EXPECTED_JAR_SHA256" ]
  secure_file_operation verify "$APP_DIR/.env" 600 - >/dev/null
  SOURCE_START_SCRIPT_SHA256="$(secure_file_operation verify \
    "$APP_DIR/start-nuono-next-test.sh" "700,750,755" -)"
  [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{{40}}$ ]]
  [[ "$EXPECTED_NGINX_UPSTREAM_SHA256" =~ ^[0-9a-f]{{64}}$ ]]
  [[ "$EXPECTED_TOPOLOGY_CAS_SHA256" =~ ^[0-9a-f]{{64}}$ ]]
  require_safe_pid "$EXPECTED_ACTIVE_PID"
  runtime_env_has_forbidden_injection "$APP_DIR/.env"
}}
validate_cutover
ACTIVE_PID="$(pid_for_port "$ACTIVE_PORT")"
[ -n "$ACTIVE_PID" ]
[ "$ACTIVE_PID" = "$EXPECTED_ACTIVE_PID" ]
initial_health="$(health_status "$ACTIVE_PORT")"
[ "$initial_health" = UP ] || [ "$ALLOW_UNHEALTHY_ACTIVE" = 1 ]
ACTIVE_JAR_PATH="$(process_jar_path "$ACTIVE_PID")"
case "$ACTIVE_JAR_PATH" in
  "$ACTIVE_SLOT_DIR/$JAR_NAME") ACTIVE_RUN_DIR="$ACTIVE_SLOT_DIR"; ACTIVE_RUNTIME_KIND=slot ;;
  "$APP_DIR/$JAR_NAME") ACTIVE_RUN_DIR="$APP_DIR"; ACTIVE_RUNTIME_KIND=canonical ;;
  *) echo "unexpected active Jar path: $ACTIVE_JAR_PATH" >&2; exit 20 ;;
esac
[ "$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" "$EXPECTED_ACTIVE_JAR_SHA256")" = "$ACTIVE_PID" ]
[ "$(topology_cas_sha256 "$NGINX_UPSTREAM_ORIGINAL_SHA256" "$ACTIVE_PORT" \
  "$ACTIVE_PID" "$ACTIVE_JAR_PATH" "$EXPECTED_ACTIVE_JAR_SHA256" "$APP_DIR")" = \
  "$EXPECTED_TOPOLOGY_CAS_SHA256" ]
freeze_active_runtime_payloads
assert_only_backend_jvm "$ACTIVE_PID"
prepare_dp_runtime_cutover
run_dp10_openapi_probe
run_dp_report_download_probe
load_dp_runtime_contract_evidence
run_dp_runtime_cutover_manifest
assert_only_backend_jvm "$ACTIVE_PID"
secure_file_operation directory "$APP_DIR/blue-green" "700,750,755" 700 accept
secure_file_operation directory "$TARGET_SLOT_DIR" "700,750,755" 700 accept
persist_dp10_probe_for_target
persist_dp_runtime_contract_evidence_for_target
prepare_target_runtime_payloads
UPSTREAM_BACKUP="$BACKUP_DIR/$(basename "$NGINX_UPSTREAM_FILE").before"
backup_nginx_upstream "$UPSTREAM_BACKUP"
trap rollback_cutover ERR
verify_dp10_probe_state
verify_dp_report_probe_state
verify_dp_runtime_contract_evidence_state
start_maintenance_responder
switch_nginx_to_maintenance
reverify_active_runtime_payloads
[ "$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" \
  "$EXPECTED_ACTIVE_JAR_SHA256")" = "$ACTIVE_PID" ]
assert_only_backend_jvm "$ACTIVE_PID"
require_legacy_cutover_ready
stop_pid "$ACTIVE_PID"
[ -z "$(pid_for_port "$ACTIVE_PORT")" ]
assert_no_backend_jvms
OLD_STOPPED=1
finalize_dp_runtime_legacy_cutover
recheck_dp_runtime_cutover_manifest
bootstrap_dp_runtime_cutover
prepare_dp10_probe_runtime_environment
rm -f "$TARGET_SLOT_DIR/nuono-next.pid"
NEW_START_ATTEMPTED=1
verify_dp10_probe_state
verify_dp_runtime_contract_evidence_state
verify_dp_runtime_database_binding
require_legacy_cutover_empty
start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"
NEW_PID="$(wait_for_unique_target_jvm)"
wait_for_health "$TARGET_PORT"
assert_target_release_ready
[ -z "$(pid_for_port "$ACTIVE_PORT")" ]
verify_dp10_probe_state
verify_dp_runtime_contract_evidence_state
verify_dp_runtime_database_binding
require_legacy_cutover_empty
assert_target_release_ready
switch_nginx_to_port "$TARGET_PORT"
assert_target_release_ready
external_health=""; capture_status external_health post_switch_external_health
[ "$external_health" = UP ]
assert_target_release_ready
FINAL_TOPOLOGY_CAS_SHA256="$(topology_cas_sha256 "$NGINX_UPSTREAM_SHA256" \
  "$TARGET_PORT" "$NEW_PID" "$TARGET_SLOT_DIR/$JAR_NAME" "$EXPECTED_JAR_SHA256" "$APP_DIR")"
stop_maintenance_responder
trap - ERR
emit CUTOVER_RESULT PASS; emit SINGLE_SCHEDULER_GUARD PASS
emit TARGET_READY_ATTEMPT "$READY_ATTEMPT"; emit TARGET_PID "$NEW_PID"
emit TARGET_HEALTH UP; emit DP_RUNTIME_HEALTH UP
emit ACTIVE_PORT "$TARGET_PORT"; emit NGINX_CURRENT_PORT "$TARGET_PORT"
emit ACTIVE_SLOT "$TARGET_SLOT"; emit ACTIVE_JAR_PATH "$TARGET_SLOT_DIR/$JAR_NAME"; emit ACTIVE_RUNTIME_KIND slot
emit TOPOLOGY_CAS_SHA256 "$FINAL_TOPOLOGY_CAS_SHA256"; emit EXTERNAL_HEALTH "$external_health"
emit DP_LEGACY_SUPERSEDED_COUNT "$DP_RUNTIME_LEGACY_SUPERSEDED_COUNT"; emit DP_LEGACY_REMAINING_AFTER_STOP "$DP_RUNTIME_LEGACY_REMAINING_AFTER_STOP"
emit DP_RUNTIME_SCHEMA_BINDING_SHA256 "$DP_RUNTIME_SCHEMA_BINDING_SHA256"; emit DP_RUNTIME_CUTOVER_BINDING_SHA256 "$DP_RUNTIME_CUTOVER_BINDING_SHA256"
emit DP_RUNTIME_MANIFEST_BASELINE_SHA256 "$DP_RUNTIME_BASELINE_MANIFEST_SHA256"; emit DP_RUNTIME_MANIFEST_RECHECK_SHA256 "$DP_RUNTIME_RECHECK_MANIFEST_SHA256"; emit DP_RUNTIME_BOOTSTRAP_SQL_SHA256 "$DP_RUNTIME_BOOTSTRAP_SQL_SHA256"
"""
