#!/usr/bin/env python3
"""Generate a single-scheduler backend cutover that preserves DP LEGACY mode."""
from __future__ import annotations
import shlex
from urllib.parse import urlsplit
from release_maintenance_probe import trap_safe_health_function
from release_maintenance_responder import build_maintenance_responder_shell
from release_nginx_upstream import build_nginx_upstream_shell
from release_predecessor_rollback import build_predecessor_rollback_shell
from release_secure_slot_files import build_secure_file_shell
LEGACY_EXECUTION_MODES = frozenset({"LEGACY", "LEGACY_DEFAULT"})
def _q(value: str | int) -> str:
    return shlex.quote(str(value))
def _validated_external_health_url(value: str) -> str:
    parsed = urlsplit(value)
    allowed = {
        ("https", "www.nuoon.com", None, "/ai/actuator/health"),
        ("http", "123.60.15.70", None, "/ai/actuator/health"),
    }
    identity = (parsed.scheme, parsed.hostname, parsed.port, parsed.path)
    if (
        parsed.username
        or parsed.password
        or parsed.query
        or parsed.fragment
        or identity not in allowed
    ):
        raise ValueError("external health URL is outside the governed allowlist")
    return value
def build_legacy_single_scheduler_cutover_script(
    *,
    staged_jar: str,
    expected_jar_sha256: str,
    expected_commit: str,
    expected_active_jar_sha256: str,
    expected_active_pid: int,
    expected_nginx_upstream_sha256: str,
    expected_topology_cas_sha256: str,
    expected_dp_execution_mode: str,
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
    if expected_dp_execution_mode not in LEGACY_EXECUTION_MODES:
        raise ValueError("LEGACY-preserving cutover requires an observed LEGACY mode")
    values = {
        "APP_DIR": app_dir,
        "STAGED_JAR": staged_jar,
        "EXPECTED_JAR_SHA256": expected_jar_sha256,
        "EXPECTED_COMMIT": expected_commit,
        "EXPECTED_ACTIVE_JAR_SHA256": expected_active_jar_sha256,
        "EXPECTED_ACTIVE_PID": expected_active_pid,
        "EXPECTED_NGINX_UPSTREAM_SHA256": expected_nginx_upstream_sha256,
        "EXPECTED_TOPOLOGY_CAS_SHA256": expected_topology_cas_sha256,
        "EXPECTED_DP_EXECUTION_MODE": expected_dp_execution_mode,
        "ACTIVE_SLOT": active_slot,
        "TARGET_SLOT": target_slot,
        "ACTIVE_PORT": active_port,
        "TARGET_PORT": target_port,
        "MAINTENANCE_PORT": maintenance_port,
        "NGINX_UPSTREAM_FILE": nginx_upstream_file,
        "RELEASE_NAME": release_name,
        "EXTERNAL_HEALTH_URL": _validated_external_health_url(external_health_url),
        "ALLOW_UNHEALTHY_ACTIVE": "1" if allow_unhealthy_active else "0",
    }
    assignments = "\n".join(f"{key}={_q(value)}" for key, value in values.items())
    return f"""#!/usr/bin/env bash
set -Eeuo pipefail
{assignments}
PRESERVE_DP_LEGACY=1
JAR_NAME=nuono-next-backend-0.0.1-SNAPSHOT.jar
ACTIVE_SLOT_DIR="$APP_DIR/blue-green/$ACTIVE_SLOT"
TARGET_SLOT_DIR="$APP_DIR/blue-green/$TARGET_SLOT"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$APP_DIR/backups/$RELEASE_NAME-$STAMP"
MAINTENANCE_DIR="$BACKUP_DIR/maintenance"
MAINTENANCE_PID="" MAINTENANCE_PYTHON_EXE="" MAINTENANCE_SERVER_SHA256=""
ACTIVE_PID="" NEW_PID="" ACTIVE_RUN_DIR="" ACTIVE_ENV_SHA256="" ACTIVE_START_SCRIPT_SHA256=""
ACTIVE_RUNTIME_KIND="" UPSTREAM_BACKUP="" TARGET_ENV_SHA256="" SOURCE_ENV_SHA256=""
SOURCE_START_SCRIPT_SHA256="" NGINX_UPSTREAM_SHA256="" NGINX_UPSTREAM_ORIGINAL_SHA256=""
NGINX_UPSTREAM_BACKUP_SHA256="" LSOF_BIN="" READY_ATTEMPT=""
MAINTENANCE_ROUTED=0 OLD_STOPPED=0 NEW_START_ATTEMPTED=0 ROLLBACK_RUNNING=0
emit() {{ printf '%s=%s\\n' "$1" "$2"; }}
{build_secure_file_shell()}
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
{build_maintenance_responder_shell()}
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
    /bin/bash --noprofile --norc "$1/start-nuono-next-test.sh"
}}
{build_predecessor_rollback_shell()}
legacy_env_mode() {{
  python3 - "$1" <<'PY'
import re, sys
values = []
for raw in open(sys.argv[1], encoding="utf-8"):
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    line = re.sub(r"^export\\s+", "", line)
    key, value = line.split("=", 1)
    if key.strip() != "NUONO_DATA_PULL_EXECUTION_MODE":
        continue
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
        value = value[1:-1]
    values.append(value)
if not values:
    print("LEGACY_DEFAULT", end="")
elif values == ["LEGACY"]:
    print("LEGACY", end="")
else:
    raise SystemExit("runtime env is not unambiguously LEGACY")
PY
}}
legacy_process_mode() {{
  python3 - "$1" <<'PY'
import pathlib, sys
pid = sys.argv[1]
if not pid.isdecimal() or int(pid) <= 1:
    raise SystemExit("invalid runtime pid")
values = [item.split(b"=", 1)[1].decode() for item in
          pathlib.Path(f"/proc/{{pid}}/environ").read_bytes().split(b"\\0")
          if item.startswith(b"NUONO_DATA_PULL_EXECUTION_MODE=")]
if not values:
    print("LEGACY_DEFAULT", end="")
elif values == ["LEGACY"]:
    print("LEGACY", end="")
else:
    raise SystemExit("runtime process is not unambiguously LEGACY")
PY
}}
assert_legacy_env_contract() {{
  [ "$(legacy_env_mode "$1")" = "$EXPECTED_DP_EXECUTION_MODE" ]
  ! grep -Eq '^NUONO_MANAGED_DP_RELEASE=' "$1"
  ! grep -Eq '^NUONO_DATA_PULL_RUNTIME_ENABLED=' "$1"
  ! grep -Eq '^NUONO_DP(10|_RUNTIME)_' "$1"
}}
prepare_target_runtime_payloads() {{
  local runtime_format="" runtime_suffix=""
  runtime_format='\\nNUONO_NEXT_APP_DIR=%s\\nNUONO_NEXT_PORT=%s\\n'
  runtime_format+='NUONO_NEXT_JAR=%s/%s\\nNUONO_NEXT_LOG_FILE=%s/nuono-next.log\\n'
  runtime_format+='NUONO_NEXT_PID_FILE=%s/nuono-next.pid\\n'
  runtime_format+='NUONO_NEXT_AUTH_SESSION_SECRET_FILE=%s/.auth-session-secret\\n'
  printf -v runtime_suffix "$runtime_format" \
    "$TARGET_SLOT_DIR" "$TARGET_PORT" "$TARGET_SLOT_DIR" "$JAR_NAME" \
    "$TARGET_SLOT_DIR" "$TARGET_SLOT_DIR" "$TARGET_SLOT_DIR"
  TARGET_ENV_SHA256="$(secure_file_operation install "$APP_DIR/.env" \
    "$TARGET_SLOT_DIR/.env" 600 600 600 "$SOURCE_ENV_SHA256" replace "$runtime_suffix")"
  [ "$(secure_file_operation install "$APP_DIR/start-nuono-next-test.sh" \
    "$TARGET_SLOT_DIR/start-nuono-next-test.sh" "700,750,755" 700 \
    "700,750,755" "$SOURCE_START_SCRIPT_SHA256" replace "")" = \
    "$SOURCE_START_SCRIPT_SHA256" ]
  [ "$(secure_file_operation install "$STAGED_JAR" "$TARGET_SLOT_DIR/$JAR_NAME" \
    "600,640,644" 600 "600,640,644" "$EXPECTED_JAR_SHA256" replace "")" = \
    "$EXPECTED_JAR_SHA256" ]
  runtime_env_has_forbidden_injection "$TARGET_SLOT_DIR/.env"
  assert_legacy_env_contract "$TARGET_SLOT_DIR/.env"
}}
assert_source_payloads() {{
  [ "$(secure_file_operation verify "$APP_DIR/.env" 600 "$SOURCE_ENV_SHA256")" = \
    "$SOURCE_ENV_SHA256" ]
  [ "$(secure_file_operation verify "$APP_DIR/start-nuono-next-test.sh" \
    "700,750,755" "$SOURCE_START_SCRIPT_SHA256")" = "$SOURCE_START_SCRIPT_SHA256" ]
  runtime_env_has_forbidden_injection "$APP_DIR/.env"
  assert_legacy_env_contract "$APP_DIR/.env"
}}
assert_target_release_ready() {{
  [ "$(health_status "$TARGET_PORT")" = UP ] &&
    assert_target_runtime_identity &&
    [ "$(legacy_process_mode "$NEW_PID")" = "$EXPECTED_DP_EXECUTION_MODE" ] &&
  [ "$(secure_file_operation verify "$TARGET_SLOT_DIR/.env" 600 \
      "$TARGET_ENV_SHA256")" = "$TARGET_ENV_SHA256" ] &&
    [ "$(secure_file_operation verify "$TARGET_SLOT_DIR/start-nuono-next-test.sh" \
      700 "$SOURCE_START_SCRIPT_SHA256")" = "$SOURCE_START_SCRIPT_SHA256" ] &&
    assert_legacy_env_contract "$TARGET_SLOT_DIR/.env"
}}
validate_cutover() {{
  [ "$ACTIVE_SLOT" != "$TARGET_SLOT" ]
  [ "$ACTIVE_PORT" != "$TARGET_PORT" ]
  [ "$MAINTENANCE_PORT" != "$ACTIVE_PORT" ]
  [ "$MAINTENANCE_PORT" != "$TARGET_PORT" ]
  [ "$EXPECTED_DP_EXECUTION_MODE" = LEGACY_DEFAULT ] ||
    [ "$EXPECTED_DP_EXECUTION_MODE" = LEGACY ]
  bind_trusted_lsof
  bind_nginx_upstream "$ACTIVE_PORT"
  [ "$NGINX_UPSTREAM_ORIGINAL_SHA256" = "$EXPECTED_NGINX_UPSTREAM_SHA256" ]
  [ -z "$(pid_for_port "$TARGET_PORT")" ]
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]
  [ "$(secure_file_operation verify "$STAGED_JAR" "600,640,644" \
    "$EXPECTED_JAR_SHA256")" = "$EXPECTED_JAR_SHA256" ]
  SOURCE_ENV_SHA256="$(secure_file_operation verify "$APP_DIR/.env" 600 -)"
  SOURCE_START_SCRIPT_SHA256="$(secure_file_operation verify \
    "$APP_DIR/start-nuono-next-test.sh" "700,750,755" -)"
  [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{{40}}$ ]]
  [[ "$EXPECTED_NGINX_UPSTREAM_SHA256" =~ ^[0-9a-f]{{64}}$ ]]
  [[ "$EXPECTED_TOPOLOGY_CAS_SHA256" =~ ^[0-9a-f]{{64}}$ ]]
  require_safe_pid "$EXPECTED_ACTIVE_PID"
  assert_source_payloads
}}
validate_cutover
ACTIVE_PID="$(pid_for_port "$ACTIVE_PORT")"
[ "$ACTIVE_PID" = "$EXPECTED_ACTIVE_PID" ]
initial_health="$(health_status "$ACTIVE_PORT")"
[ "$initial_health" = UP ] || [ "$ALLOW_UNHEALTHY_ACTIVE" = 1 ]
ACTIVE_JAR_PATH="$(process_jar_path "$ACTIVE_PID")"
case "$ACTIVE_JAR_PATH" in
  "$ACTIVE_SLOT_DIR/$JAR_NAME") ACTIVE_RUN_DIR="$ACTIVE_SLOT_DIR"; ACTIVE_RUNTIME_KIND=slot ;;
  "$APP_DIR/$JAR_NAME") ACTIVE_RUN_DIR="$APP_DIR"; ACTIVE_RUNTIME_KIND=canonical ;;
  *) echo "unexpected active Jar path: $ACTIVE_JAR_PATH" >&2; exit 20 ;;
esac
[ "$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" \
  "$EXPECTED_ACTIVE_JAR_SHA256")" = "$ACTIVE_PID" ]
[ "$(topology_cas_sha256 "$NGINX_UPSTREAM_ORIGINAL_SHA256" "$ACTIVE_PORT" \
  "$ACTIVE_PID" "$ACTIVE_JAR_PATH" "$EXPECTED_ACTIVE_JAR_SHA256" "$APP_DIR")" = \
  "$EXPECTED_TOPOLOGY_CAS_SHA256" ]
[ "$(legacy_process_mode "$ACTIVE_PID")" = "$EXPECTED_DP_EXECUTION_MODE" ]
freeze_active_runtime_payloads
assert_legacy_env_contract "$ACTIVE_RUN_DIR/.env"
assert_only_backend_jvm "$ACTIVE_PID"
secure_file_operation directory "$APP_DIR/backups" "700,750,755" 700 accept
secure_file_operation directory "$BACKUP_DIR" 700 700 create-new
secure_file_operation directory "$APP_DIR/blue-green" "700,750,755" 700 accept
secure_file_operation directory "$TARGET_SLOT_DIR" "700,750,755" 700 accept
prepare_target_runtime_payloads
UPSTREAM_BACKUP="$BACKUP_DIR/$(basename "$NGINX_UPSTREAM_FILE").before"
backup_nginx_upstream "$UPSTREAM_BACKUP"
trap rollback_cutover ERR
start_maintenance_responder
switch_nginx_to_maintenance
reverify_active_runtime_payloads
assert_source_payloads
[ "$(legacy_process_mode "$ACTIVE_PID")" = "$EXPECTED_DP_EXECUTION_MODE" ]
assert_only_backend_jvm "$ACTIVE_PID"
stop_pid "$ACTIVE_PID"
[ -z "$(pid_for_port "$ACTIVE_PORT")" ]
assert_no_backend_jvms
OLD_STOPPED=1
rm -f "$TARGET_SLOT_DIR/nuono-next.pid"
NEW_START_ATTEMPTED=1
start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"
NEW_PID="$(wait_for_unique_target_jvm)"
wait_for_health "$TARGET_PORT"
assert_target_release_ready
[ -z "$(pid_for_port "$ACTIVE_PORT")" ]
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
emit DP_RELEASE_MODE PRESERVE_LEGACY; emit DP_EXECUTION_MODE "$EXPECTED_DP_EXECUTION_MODE"
emit DP_DATA_WRITE_COUNT 0; emit TARGET_READY_ATTEMPT "$READY_ATTEMPT"; emit TARGET_PID "$NEW_PID"
emit TARGET_HEALTH UP; emit DP_RUNTIME_HEALTH NOT_ACTIVATED
emit ACTIVE_PORT "$TARGET_PORT"; emit NGINX_CURRENT_PORT "$TARGET_PORT"
emit ACTIVE_SLOT "$TARGET_SLOT"; emit ACTIVE_JAR_PATH "$TARGET_SLOT_DIR/$JAR_NAME"; emit ACTIVE_RUNTIME_KIND slot
emit TOPOLOGY_CAS_SHA256 "$FINAL_TOPOLOGY_CAS_SHA256"; emit EXTERNAL_HEALTH "$external_health"
"""
__all__ = ["build_legacy_single_scheduler_cutover_script"]
