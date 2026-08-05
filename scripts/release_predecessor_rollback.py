#!/usr/bin/env python3
"""Generate exact predecessor restart and rollback guards."""
from __future__ import annotations
from release_runtime_identity import build_runtime_identity_shell


def build_predecessor_rollback_shell() -> str:
    return build_runtime_identity_shell() + r'''restart_old_runtime() {
  reverify_active_runtime_payloads || return 1
  local listener_pid="" java_pids="" count=""
  listener_pid="$(pid_for_port "$ACTIVE_PORT")"
  if [ -n "$listener_pid" ]; then
    [ "$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" \
      "$EXPECTED_ACTIVE_JAR_SHA256")" = "$listener_pid" ] &&
      assert_only_backend_jvm "$listener_pid"
    return
  fi
  java_pids="$(java_pids_for_jar "$ACTIVE_JAR_PATH")"
  count="$(printf '%s\n' "$java_pids" | awk 'NF { count++ } END { print count + 0 }')"
  if [ "$count" = 1 ]; then
    assert_only_backend_jvm "${java_pids%%$'\n'*}"
    return
  fi
  [ "$count" = 0 ] && assert_no_backend_jvms || return 1
  start_runtime "$ACTIVE_RUN_DIR" "$ACTIVE_PORT"
}
restore_nginx_to_active() {
  local pid=""
  pid="$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" \
    "$EXPECTED_ACTIVE_JAR_SHA256")" || return 1
  assert_only_backend_jvm "$pid" || return 1
  restore_nginx_upstream
  nginx -t && nginx -s reload
  sleep 1
  [ "$(current_upstream_port)" = "$ACTIVE_PORT" ] &&
    [ "$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" \
      "$EXPECTED_ACTIVE_JAR_SHA256")" = "$pid" ] && assert_only_backend_jvm "$pid"
}
rollback_cutover() {
  local original_status="$?" predecessor_pid="" predecessor_restored=0 rollback_external=""
  trap - ERR
  set +e
  [ "$ROLLBACK_RUNNING" = 0 ] || exit "$original_status"
  ROLLBACK_RUNNING=1
  if [ "$MAINTENANCE_ROUTED" = 1 ] && ! switch_nginx_to_maintenance; then
    emit ROLLBACK_RESULT BLOCKED_MAINTENANCE_UNAVAILABLE
    emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
    exit "$original_status"
  fi
  if [ "$NEW_START_ATTEMPTED" = 1 ] && ! stop_target_runtime; then
    emit ROLLBACK_RESULT BLOCKED_TARGET_MISMATCH
    emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
    exit "$original_status"
  fi
  if ! reverify_active_runtime_payloads; then
    emit ROLLBACK_RESULT BLOCKED_PREDECESSOR_PAYLOAD_DRIFT
    emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
    exit "$original_status"
  fi
  if [ "$OLD_STOPPED" = 1 ] && declare -F rollback_managed_release_data >/dev/null &&
     ! rollback_managed_release_data; then
    emit ROLLBACK_RESULT BLOCKED_MANAGED_DATA_REPAIR_FORWARD_REQUIRED
    emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
    exit "$original_status"
  fi
  predecessor_pid="$(pid_for_port "$ACTIVE_PORT")"
  if [ -n "$predecessor_pid" ] &&
     [ "$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" \
       "$EXPECTED_ACTIVE_JAR_SHA256")" != "$predecessor_pid" ]; then
    emit ROLLBACK_RESULT BLOCKED_PREDECESSOR_MISMATCH
    emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
    exit "$original_status"
  fi
  if [ "$OLD_STOPPED" = 1 ] || [ -z "$predecessor_pid" ]; then
    if ! restart_old_runtime || ! wait_for_health "$ACTIVE_PORT" >/dev/null; then
      emit ROLLBACK_RESULT BLOCKED_PREDECESSOR_RESTART_FAILED
      emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
      exit "$original_status"
    fi
  fi
  predecessor_pid="$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" \
    "$EXPECTED_ACTIVE_JAR_SHA256")" || {
      emit ROLLBACK_RESULT BLOCKED_PREDECESSOR_MISMATCH
      emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
      exit "$original_status"
    }
  assert_only_backend_jvm "$predecessor_pid" || {
    emit ROLLBACK_RESULT BLOCKED_BACKEND_JVM_CENSUS
    emit CUTOVER_RESULT FAILED_MAINTENANCE_PROTECTED
    exit "$original_status"
  }
  if [ -n "$UPSTREAM_BACKUP" ] && [ -f "$UPSTREAM_BACKUP" ] &&
     [ "$(health_status "$ACTIVE_PORT")" = UP ] && restore_nginx_to_active; then
    predecessor_restored=1
  fi
  if [ "$predecessor_restored" = 1 ] && [ "$(health_status "$ACTIVE_PORT")" = UP ]; then
    stop_maintenance_responder || true
  fi
  rollback_external="$(curl -fsS --max-time 10 -- "$EXTERNAL_HEALTH_URL" 2>/dev/null |
    sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1 || true)"
  if [ "$(health_status "$ACTIVE_PORT")" = UP ] && [ "$rollback_external" = UP ] &&
     [ "$(current_upstream_port)" = "$ACTIVE_PORT" ] &&
     [ "$(exact_listener_pid_for_jar "$ACTIVE_PORT" "$ACTIVE_JAR_PATH" \
       "$EXPECTED_ACTIVE_JAR_SHA256")" = "$predecessor_pid" ] &&
     assert_only_backend_jvm "$predecessor_pid" &&
     [ -z "$(pid_for_port "$TARGET_PORT")" ] && [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]; then
    emit ROLLBACK_RESULT PASS
    emit CUTOVER_RESULT FAILED_ROLLED_BACK
    exit 0
  fi
  emit ROLLBACK_RESULT FAILED
  emit CUTOVER_RESULT FAILED_ROLLBACK_ATTEMPTED
  exit "$original_status"
}
'''


__all__ = ["build_predecessor_rollback_shell"]
