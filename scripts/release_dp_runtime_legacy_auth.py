#!/usr/bin/env python3
"""Exact legacy authorization-wait cohort for the managed DP cutover."""
from __future__ import annotations


def build_dp_runtime_legacy_auth_shell() -> str:
    return r'''
DP_RUNTIME_LEGACY_SAFE_AUTH_COUNT=""
DP_RUNTIME_AUTH_PENDING_SUPERSEDED_IDS=""
DP_RUNTIME_AUTH_VALIDATING_SUPERSEDED_IDS=""
DP_RUNTIME_LEGACY_AUTH_SUPERSEDED_COUNT=0

dp_runtime_safe_auth_ids() {
  local status="$1"
  [ "$status" = PENDING ] || [ "$status" = VALIDATING ]
  if [ -z "$DP_RUNTIME_NOON_SUPERSEDED_IDS" ]; then
    printf ''
    return
  fi
  dp_runtime_db_scalar "SELECT COALESCE(GROUP_CONCAT(item.id ORDER BY item.id SEPARATOR ','),'')
    FROM noon_auth_identity_recovery_item item
    JOIN noon_pull_task task
      ON task.id=item.source_task_id AND task.auth_recovery_id=item.recovery_id
    WHERE item.status='$status'
      AND item.source_task_id IN ($DP_RUNTIME_NOON_SUPERSEDED_IDS)
      AND (item.source_domain IS NULL OR UPPER(item.source_domain) IN (
        'NOON_PULL','PRODUCT','SALES','SALES_SYNC','ORDER',
        'FINANCE_TRANSACTION','NOON_ADVERTISING','OFFICIAL_WAREHOUSE_ASN',
        'OFFICIAL_WAREHOUSE_INVENTORY','OFFICIAL_WAREHOUSE_FBN_RECEIVED'));"
}
'''


__all__ = ["build_dp_runtime_legacy_auth_shell"]
