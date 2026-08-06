#!/usr/bin/env python3
"""Generate exact Linux process, runtime payload, and maintenance identity guards."""
from __future__ import annotations


def build_runtime_identity_shell() -> str:
    return r'''bind_trusted_lsof() {
  [ -z "${LSOF_BIN:-}" ] || return 0
  LSOF_BIN="$(python3 - <<'PY'
import os, stat
for candidate in ("/usr/sbin/lsof", "/usr/bin/lsof"):
    try:
        metadata = os.stat(candidate, follow_symlinks=False)
    except FileNotFoundError:
        continue
    if (stat.S_ISREG(metadata.st_mode) and metadata.st_uid == 0 and
            not stat.S_IMODE(metadata.st_mode) & 0o022 and os.access(candidate, os.X_OK)):
        print(candidate, end="")
        break
else:
    raise SystemExit("trusted lsof executable is required")
PY
)"
}
listener_pids_for_port() {
  bind_trusted_lsof || return 1
  "$LSOF_BIN" -nP -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | sort -nu || true
}
pid_for_port() { listener_pids_for_port "$1" | head -n 1 || true; }
require_safe_pid() { [[ "$1" =~ ^[1-9][0-9]*$ ]] && [ "$1" -gt 1 ]; }
process_jar_path() {
  python3 - "$1" <<'PY'
import os, pathlib, sys
pid = sys.argv[1]
if not pid.isdecimal() or int(pid) <= 1:
    raise SystemExit(1)
try:
    executable = os.path.realpath(f"/proc/{pid}/exe")
    arguments = pathlib.Path(f"/proc/{pid}/cmdline").read_bytes().split(b"\0")
    mappings = pathlib.Path(f"/proc/{pid}/maps").read_bytes()
except (FileNotFoundError, PermissionError, ProcessLookupError):
    raise SystemExit(1)
if arguments and arguments[-1] == b"":
    arguments.pop()
if pathlib.Path(executable).name != "java" or b"/libjvm.so" not in mappings:
    raise SystemExit(1)
positions = [index for index, value in enumerate(arguments) if value == b"-jar"]
if len(positions) != 1 or positions[0] + 1 >= len(arguments):
    raise SystemExit(1)
if any(not value.startswith(b"-") for value in arguments[1:positions[0]]):
    raise SystemExit(1)
try:
    jar = os.fsdecode(arguments[positions[0] + 1])
except UnicodeDecodeError:
    raise SystemExit(1)
if not os.path.isabs(jar):
    raise SystemExit(1)
print(jar, end="")
PY
}
process_uses_jar() { [ "$(process_jar_path "$1")" = "$2" ]; }
java_pids_for_jar() {
  python3 - "$1" <<'PY'
import os, pathlib, sys
expected = sys.argv[1]
matches = []
for entry in pathlib.Path("/proc").iterdir():
    if not entry.name.isdecimal() or int(entry.name) <= 1:
        continue
    try:
        if pathlib.Path(os.path.realpath(entry / "exe")).name != "java":
            continue
        args = (entry / "cmdline").read_bytes().split(b"\0")
        if b"/libjvm.so" not in (entry / "maps").read_bytes():
            continue
    except (FileNotFoundError, PermissionError, ProcessLookupError):
        continue
    if args and args[-1] == b"":
        args.pop()
    positions = [index for index, value in enumerate(args) if value == b"-jar"]
    if (len(positions) == 1 and positions[0] + 1 < len(args) and
            all(value.startswith(b"-") for value in args[1:positions[0]])):
        if os.fsdecode(args[positions[0] + 1]) == expected:
            matches.append(int(entry.name))
print("\n".join(map(str, sorted(matches))))
PY
}
backend_jvm_pids() {
  python3 - "$APP_DIR" <<'PY'
import os, pathlib, sys
app_dir = os.path.abspath(sys.argv[1])
matches = []
for entry in pathlib.Path("/proc").iterdir():
    if not entry.name.isdecimal() or int(entry.name) <= 1:
        continue
    try:
        if pathlib.Path(os.path.realpath(entry / "exe")).name != "java":
            continue
        args = (entry / "cmdline").read_bytes().split(b"\0")
        if b"/libjvm.so" not in (entry / "maps").read_bytes():
            continue
    except (FileNotFoundError, PermissionError, ProcessLookupError):
        continue
    if args and args[-1] == b"":
        args.pop()
    positions = [index for index, value in enumerate(args) if value == b"-jar"]
    if len(positions) != 1 or positions[0] + 1 >= len(args):
        continue
    if any(not value.startswith(b"-") for value in args[1:positions[0]]):
        continue
    raw_jar = os.fsdecode(args[positions[0] + 1])
    if os.path.isabs(raw_jar):
        jar = os.path.realpath(raw_jar)
    else:
        try:
            jar = os.path.realpath(os.path.join(os.path.realpath(entry / "cwd"), raw_jar))
        except (FileNotFoundError, PermissionError, ProcessLookupError):
            continue
    try:
        inside = os.path.commonpath((app_dir, jar)) == app_dir
    except ValueError:
        inside = False
    if inside:
        matches.append(int(entry.name))
print("\n".join(map(str, sorted(matches))))
PY
}
backend_jvm_count() { backend_jvm_pids | awk 'NF { count++ } END { print count + 0 }'; }
assert_only_backend_jvm() { [ "$(backend_jvm_pids)" = "$1" ]; }
assert_no_backend_jvms() { [ -z "$(backend_jvm_pids)" ]; }
exact_java_pid_for_jar() {
  require_safe_pid "$1" || return 1
  [ "$(secure_file_operation verify "$2" "600,640,644" "$3")" = "$3" ] || return 1
  process_uses_jar "$1" "$2"
}
exact_listener_pid_for_jar() {
  local listeners="" count="" pid=""
  listeners="$(listener_pids_for_port "$1")"
  count="$(printf '%s\n' "$listeners" | awk 'NF { count++ } END { print count + 0 }')"
  [ "$count" = 1 ] || return 1
  pid="$(printf '%s\n' "$listeners" | head -n 1)"
  exact_java_pid_for_jar "$pid" "$2" "$3" || return 1
  printf '%s' "$pid"
}
topology_cas_sha256() {
  python3 - "$@" <<'PY'
import hashlib, json, sys
keys = ("nginx_sha256", "active_port", "active_pid", "jar_path", "jar_sha256", "app_dir")
payload = dict(zip(keys, sys.argv[1:], strict=True))
print(hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()).hexdigest())
PY
}
require_real_runtime_directory() {
  python3 - "$1" <<'PY'
import os, pathlib, stat, sys
path = pathlib.Path(sys.argv[1])
if not path.is_absolute() or os.path.realpath(path) != str(path):
    raise SystemExit("runtime directory is not a real absolute directory")
metadata = os.stat(path, follow_symlinks=False)
if not stat.S_ISDIR(metadata.st_mode) or metadata.st_uid != os.geteuid():
    raise SystemExit("runtime directory trust mismatch")
PY
  secure_file_operation directory "$1" "700,750,755" 700 accept
}
runtime_env_has_forbidden_injection() {
  python3 - "$1" <<'PY'
import re, sys
for raw in open(sys.argv[1], encoding="utf-8"):
    line = raw.strip()
    if not line or line.startswith("#"):
        continue
    line = re.sub(r"^export\s+", "", line)
    match = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\s*=", line)
    if not match:
        continue
    key = match.group(1)
    if (key == "SPRING_APPLICATION_JSON" or key.startswith("SPRING_DATASOURCE_") or
            key in {"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS"}):
        raise SystemExit(f"forbidden runtime environment key: {key}")
PY
}
freeze_active_runtime_payloads() {
  require_real_runtime_directory "$ACTIVE_RUN_DIR"
  ACTIVE_ENV_SHA256="$(secure_file_operation verify "$ACTIVE_RUN_DIR/.env" 600 -)"
  ACTIVE_START_SCRIPT_SHA256="$(secure_file_operation verify \
    "$ACTIVE_RUN_DIR/start-nuono-next-test.sh" "700,750,755" -)"
  runtime_env_has_forbidden_injection "$ACTIVE_RUN_DIR/.env"
  [ "$(secure_file_operation verify "$ACTIVE_JAR_PATH" "600,640,644" \
    "$EXPECTED_ACTIVE_JAR_SHA256")" = "$EXPECTED_ACTIVE_JAR_SHA256" ]
}
reverify_active_runtime_payloads() {
  require_real_runtime_directory "$ACTIVE_RUN_DIR"
  [ "$(secure_file_operation verify "$ACTIVE_RUN_DIR/.env" 600 \
    "$ACTIVE_ENV_SHA256")" = "$ACTIVE_ENV_SHA256" ]
  [ "$(secure_file_operation verify "$ACTIVE_RUN_DIR/start-nuono-next-test.sh" \
    "700,750,755" "$ACTIVE_START_SCRIPT_SHA256")" = "$ACTIVE_START_SCRIPT_SHA256" ]
  runtime_env_has_forbidden_injection "$ACTIVE_RUN_DIR/.env"
  [ "$(secure_file_operation verify "$ACTIVE_JAR_PATH" "600,640,644" \
    "$EXPECTED_ACTIVE_JAR_SHA256")" = "$EXPECTED_ACTIVE_JAR_SHA256" ]
}
wait_for_unique_target_jvm() {
  local attempt="" pids="" count=""
  for attempt in {1..40}; do
    pids="$(java_pids_for_jar "$TARGET_SLOT_DIR/$JAR_NAME")"
    count="$(printf '%s\n' "$pids" | awk 'NF { count++ } END { print count + 0 }')"
    if [ "$count" = 1 ]; then
      local pid="${pids%%$'\n'*}"
      exact_java_pid_for_jar "$pid" "$TARGET_SLOT_DIR/$JAR_NAME" \
        "$EXPECTED_JAR_SHA256" || return 1
      assert_only_backend_jvm "$pid" || return 1
      printf '%s' "$pid"
      return 0
    fi
    [ "$count" = 0 ] || return 1
    sleep 1
  done
  return 1
}
assert_target_runtime_identity() {
  require_safe_pid "$NEW_PID" || return 1
  [ "$(exact_listener_pid_for_jar "$TARGET_PORT" "$TARGET_SLOT_DIR/$JAR_NAME" \
    "$EXPECTED_JAR_SHA256")" = "$NEW_PID" ] || return 1
  assert_only_backend_jvm "$NEW_PID"
}
stop_target_runtime() {
  local pids="" count="" pid=""
  pids="$(java_pids_for_jar "$TARGET_SLOT_DIR/$JAR_NAME")"
  count="$(printf '%s\n' "$pids" | awk 'NF { count++ } END { print count + 0 }')"
  if [ "$count" = 0 ]; then
    [ -z "$(pid_for_port "$TARGET_PORT")" ]
    return
  fi
  [ "$count" = 1 ] || return 1
  pid="${pids%%$'\n'*}"
  [ -z "${NEW_PID:-}" ] || [ "$NEW_PID" = "$pid" ] || return 1
  exact_java_pid_for_jar "$pid" "$TARGET_SLOT_DIR/$JAR_NAME" \
    "$EXPECTED_JAR_SHA256" || return 1
  stop_pid "$pid" || return 1
  [ -z "$(java_pids_for_jar "$TARGET_SLOT_DIR/$JAR_NAME")" ] &&
    [ -z "$(pid_for_port "$TARGET_PORT")" ]
}
exact_maintenance_listener_pid() {
  local listeners="" count="" pid=""
  listeners="$(listener_pids_for_port "$MAINTENANCE_PORT")"
  count="$(printf '%s\n' "$listeners" | awk 'NF { count++ } END { print count + 0 }')"
  [ "$count" = 1 ] || return 1
  pid="${listeners%%$'\n'*}"
  require_safe_pid "$pid" || return 1
  [ -n "${MAINTENANCE_PID:-}" ] && [ "$pid" = "$MAINTENANCE_PID" ] || return 1
  [ "$(secure_file_operation verify "$MAINTENANCE_DIR/server.py" 600 \
    "$MAINTENANCE_SERVER_SHA256")" = "$MAINTENANCE_SERVER_SHA256" ] || return 1
  python3 - "$pid" "$MAINTENANCE_PYTHON_EXE" "$MAINTENANCE_DIR/server.py" \
    "$MAINTENANCE_PORT" <<'PY'
import os, pathlib, sys
pid, expected_exe, server, port = sys.argv[1:]
try:
    executable = os.path.realpath(f"/proc/{pid}/exe")
    args = pathlib.Path(f"/proc/{pid}/cmdline").read_bytes().split(b"\0")
except (FileNotFoundError, PermissionError, ProcessLookupError):
    raise SystemExit(1)
if args and args[-1] == b"":
    args.pop()
expected = [os.fsencode(expected_exe), os.fsencode(server), os.fsencode(port)]
if executable != expected_exe or args != expected:
    raise SystemExit(1)
PY
  printf '%s' "$pid"
}
'''


__all__ = ["build_runtime_identity_shell"]
