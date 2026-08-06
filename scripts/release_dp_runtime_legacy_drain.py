#!/usr/bin/env python3
"""Frozen zero-fact predecessor cohort drain for the managed DP cutover."""
from __future__ import annotations


def build_dp_runtime_legacy_drain_shell() -> str:
    return r'''
DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT=""
DP_RUNTIME_LEGACY_SAFE_DP10_COUNT=""
DP_RUNTIME_NOON_SUPERSEDED_IDS=""
DP_RUNTIME_DP10_SUPERSEDED_IDS=""
DP_RUNTIME_LEGACY_SUPERSEDED_COUNT=0
DP_RUNTIME_LEGACY_REMAINING_AFTER_STOP=""

dp_runtime_legacy_counts() {
  dp_runtime_db_scalar "
    SELECT CONCAT_WS(CHAR(9),
      (SELECT COUNT(*) FROM noon_pull_task
       WHERE status IN ('QUEUED','RUNNING','BLOCKED_AUTH') AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM noon_pull_task
       WHERE status IN ('QUEUED','RUNNING','BLOCKED_AUTH')
         AND trigger_mode='SCHEDULED_DAILY'
         AND pull_type IN ('INTERFACE','REPORT')
         AND data_domain IN ('PRODUCT','SALES','ORDER','FINANCE_TRANSACTION',
           'NOON_ADVERTISING','OFFICIAL_WAREHOUSE_ASN',
           'OFFICIAL_WAREHOUSE_INVENTORY','OFFICIAL_WAREHOUSE_FBN_RECEIVED')
         AND ((status='BLOCKED_AUTH' AND retry_action='WAIT_FOR_AUTH')
           OR (status='QUEUED' AND started_at IS NULL)
           OR (status='RUNNING' AND started_at IS NOT NULL
             AND COALESCE(retry_action,'')<>'WAIT_FOR_AUTH'))
         AND checkpoint_cursor IS NULL AND next_resume_position IS NULL
         AND last_safe_response_summary IS NULL
         AND COALESCE(processed_item_count,0)=0
         AND COALESCE(request_count,0)=0
         AND COALESCE(report_total_rows,0)=0
         AND finished_at IS NULL AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM operational_task
       WHERE task_type='PRODUCT_PUBLIC_DETAIL_SYNC'
         AND status IN ('QUEUED','RUNNING') AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM procurement_ali1688_order_sync_task
       WHERE status='running' AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM procurement_ali1688_order_sync_task
       WHERE status='running'
         AND COALESCE(processed_count,0)=0
         AND COALESCE(imported_count,0)=0
         AND COALESCE(failed_count,0)=0
         AND COALESCE(progress_percent,0)=0
         AND failure_code IS NULL AND failure_message IS NULL
         AND COALESCE(requires_manual_action,b'0')=b'0'
         AND finished_at IS NULL AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM sales_sync_task
       WHERE status IN ('queued','running','waiting_authorization')),
      (SELECT COUNT(*) FROM noon_auth_identity_recovery_item
       WHERE status IN ('PENDING','VALIDATING') AND source_task_id IS NOT NULL
         AND (source_domain IS NULL OR UPPER(source_domain) IN (
           'NOON_PULL','PRODUCT','SALES','SALES_SYNC','ORDER',
           'FINANCE_TRANSACTION','NOON_ADVERTISING','OFFICIAL_WAREHOUSE_ASN',
           'OFFICIAL_WAREHOUSE_INVENTORY','OFFICIAL_WAREHOUSE_FBN_RECEIVED'))));"
}
dp_runtime_safe_noon_ids() {
  dp_runtime_db_scalar "SELECT COALESCE(GROUP_CONCAT(id ORDER BY id SEPARATOR ','),'')
    FROM noon_pull_task
    WHERE status IN ('QUEUED','RUNNING','BLOCKED_AUTH')
      AND trigger_mode='SCHEDULED_DAILY'
      AND pull_type IN ('INTERFACE','REPORT')
      AND data_domain IN ('PRODUCT','SALES','ORDER','FINANCE_TRANSACTION',
        'NOON_ADVERTISING','OFFICIAL_WAREHOUSE_ASN',
        'OFFICIAL_WAREHOUSE_INVENTORY','OFFICIAL_WAREHOUSE_FBN_RECEIVED')
      AND ((status='BLOCKED_AUTH' AND retry_action='WAIT_FOR_AUTH')
        OR (status='QUEUED' AND started_at IS NULL)
        OR (status='RUNNING' AND started_at IS NOT NULL
          AND COALESCE(retry_action,'')<>'WAIT_FOR_AUTH'))
      AND checkpoint_cursor IS NULL AND next_resume_position IS NULL
      AND last_safe_response_summary IS NULL
      AND COALESCE(processed_item_count,0)=0
      AND COALESCE(request_count,0)=0
      AND COALESCE(report_total_rows,0)=0
      AND finished_at IS NULL AND is_deleted=b'0';"
}
dp_runtime_safe_dp10_ids() {
  dp_runtime_db_scalar "SELECT COALESCE(GROUP_CONCAT(id ORDER BY id SEPARATOR ','),'')
    FROM procurement_ali1688_order_sync_task
    WHERE status='running'
      AND COALESCE(processed_count,0)=0
      AND COALESCE(imported_count,0)=0
      AND COALESCE(failed_count,0)=0
      AND COALESCE(progress_percent,0)=0
      AND failure_code IS NULL AND failure_message IS NULL
      AND COALESCE(requires_manual_action,b'0')=b'0'
      AND finished_at IS NULL AND is_deleted=b'0';"
}
validate_dp_runtime_id_list() {
  [ -z "$1" ] || [[ "$1" =~ ^[0-9]+(,[0-9]+)*$ ]]
}
dp_runtime_id_list_count() {
  if [ -z "$1" ]; then printf 0; else awk -F, '{print NF}' <<<"$1"; fi
}
capture_dp_runtime_legacy_cohort() {
  DP_RUNTIME_NOON_SUPERSEDED_IDS="$(dp_runtime_safe_noon_ids)"
  DP_RUNTIME_DP10_SUPERSEDED_IDS="$(dp_runtime_safe_dp10_ids)"
  validate_dp_runtime_id_list "$DP_RUNTIME_NOON_SUPERSEDED_IDS"
  validate_dp_runtime_id_list "$DP_RUNTIME_DP10_SUPERSEDED_IDS"
  [ "$(dp_runtime_id_list_count "$DP_RUNTIME_NOON_SUPERSEDED_IDS")" = \
    "$DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT" ]
  [ "$(dp_runtime_id_list_count "$DP_RUNTIME_DP10_SUPERSEDED_IDS")" = \
    "$DP_RUNTIME_LEGACY_SAFE_DP10_COUNT" ]
}
verify_dp_runtime_legacy_cohort() {
  [ "$(dp_runtime_safe_noon_ids)" = "$DP_RUNTIME_NOON_SUPERSEDED_IDS" ]
  [ "$(dp_runtime_safe_dp10_ids)" = "$DP_RUNTIME_DP10_SUPERSEDED_IDS" ]
}
require_legacy_cutover_ready() {
  local row="" noon="" safe="" dp05="" dp10="" safe_dp10="" sales="" auth=""
  row="$(dp_runtime_legacy_counts)"
  IFS=$'\t' read -r noon safe dp05 dp10 safe_dp10 sales auth <<<"$row"
  [[ "$noon$safe$dp05$dp10$safe_dp10$sales$auth" =~ ^[0-9]+$ ]]
  if [ "$noon" != "$safe" ] || [ "$dp05" != 0 ] \
      || [ "$dp10" != "$safe_dp10" ] \
      || [ "$sales" != 0 ] || [ "$auth" != 0 ]; then
    return 1
  fi
  DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT="$safe"
  DP_RUNTIME_LEGACY_SAFE_DP10_COUNT="$safe_dp10"
}
require_legacy_cutover_empty() {
  local row="" noon="" safe="" dp05="" dp10="" safe_dp10="" sales="" auth=""
  row="$(dp_runtime_legacy_counts)"
  IFS=$'\t' read -r noon safe dp05 dp10 safe_dp10 sales auth <<<"$row"
  [[ "$noon$safe$dp05$dp10$safe_dp10$sales$auth" =~ ^[0-9]+$ ]]
  if [ "$noon" != 0 ] || [ "$safe" != 0 ] || [ "$dp05" != 0 ] \
      || [ "$dp10" != 0 ] || [ "$safe_dp10" != 0 ] \
      || [ "$sales" != 0 ] || [ "$auth" != 0 ]; then
    return 1
  fi
}
prepare_dp_runtime_cutover() {
  command -v mysql >/dev/null
  secure_file_operation verify "$DP_RUNTIME_MYSQL_CNF" 600 - >/dev/null
  prepare_dp_runtime_database_target
  [ "$(dp_runtime_db_scalar 'SELECT DATABASE();')" = "$DP_RUNTIME_DB_SCHEMA" ]
  capture_dp_runtime_schema_binding
  require_legacy_cutover_ready
  capture_dp_runtime_legacy_cohort
}
finalize_dp_runtime_legacy_cutover() {
  assert_no_backend_jvms
  require_legacy_cutover_ready
  verify_dp_runtime_legacy_cohort
  local changed="0" dp10_changed="0" result=""
  local noon_update="SET @noon_changed=0;"
  local dp10_update="SET @dp10_changed=0;"
  if [ -n "$DP_RUNTIME_NOON_SUPERSEDED_IDS" ]; then
    noon_update="UPDATE noon_pull_task SET
      status='CANCELLED', finished_at=NOW(), gmt_updated=NOW()
    WHERE id IN ($DP_RUNTIME_NOON_SUPERSEDED_IDS)
      AND status IN ('QUEUED','RUNNING','BLOCKED_AUTH')
      AND trigger_mode='SCHEDULED_DAILY'
      AND pull_type IN ('INTERFACE','REPORT')
      AND data_domain IN ('PRODUCT','SALES','ORDER','FINANCE_TRANSACTION',
        'NOON_ADVERTISING','OFFICIAL_WAREHOUSE_ASN',
        'OFFICIAL_WAREHOUSE_INVENTORY','OFFICIAL_WAREHOUSE_FBN_RECEIVED')
      AND ((status='BLOCKED_AUTH' AND retry_action='WAIT_FOR_AUTH')
        OR (status='QUEUED' AND started_at IS NULL)
        OR (status='RUNNING' AND started_at IS NOT NULL
          AND COALESCE(retry_action,'')<>'WAIT_FOR_AUTH'))
      AND checkpoint_cursor IS NULL AND next_resume_position IS NULL
      AND last_safe_response_summary IS NULL
      AND COALESCE(processed_item_count,0)=0 AND COALESCE(request_count,0)=0
      AND COALESCE(report_total_rows,0)=0 AND finished_at IS NULL
      AND is_deleted=b'0'; SET @noon_changed=ROW_COUNT();"
  fi
  if [ -n "$DP_RUNTIME_DP10_SUPERSEDED_IDS" ]; then
    dp10_update="UPDATE procurement_ali1688_order_sync_task SET
        status='cancelled', finished_at=NOW(), gmt_updated=NOW()
      WHERE id IN ($DP_RUNTIME_DP10_SUPERSEDED_IDS)
        AND status='running'
        AND COALESCE(processed_count,0)=0
        AND COALESCE(imported_count,0)=0
        AND COALESCE(failed_count,0)=0
        AND COALESCE(progress_percent,0)=0
        AND failure_code IS NULL AND failure_message IS NULL
        AND COALESCE(requires_manual_action,b'0')=b'0'
        AND finished_at IS NULL AND is_deleted=b'0';
      SET @dp10_changed=ROW_COUNT();"
  fi
  result="$(dp_runtime_db_scalar "START TRANSACTION;
    $noon_update
    $dp10_update
    SELECT CONCAT_WS(CHAR(9),@noon_changed,@dp10_changed);
    COMMIT;")"
  IFS=$'\t' read -r changed dp10_changed <<<"$result"
  [[ "$changed" =~ ^[0-9]+$ ]]
  [[ "$dp10_changed" =~ ^[0-9]+$ ]]
  [ "$changed" = "$DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT" ]
  [ "$dp10_changed" = "$DP_RUNTIME_LEGACY_SAFE_DP10_COUNT" ]
  DP_RUNTIME_LEGACY_SUPERSEDED_COUNT="$((changed + dp10_changed))"
  require_legacy_cutover_empty
  DP_RUNTIME_LEGACY_REMAINING_AFTER_STOP=0
  verify_dp_runtime_schema_binding
}
rollback_dp_runtime_legacy_cohort() {
  local changed="0" expected="0"
  if [ -n "$DP_RUNTIME_NOON_SUPERSEDED_IDS" ]; then
    expected="$(dp_runtime_db_scalar "SELECT COUNT(*) FROM noon_pull_task
      WHERE id IN ($DP_RUNTIME_NOON_SUPERSEDED_IDS) AND status='CANCELLED';")"
    [[ "$expected" =~ ^[0-9]+$ ]]
    [ "$expected" = 0 ] || [ "$expected" = "$DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT" ]
    if [ "$expected" != 0 ]; then
      changed="$(dp_runtime_db_scalar "UPDATE noon_pull_task SET
        status=CASE WHEN retry_action='WAIT_FOR_AUTH' THEN 'BLOCKED_AUTH'
          WHEN started_at IS NULL THEN 'QUEUED' ELSE 'RUNNING' END,
        finished_at=NULL, gmt_updated=NOW()
        WHERE id IN ($DP_RUNTIME_NOON_SUPERSEDED_IDS)
          AND status='CANCELLED'; SELECT ROW_COUNT();")"
      [ "$changed" = "$expected" ]
    fi
  fi
  if [ -n "$DP_RUNTIME_DP10_SUPERSEDED_IDS" ]; then
    expected="$(dp_runtime_db_scalar "SELECT COUNT(*)
      FROM procurement_ali1688_order_sync_task
      WHERE id IN ($DP_RUNTIME_DP10_SUPERSEDED_IDS) AND status='cancelled';")"
    [[ "$expected" =~ ^[0-9]+$ ]]
    [ "$expected" = 0 ] || [ "$expected" = "$DP_RUNTIME_LEGACY_SAFE_DP10_COUNT" ]
    if [ "$expected" != 0 ]; then
      changed="$(dp_runtime_db_scalar "
        UPDATE procurement_ali1688_order_sync_task SET
          status='running', finished_at=NULL, gmt_updated=NOW()
        WHERE id IN ($DP_RUNTIME_DP10_SUPERSEDED_IDS)
          AND status='cancelled'; SELECT ROW_COUNT();")"
      [ "$changed" = "$expected" ]
    fi
  fi
  require_legacy_cutover_ready
}
'''


__all__ = ["build_dp_runtime_legacy_drain_shell"]
