#!/usr/bin/env python3
"""Governed shell template for bounded external maintenance verification."""
from __future__ import annotations


def external_maintenance_retry_function() -> str:
    return r'''wait_for_external_maintenance() {
  local external_status=""
  local external_attempt=""
  for external_attempt in {1..15}; do
    if [ "$(maintenance_response_status)" != "503" ]; then
      printf '%s' "${external_status:-UNAVAILABLE}"
      return 1
    fi
    external_status="$(external_maintenance_status)"
    if [ "$external_status" = "503" ] \
      && grep -F -q "服务正在更新，请稍后重试" "$MAINTENANCE_DIR/external-response.json"; then
      printf '%s' "$external_status"
      return 0
    fi
    sleep 1
  done
  printf '%s' "${external_status:-UNAVAILABLE}"
  return 1
}'''


def trap_safe_capture_functions() -> str:
    return r'''capture_status() {
  local target="$1" previous="" output="" status=0
  shift
  previous="$(trap -p ERR)"
  trap - ERR
  if output="$("$@")"; then status=0; else status="$?"; fi
  if [ -n "$previous" ]; then eval "$previous"; else trap - ERR; fi
  printf -v "$target" '%s' "$output"
  return "$status"
}
post_switch_external_health() {
  curl -fsS --max-time 10 "$EXTERNAL_HEALTH_URL" |
    sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1
}'''


def trap_safe_health_function() -> str:
    return r'''health_status() {
  local body="" parsed=""
  if ! capture_status body loopback_health_body "$1"; then printf UNAVAILABLE; return 0; fi
  [ -n "$body" ] || { printf UNAVAILABLE; return 0; }
  if ! capture_status parsed parse_health_body "$body"; then printf UNAVAILABLE; return 0; fi
  printf '%s' "${parsed:-UNAVAILABLE}"
}
loopback_health_body() {
  curl -fsS --max-time 5 "http://127.0.0.1:$1/actuator/health" 2>/dev/null
}
parse_health_body() {
  printf '%s' "$1" | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1
}'''
