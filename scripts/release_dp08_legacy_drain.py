#!/usr/bin/env python3
"""Exact reversible drain of active scheduled Legacy DP08 task/run pairs."""
from __future__ import annotations


def build_dp08_legacy_drain_shell() -> str:
    return r'''
DP08_LEGACY_QUEUED_TASK_IDS=""
DP08_LEGACY_RUNNING_TASK_IDS=""
DP08_LEGACY_QUEUED_RUN_IDS=""
DP08_LEGACY_RUNNING_RUN_IDS=""
DP08_LEGACY_SUPERSEDED_COUNT=0

dp08_legacy_pair_ids() {
  local record="$1" status="$2" column="t.id"
  [ "$record" = TASK ] || [ "$record" = RUN ]
  [ "$status" = QUEUED ] || [ "$status" = RUNNING ]
  [ "$record" = TASK ] || column="r.id"
  dp_runtime_db_scalar "SET SESSION group_concat_max_len=4194304;
    SELECT COALESCE(GROUP_CONCAT($column ORDER BY $column SEPARATOR ','),'')
    FROM operational_task t
    JOIN operations_competitor_search_run r
      ON r.task_id=t.id AND r.status=t.status AND r.is_deleted=b'0'
    WHERE t.task_type='OPERATIONS_COMPETITOR_REFRESH'
      AND t.status='$status' AND t.is_deleted=b'0'
      AND t.error_code IS NULL AND t.finished_at IS NULL
      AND r.error_code IS NULL AND r.error_message IS NULL AND r.finished_at IS NULL
      AND JSON_UNQUOTE(JSON_EXTRACT(t.payload_json,'$.triggerMode'))
        IN ('SCHEDULED_RANK_MONITOR','SCHEDULED_DETAIL_MONITOR')
      AND r.trigger_mode=JSON_UNQUOTE(JSON_EXTRACT(t.payload_json,'$.triggerMode'))
      AND JSON_TYPE(JSON_EXTRACT(t.payload_json,'$.watchProductId'))='INTEGER'
      AND r.watch_product_id=CAST(JSON_UNQUOTE(
        JSON_EXTRACT(t.payload_json,'$.watchProductId')) AS UNSIGNED)
      AND JSON_UNQUOTE(JSON_EXTRACT(t.payload_json,'$.executionMode'))=
        CASE r.trigger_mode WHEN 'SCHEDULED_RANK_MONITOR' THEN 'rank' ELSE 'detail' END
      AND JSON_EXTRACT(t.payload_json,'$.rankRefresh')=
        (r.trigger_mode='SCHEDULED_RANK_MONITOR')
      AND JSON_EXTRACT(t.payload_json,'$.detailRefresh')=
        (r.trigger_mode='SCHEDULED_DETAIL_MONITOR')
      AND (t.natural_key=CONCAT('watchProduct:',r.watch_product_id,
          CASE r.trigger_mode WHEN 'SCHEDULED_RANK_MONITOR' THEN ':rank' ELSE ':detail' END)
        OR (r.trigger_mode='SCHEDULED_DETAIL_MONITOR'
          AND JSON_TYPE(JSON_EXTRACT(t.payload_json,'$.batchKey'))='STRING'
          AND CHAR_LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(t.payload_json,'$.batchKey'))))>0
          AND t.natural_key=CONCAT('watchProduct:',r.watch_product_id,':detail:',
            TRIM(JSON_UNQUOTE(JSON_EXTRACT(t.payload_json,'$.batchKey'))))));"
}
dp08_legacy_active_counts() {
  dp_runtime_db_scalar "SELECT CONCAT_WS(CHAR(9),
    (SELECT COUNT(*) FROM operational_task t
      WHERE t.task_type IN ('OPERATIONS_COMPETITOR_REFRESH',
        'OPERATIONS_COMPETITOR_MONITORING','OPERATIONS_COMPETITOR_MONITORING_CYCLE')
        AND t.status IN ('QUEUED','RUNNING') AND t.is_deleted=b'0'
        AND JSON_UNQUOTE(JSON_EXTRACT(t.payload_json,'$.triggerMode'))
          IN ('SCHEDULED_RANK_MONITOR','SCHEDULED_DETAIL_MONITOR')),
    (SELECT COUNT(*) FROM operations_competitor_search_run r
      WHERE r.status IN ('QUEUED','RUNNING') AND r.is_deleted=b'0'
        AND r.trigger_mode IN ('SCHEDULED_RANK_MONITOR','SCHEDULED_DETAIL_MONITOR')));"
}
capture_dp08_legacy_cohort() {
  local row="" tasks="" runs="" safe_tasks="" safe_runs=""
  DP08_LEGACY_QUEUED_TASK_IDS="$(dp08_legacy_pair_ids TASK QUEUED)"
  DP08_LEGACY_RUNNING_TASK_IDS="$(dp08_legacy_pair_ids TASK RUNNING)"
  DP08_LEGACY_QUEUED_RUN_IDS="$(dp08_legacy_pair_ids RUN QUEUED)"
  DP08_LEGACY_RUNNING_RUN_IDS="$(dp08_legacy_pair_ids RUN RUNNING)"
  validate_dp_runtime_id_list "$DP08_LEGACY_QUEUED_TASK_IDS"
  validate_dp_runtime_id_list "$DP08_LEGACY_RUNNING_TASK_IDS"
  validate_dp_runtime_id_list "$DP08_LEGACY_QUEUED_RUN_IDS"
  validate_dp_runtime_id_list "$DP08_LEGACY_RUNNING_RUN_IDS"
  row="$(dp08_legacy_active_counts)"
  IFS=$'\t' read -r tasks runs <<<"$row"
  [[ "$tasks$runs" =~ ^[0-9]+$ ]]
  safe_tasks="$(($(dp_runtime_id_list_count "$DP08_LEGACY_QUEUED_TASK_IDS")
    + $(dp_runtime_id_list_count "$DP08_LEGACY_RUNNING_TASK_IDS")))"
  safe_runs="$(($(dp_runtime_id_list_count "$DP08_LEGACY_QUEUED_RUN_IDS")
    + $(dp_runtime_id_list_count "$DP08_LEGACY_RUNNING_RUN_IDS")))"
  [ "$tasks" = "$safe_tasks" ]
  [ "$runs" = "$safe_runs" ]
  [ "$safe_tasks" = "$safe_runs" ]
}
verify_dp08_legacy_cohort() {
  [ "$(dp08_legacy_pair_ids TASK QUEUED)" = "$DP08_LEGACY_QUEUED_TASK_IDS" ]
  [ "$(dp08_legacy_pair_ids TASK RUNNING)" = "$DP08_LEGACY_RUNNING_TASK_IDS" ]
  [ "$(dp08_legacy_pair_ids RUN QUEUED)" = "$DP08_LEGACY_QUEUED_RUN_IDS" ]
  [ "$(dp08_legacy_pair_ids RUN RUNNING)" = "$DP08_LEGACY_RUNNING_RUN_IDS" ]
}
dp08_legacy_terminal_update() {
  local table="$1" ids="$2" source="$3" set_clause="$4"
  if [ -z "$ids" ]; then printf '0'; return; fi
  dp_runtime_db_scalar "UPDATE $table SET $set_clause
    WHERE id IN ($ids) AND status='$source' AND is_deleted=b'0';
    SELECT ROW_COUNT();"
}
finalize_dp08_legacy_cutover() {
  assert_no_backend_jvms
  capture_dp08_legacy_cohort
  verify_dp08_legacy_cohort
  local qt="" rt="" qr="" rr="" expected=""
  qr="$(dp08_legacy_terminal_update operations_competitor_search_run \
    "$DP08_LEGACY_QUEUED_RUN_IDS" QUEUED \
    "status='FAILED',finished_at=NOW(),error_code='DP_RUNTIME_SUPERSEDED',error_message='legacy DP08 scheduled execution superseded by managed runtime cutover',gmt_updated=NOW()")"
  rr="$(dp08_legacy_terminal_update operations_competitor_search_run \
    "$DP08_LEGACY_RUNNING_RUN_IDS" RUNNING \
    "status='FAILED',finished_at=NOW(),error_code='DP_RUNTIME_SUPERSEDED',error_message='legacy DP08 scheduled execution superseded by managed runtime cutover',gmt_updated=NOW()")"
  qt="$(dp08_legacy_terminal_update operational_task \
    "$DP08_LEGACY_QUEUED_TASK_IDS" QUEUED \
    "status='CANCELLED',finished_at=NOW(),error_code='DP_RUNTIME_SUPERSEDED',gmt_updated=NOW()")"
  rt="$(dp08_legacy_terminal_update operational_task \
    "$DP08_LEGACY_RUNNING_TASK_IDS" RUNNING \
    "status='CANCELLED',finished_at=NOW(),error_code='DP_RUNTIME_SUPERSEDED',gmt_updated=NOW()")"
  [ "$qt" = "$(dp_runtime_id_list_count "$DP08_LEGACY_QUEUED_TASK_IDS")" ]
  [ "$rt" = "$(dp_runtime_id_list_count "$DP08_LEGACY_RUNNING_TASK_IDS")" ]
  [ "$qr" = "$(dp_runtime_id_list_count "$DP08_LEGACY_QUEUED_RUN_IDS")" ]
  [ "$rr" = "$(dp_runtime_id_list_count "$DP08_LEGACY_RUNNING_RUN_IDS")" ]
  expected="$((qt + rt))"
  [ "$expected" = "$((qr + rr))" ]
  DP08_LEGACY_SUPERSEDED_COUNT="$expected"
  [ "$(dp08_legacy_active_counts)" = $'0\t0' ]
}
rollback_dp08_legacy_cohort() {
  local changed="" expected=""
  for spec in \
    "operational_task|$DP08_LEGACY_QUEUED_TASK_IDS|QUEUED|" \
    "operational_task|$DP08_LEGACY_RUNNING_TASK_IDS|RUNNING|" \
    "operations_competitor_search_run|$DP08_LEGACY_QUEUED_RUN_IDS|QUEUED|error_message=NULL," \
    "operations_competitor_search_run|$DP08_LEGACY_RUNNING_RUN_IDS|RUNNING|error_message=NULL,"; do
    IFS='|' read -r table ids original extra_restore <<<"$spec"
    [ -n "$ids" ] || continue
    expected="$(dp_runtime_db_scalar "SELECT COUNT(*) FROM $table
      WHERE id IN ($ids) AND status IN ('FAILED','CANCELLED')
        AND error_code='DP_RUNTIME_SUPERSEDED';")"
    [ "$expected" = 0 ] || [ "$expected" = "$(dp_runtime_id_list_count "$ids")" ]
    if [ "$expected" != 0 ]; then
      changed="$(dp_runtime_db_scalar "UPDATE $table SET status='$original',
        finished_at=NULL,error_code=NULL,$extra_restore gmt_updated=NOW()
        WHERE id IN ($ids) AND status IN ('FAILED','CANCELLED')
          AND error_code='DP_RUNTIME_SUPERSEDED'; SELECT ROW_COUNT();")"
      [ "$changed" = "$expected" ]
    fi
  done
  capture_dp08_legacy_cohort
}
'''


__all__ = ["build_dp08_legacy_drain_shell"]
