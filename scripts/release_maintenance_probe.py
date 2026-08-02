#!/usr/bin/env python3
"""Governed shell template for bounded external maintenance verification."""
from __future__ import annotations


def external_maintenance_retry_function() -> str:
    return r'''wait_for_external_maintenance() {
  local external_status=""
  local external_attempt=""
  for external_attempt in $(seq 1 15); do
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
