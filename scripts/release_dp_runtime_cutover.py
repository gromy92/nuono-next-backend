#!/usr/bin/env python3
"""Fail-closed database and legacy-writer fence for the managed DP cutover."""
from __future__ import annotations


def build_dp_runtime_cutover_shell() -> str:
    return r'''
DP_RUNTIME_MYSQL_CNF="$APP_DIR/.migration.cnf"
DP_RUNTIME_SCHEMA_BINDING_SHA256=""
DP_RUNTIME_CUTOVER_BINDING_SHA256=""
DP_RUNTIME_CUTOVER_OPERATION_COUNT=""
DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT=""
DP_RUNTIME_LEGACY_SUPERSEDED_COUNT=0
DP_RUNTIME_LEGACY_REMAINING_AFTER_STOP=""

dp_runtime_db_scalar() {
  mysql --defaults-extra-file="$DP_RUNTIME_MYSQL_CNF" \
    --connect-timeout=10 --batch --skip-column-names --raw -e "$1"
}
dp_runtime_expected_schema() {
  python3 - "$APP_DIR/.env" <<'PY'
from pathlib import Path
from urllib.parse import urlsplit
import sys
values = []
for raw in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    if key.strip() != "NUONO_NEXT_DB_URL":
        continue
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
        value = value[1:-1]
    values.append(value)
if len(values) != 1 or not values[0].startswith("jdbc:mysql://"):
    raise SystemExit("managed DP database URL is unavailable or ambiguous")
parsed = urlsplit(values[0].removeprefix("jdbc:"))
schema = parsed.path.lstrip("/")
if not parsed.hostname or not schema or "/" in schema:
    raise SystemExit("managed DP database target is invalid")
print(schema)
PY
}
dp_runtime_database_binding() {
  dp_runtime_db_scalar "
    WITH required_migration AS (
      SELECT '243_dp_pull_runtime.sql' migration_key UNION ALL
      SELECT '244_dp_pull_report_bounded_apply.sql' UNION ALL
      SELECT '245_dp_pull_snapshot_bounded_apply.sql' UNION ALL
      SELECT '246_dp_pull_advertising_generation.sql' UNION ALL
      SELECT '247_dp_pull_schedule_core.sql' UNION ALL
      SELECT '248_dp_pull_dp08_member_retention.sql'
    ), schema_binding AS (
      SELECT COUNT(*) AS migration_count,
        SHA2(GROUP_CONCAT(SHA2(CONCAT_WS('|', h.migration_key,
          h.checksum_sha256, h.postcheck_sha256, h.state, h.attempt_no,
          a.checksum_sha256, a.postcheck_sha256, a.state, a.attempt_no), 256)
          ORDER BY BINARY h.migration_key SEPARATOR ''), 256) AS binding_sha256
      FROM required_migration required
      JOIN nuono_schema_migration h ON h.migration_key=required.migration_key
      JOIN nuono_schema_migration_attempt a
        ON a.migration_key=h.migration_key AND a.attempt_no=h.attempt_no
      WHERE h.state='APPLIED' AND a.state='APPLIED'
    )
    SELECT CONCAT_WS(CHAR(9),
      schema_binding.binding_sha256,
      cutover.binding_sha256, cutover.operation_count)
    FROM schema_binding
    CROSS JOIN (
      SELECT COUNT(*) AS operation_count,
        SHA2(GROUP_CONCAT(SHA2(CONCAT_WS('|', operation_code,
          HEX(cutover_key), expected_scope_count, anchor_manifest_sha256,
          DATE_FORMAT(activated_at_utc, '%Y-%m-%dT%H:%i:%s.%f')), 256)
          ORDER BY BINARY operation_code SEPARATOR ''), 256) AS binding_sha256
      FROM dp_pull_schedule_cutover WHERE state = 'ACTIVE'
    ) cutover
    WHERE schema_binding.migration_count=6;"
}
dp_runtime_schema_binding() {
  dp_runtime_db_scalar "
    WITH required_migration AS (
      SELECT '243_dp_pull_runtime.sql' migration_key UNION ALL
      SELECT '244_dp_pull_report_bounded_apply.sql' UNION ALL
      SELECT '245_dp_pull_snapshot_bounded_apply.sql' UNION ALL
      SELECT '246_dp_pull_advertising_generation.sql' UNION ALL
      SELECT '247_dp_pull_schedule_core.sql' UNION ALL
      SELECT '248_dp_pull_dp08_member_retention.sql'
    ), schema_binding AS (
      SELECT COUNT(*) AS migration_count,
        SHA2(GROUP_CONCAT(SHA2(CONCAT_WS('|', h.migration_key,
          h.checksum_sha256, h.postcheck_sha256, h.state, h.attempt_no,
          a.checksum_sha256, a.postcheck_sha256, a.state, a.attempt_no), 256)
          ORDER BY BINARY h.migration_key SEPARATOR ''), 256) AS binding_sha256
      FROM required_migration required
      JOIN nuono_schema_migration h ON h.migration_key=required.migration_key
      JOIN nuono_schema_migration_attempt a
        ON a.migration_key=h.migration_key AND a.attempt_no=h.attempt_no
      WHERE h.state='APPLIED' AND a.state='APPLIED'
    )
    SELECT binding_sha256 FROM schema_binding WHERE migration_count=6;"
}
dp_runtime_legacy_counts() {
  dp_runtime_db_scalar "
    SELECT CONCAT_WS(CHAR(9),
      (SELECT COUNT(*) FROM noon_pull_task
       WHERE status IN ('QUEUED','RUNNING','BLOCKED_AUTH') AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM noon_pull_task
       WHERE status='QUEUED' AND trigger_mode='SCHEDULED_DAILY'
         AND pull_type='INTERFACE'
         AND ((data_domain='PRODUCT' AND target_identity LIKE 'product-list:%')
           OR (data_domain='OFFICIAL_WAREHOUSE_INVENTORY'
             AND target_identity LIKE 'official-warehouse-fbn-inventory:%'))
         AND started_at IS NULL AND locked_by IS NULL
         AND source_batch_id IS NULL AND auth_recovery_id IS NULL
         AND checkpoint_cursor IS NULL AND next_resume_position IS NULL
         AND last_safe_response_summary IS NULL
         AND report_export_id IS NULL AND report_download_url IS NULL
         AND report_export_status IS NULL AND report_total_rows IS NULL
         AND report_last_poll_at IS NULL AND report_next_poll_at IS NULL
         AND COALESCE(processed_item_count,0)=0
         AND COALESCE(request_count,0)=0
         AND COALESCE(report_poll_attempts,0)=0
         AND failure_type IS NULL AND retry_action IS NULL
         AND retryable IS NULL AND requires_manual_action IS NULL
         AND diagnostic_summary IS NULL
         AND finished_at IS NULL AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM operational_task
       WHERE task_type='PRODUCT_PUBLIC_DETAIL_SYNC'
         AND status IN ('QUEUED','RUNNING') AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM procurement_ali1688_order_sync_task
       WHERE (status='running' OR (status IN ('failed','partial_success')
         AND COALESCE(retryable,b'1')=b'1')) AND is_deleted=b'0'),
      (SELECT COUNT(*) FROM sales_sync_task
       WHERE status IN ('queued','running','waiting_authorization')),
      (SELECT COUNT(*) FROM noon_auth_identity_recovery_item
       WHERE status IN ('PENDING','VALIDATING') AND source_task_id IS NOT NULL
         AND (source_domain IS NULL OR UPPER(source_domain) IN (
           'NOON_PULL','PRODUCT','SALES','SALES_SYNC','ORDER',
           'FINANCE_TRANSACTION','NOON_ADVERTISING','OFFICIAL_WAREHOUSE_ASN',
           'OFFICIAL_WAREHOUSE_INVENTORY','OFFICIAL_WAREHOUSE_FBN_RECEIVED'))));"
}
capture_dp_runtime_schema_binding() {
  DP_RUNTIME_SCHEMA_BINDING_SHA256="$(dp_runtime_schema_binding)"
  [[ "$DP_RUNTIME_SCHEMA_BINDING_SHA256" =~ ^[0-9a-f]{64}$ ]]
}
verify_dp_runtime_schema_binding() {
  [ "$(dp_runtime_schema_binding)" = "$DP_RUNTIME_SCHEMA_BINDING_SHA256" ]
}
capture_dp_runtime_database_binding() {
  local row=""
  row="$(dp_runtime_database_binding)"
  IFS=$'\t' read -r DP_RUNTIME_SCHEMA_BINDING_SHA256 \
    DP_RUNTIME_CUTOVER_BINDING_SHA256 DP_RUNTIME_CUTOVER_OPERATION_COUNT <<<"$row"
  [[ "$DP_RUNTIME_SCHEMA_BINDING_SHA256" =~ ^[0-9a-f]{64}$ ]]
  [[ "$DP_RUNTIME_CUTOVER_BINDING_SHA256" =~ ^[0-9a-f]{64}$ ]]
  [ "$DP_RUNTIME_CUTOVER_OPERATION_COUNT" = 11 ]
}
verify_dp_runtime_database_binding() {
  local row="" schema="" cutover="" count=""
  row="$(dp_runtime_database_binding)"
  IFS=$'\t' read -r schema cutover count <<<"$row"
  [ "$schema" = "$DP_RUNTIME_SCHEMA_BINDING_SHA256" ]
  [ "$cutover" = "$DP_RUNTIME_CUTOVER_BINDING_SHA256" ]
  [ "$count" = "$DP_RUNTIME_CUTOVER_OPERATION_COUNT" ]
}
require_legacy_cutover_ready() {
  local row="" noon="" safe="" dp05="" dp10="" sales="" auth=""
  row="$(dp_runtime_legacy_counts)"
  IFS=$'\t' read -r noon safe dp05 dp10 sales auth <<<"$row"
  [[ "$noon$safe$dp05$dp10$sales$auth" =~ ^[0-9]+$ ]]
  if [ "$noon" != "$safe" ] || [ "$dp05" != 0 ] || [ "$dp10" != 0 ] \
      || [ "$sales" != 0 ] || [ "$auth" != 0 ]; then
    return 1
  fi
  DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT="$safe"
}
require_legacy_cutover_empty() {
  local row="" noon="" safe="" dp05="" dp10="" sales="" auth=""
  row="$(dp_runtime_legacy_counts)"
  IFS=$'\t' read -r noon safe dp05 dp10 sales auth <<<"$row"
  [[ "$noon$safe$dp05$dp10$sales$auth" =~ ^[0-9]+$ ]]
  if [ "$noon" != 0 ] || [ "$safe" != 0 ] || [ "$dp05" != 0 ] \
      || [ "$dp10" != 0 ] || [ "$sales" != 0 ] || [ "$auth" != 0 ]; then
    return 1
  fi
}
prepare_dp_runtime_cutover() {
  command -v mysql >/dev/null
  secure_file_operation verify "$DP_RUNTIME_MYSQL_CNF" 600 - >/dev/null
  [ "$(dp_runtime_db_scalar 'SELECT DATABASE();')" = "$(dp_runtime_expected_schema)" ]
  capture_dp_runtime_schema_binding
  require_legacy_cutover_ready
}
finalize_dp_runtime_legacy_cutover() {
  assert_no_backend_jvms
  require_legacy_cutover_ready
  local changed=""
  changed="$(dp_runtime_db_scalar "
    UPDATE noon_pull_task SET
      status='CANCELLED', failure_type='runtime_cutover_superseded',
      retry_action='NONE', retryable=b'0', requires_manual_action=b'0',
      diagnostic_summary='Managed DP runtime cutover $EXPECTED_COMMIT',
      finished_at=NOW(), gmt_updated=NOW()
    WHERE status='QUEUED' AND trigger_mode='SCHEDULED_DAILY'
      AND pull_type='INTERFACE'
      AND ((data_domain='PRODUCT' AND target_identity LIKE 'product-list:%')
        OR (data_domain='OFFICIAL_WAREHOUSE_INVENTORY'
          AND target_identity LIKE 'official-warehouse-fbn-inventory:%'))
      AND started_at IS NULL AND locked_by IS NULL
      AND source_batch_id IS NULL AND auth_recovery_id IS NULL
      AND checkpoint_cursor IS NULL AND next_resume_position IS NULL
      AND last_safe_response_summary IS NULL
      AND report_export_id IS NULL AND report_download_url IS NULL
      AND report_export_status IS NULL AND report_total_rows IS NULL
      AND report_last_poll_at IS NULL AND report_next_poll_at IS NULL
      AND COALESCE(processed_item_count,0)=0 AND COALESCE(request_count,0)=0
      AND COALESCE(report_poll_attempts,0)=0
      AND failure_type IS NULL AND retry_action IS NULL
      AND retryable IS NULL AND requires_manual_action IS NULL
      AND diagnostic_summary IS NULL AND finished_at IS NULL
      AND is_deleted=b'0'; SELECT ROW_COUNT();")"
  [[ "$changed" =~ ^[0-9]+$ ]]
  [ "$changed" = "$DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT" ]
  DP_RUNTIME_LEGACY_SUPERSEDED_COUNT="$changed"
  require_legacy_cutover_empty
  DP_RUNTIME_LEGACY_REMAINING_AFTER_STOP=0
  verify_dp_runtime_schema_binding
}
'''


__all__ = ["build_dp_runtime_cutover_shell"]
