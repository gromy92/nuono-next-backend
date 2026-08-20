#!/usr/bin/env python3
"""Transactional manifest-to-runtime bootstrap and repair-forward rollback fence."""
from __future__ import annotations


def build_dp_runtime_bootstrap_shell() -> str:
    return r'''
DP_RUNTIME_BOOTSTRAP_SQL="$DP_RUNTIME_MANIFEST_DIR/bootstrap.sql"
DP_RUNTIME_BOOTSTRAP_SQL_SHA256=""
DP_RUNTIME_EXPECTED_ADMISSION_COUNT=""
DP_RUNTIME_EXPECTED_BINDING_COUNT=""
DP_RUNTIME_EXPECTED_ANCHOR_COUNT=""
DP_RUNTIME_BOOTSTRAPPED=0

write_dp_runtime_bootstrap_sql() {
  python3 - "$DP_RUNTIME_RECHECK_MANIFEST" "$DP_RUNTIME_BOOTSTRAP_SQL" <<'PY'
import json, os, pathlib, sys
source, target = map(pathlib.Path, sys.argv[1:])
root = json.loads(source.read_text(encoding="utf-8"))
cutover = root["cutoverKey"]
observed = root["sourceObservedAtUtc"]
def text(value):
    if not isinstance(value, str) or not value or "\x00" in value:
        raise SystemExit("invalid manifest text")
    return "CONVERT(X'%s' USING utf8mb4)" % value.encode().hex()
def nullable(value):
    return "NULL" if value is None else text(value)
def number(value):
    if not isinstance(value, int) or value <= 0:
        raise SystemExit("invalid manifest number")
    return str(value)
def moment(value):
    if not isinstance(value, str) or not value.endswith("Z"):
        raise SystemExit("invalid manifest UTC time")
    candidate = value[:-1]
    if len(candidate) != 23 or candidate[10] != "T":
        raise SystemExit("manifest time must fit DATETIME(3)")
    return "'%s'" % candidate.replace("T", " ")
def binary_equal(table, fields):
    return " AND ".join(
        f"(BINARY {table}.{field} <=> BINARY incoming.{field})" for field in fields
    )
admissions, bindings, anchors = {}, [], []
operations = root["operations"]
for operation in operations:
    code = operation["operationCode"]
    for scope in operation["scopes"]:
        previous = admissions.setdefault(scope["scopeKey"], scope)
        if previous != scope:
            shared = {key for key in scope if key not in {
                "reconcileAfterUtc", "boundaryKind", "boundaryEvidenceSha256",
                "anchorEvidenceSha256", "binding",
            }}
            if any(previous[key] != scope[key] for key in shared):
                raise SystemExit("shared admission identity drift")
        anchors.append((code, scope, operation))
        if scope["binding"] is not None:
            bindings.append((code, scope["scopeKey"], scope["binding"]))
lines = ["SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;", "START TRANSACTION;"]
lines.append("UPDATE dp_pull_task SET id = NULL WHERE id = (SELECT id FROM (SELECT id FROM dp_pull_task LIMIT 1) guarded);")
for scope in sorted(admissions.values(), key=lambda item: item["scopeKey"]):
    columns = (
        "scope_key", "scope_namespace", "owner_user_id", "logical_store_id",
        "account_key", "egress_key", "project_code", "store_code", "site_code",
        "admission_kind", "first_eligible_at_utc", "source_binding_sha256",
        "cutover_key", "gmt_create",
    )
    values = (
        text(scope["scopeKey"]), text(scope["scopeNamespace"]),
        number(scope["ownerUserId"]),
        "NULL" if scope["logicalStoreId"] is None else number(scope["logicalStoreId"]),
        text(scope["accountKey"]), nullable(scope["egressKey"]),
        nullable(scope["projectCode"]), nullable(scope["storeCode"]),
        nullable(scope["siteCode"]), "'CUTOVER_EXISTING'", "NULL",
        text(scope["sourceBindingSha256"]), text(cutover), "UTC_TIMESTAMP(3)",
    )
    exact = binary_equal("dp_pull_scope_admission", columns[:-1])
    lines.append(
        f"INSERT INTO dp_pull_scope_admission ({','.join(columns)}) VALUES "
        f"({','.join(values)}) AS incoming ON DUPLICATE KEY UPDATE "
        f"scope_key=IF({exact},dp_pull_scope_admission.scope_key,NULL);"
    )
for code, scope_key, binding in sorted(bindings):
    columns = (
        "binding_id", "operation_code", "scope_key", "payload_type", "payload_sha256",
        "payload", "effective_from_utc", "effective_until_utc", "source_observed_at_utc",
        "open_scope_slot", "gmt_create", "gmt_updated",
    )
    values = (
        text(binding["bindingId"]), text(code), text(scope_key),
        text(binding["payloadType"]), text(binding["payloadSha256"]),
        text(binding["payload"]), moment(binding["effectiveFromUtc"]), "NULL",
        moment(observed), text(code + ":" + scope_key),
        "UTC_TIMESTAMP(3)", "UTC_TIMESTAMP(3)",
    )
    exact = binary_equal("dp_pull_scope_binding_epoch", columns[:8] + ("open_scope_slot",))
    lines.append(
        f"INSERT INTO dp_pull_scope_binding_epoch ({','.join(columns)}) VALUES "
        f"({','.join(values)}) AS incoming ON DUPLICATE KEY UPDATE "
        f"binding_id=IF({exact},dp_pull_scope_binding_epoch.binding_id,NULL);"
    )
for operation in operations:
    columns = (
        "operation_code", "cutover_key", "state", "expected_scope_count",
        "anchor_manifest_sha256", "activated_at_utc", "version_no", "gmt_create", "gmt_updated",
    )
    values = (
        text(operation["operationCode"]), text(cutover), "'ACTIVE'",
        str(operation["expectedScopeCount"]), text(operation["anchorManifestSha256"]),
        "UTC_TIMESTAMP(3)", "0", "UTC_TIMESTAMP(3)", "UTC_TIMESTAMP(3)",
    )
    exact = binary_equal("dp_pull_schedule_cutover", columns[:5])
    lines.append(
        f"INSERT INTO dp_pull_schedule_cutover ({','.join(columns)}) VALUES "
        f"({','.join(values)}) AS incoming ON DUPLICATE KEY UPDATE "
        f"operation_code=IF({exact},dp_pull_schedule_cutover.operation_code,NULL);"
    )
for code, scope, _operation in sorted(anchors, key=lambda item: (item[0], item[1]["scopeKey"])):
    columns = (
        "operation_code", "scope_key", "cutover_key", "anchor_kind",
        "reconcile_after_utc", "anchor_evidence_sha256", "gmt_create",
    )
    values = (
        text(code), text(scope["scopeKey"]), text(cutover), "'CUTOVER_RECONCILED'",
        moment(scope["reconcileAfterUtc"]), text(scope["anchorEvidenceSha256"]),
        "UTC_TIMESTAMP(3)",
    )
    exact = binary_equal("dp_pull_schedule_anchor", columns[:-1])
    lines.append(
        f"INSERT INTO dp_pull_schedule_anchor ({','.join(columns)}) VALUES "
        f"({','.join(values)}) AS incoming ON DUPLICATE KEY UPDATE "
        f"operation_code=IF({exact},dp_pull_schedule_anchor.operation_code,NULL);"
    )
lines.append("COMMIT;")
payload = ("\n".join(lines) + "\n").encode()
flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
if hasattr(os, "O_NOFOLLOW"): flags |= os.O_NOFOLLOW
descriptor = os.open(target, flags, 0o600)
with os.fdopen(descriptor, "wb") as output: output.write(payload)
print(len(admissions), len(bindings), len(anchors), sep="\t")
PY
}
dp_runtime_bootstrap_counts() {
  dp_runtime_db_scalar "SELECT CONCAT_WS(CHAR(9),
    (SELECT COUNT(*) FROM dp_pull_scope_admission),
    (SELECT COUNT(*) FROM dp_pull_scope_binding_epoch),
    (SELECT COUNT(*) FROM dp_pull_schedule_anchor),
    (SELECT COUNT(*) FROM dp_pull_schedule_cutover));"
}
bootstrap_dp_runtime_cutover() {
  assert_no_backend_jvms
  require_legacy_cutover_empty
  local expected="" actual="" cutovers=""
  expected="$(write_dp_runtime_bootstrap_sql)"
  IFS=$'\t' read -r DP_RUNTIME_EXPECTED_ADMISSION_COUNT \
    DP_RUNTIME_EXPECTED_BINDING_COUNT DP_RUNTIME_EXPECTED_ANCHOR_COUNT <<<"$expected"
  [[ "$DP_RUNTIME_EXPECTED_ADMISSION_COUNT$DP_RUNTIME_EXPECTED_BINDING_COUNT$DP_RUNTIME_EXPECTED_ANCHOR_COUNT" =~ ^[0-9]+$ ]]
  DP_RUNTIME_BOOTSTRAP_SQL_SHA256="$(secure_file_operation verify \
    "$DP_RUNTIME_BOOTSTRAP_SQL" 600 -)"
  dp_runtime_mysql --batch --raw < "$DP_RUNTIME_BOOTSTRAP_SQL"
  # The transaction has committed. Arm repair-forward rollback before any
  # attestation or count postcheck can fail and leave managed rows behind.
  DP_RUNTIME_BOOTSTRAPPED=1
  capture_dp_runtime_database_binding
  actual="$(dp_runtime_bootstrap_counts)"
  IFS=$'\t' read -r admissions bindings anchors cutovers <<<"$actual"
  [ "$admissions" = "$DP_RUNTIME_EXPECTED_ADMISSION_COUNT" ]
  [ "$bindings" = "$DP_RUNTIME_EXPECTED_BINDING_COUNT" ]
  [ "$anchors" = "$DP_RUNTIME_EXPECTED_ANCHOR_COUNT" ]
  [ "$cutovers" = 11 ]
}
dp_runtime_new_work_count() {
  dp_runtime_db_scalar "SELECT
    (SELECT COUNT(*) FROM dp_pull_task) +
    (SELECT COUNT(*) FROM dp_pull_scope_progress) +
    (SELECT COUNT(*) FROM dp_pull_snapshot_stage) +
    (SELECT COUNT(*) FROM dp_pull_snapshot_apply) +
    (SELECT COUNT(*) FROM dp_pull_dp10_stage_page) +
    (SELECT COUNT(*) FROM dp_pull_dp10_stage_item) +
    (SELECT COUNT(*) FROM dp_pull_dp10_stage_fingerprint_count) +
    (SELECT COUNT(*) FROM dp_pull_dp10_stage_identity) +
    (SELECT COUNT(*) FROM dp_pull_dp10_stage_cleanup) +
    (SELECT COUNT(*) FROM dp_pull_report_artifact) +
    (SELECT COUNT(*) FROM dp_pull_report_download_locator) +
    (SELECT COUNT(*) FROM dp_pull_report_apply) +
    (SELECT COUNT(*) FROM dp_pull_report_artifact_chunk) +
    (SELECT COUNT(*) FROM dp_pull_report_stage) +
    (SELECT COUNT(*) FROM dp_pull_report_stage_row) +
    (SELECT COUNT(*) FROM dp_pull_snapshot_fingerprint_count) +
    (SELECT COUNT(*) FROM dp_pull_snapshot_verify_page) +
    (SELECT COUNT(*) FROM dp_pull_snapshot_apply_progress) +
    (SELECT COUNT(*) FROM dp_pull_snapshot_effective_item) +
    (SELECT COUNT(*) FROM dp_pull_snapshot_current_head) +
    (SELECT COUNT(*) FROM dp_pull_advertising_generation) +
    (SELECT COUNT(*) FROM dp_pull_advertising_campaign_fact) +
    (SELECT COUNT(*) FROM dp_pull_advertising_query_fact) +
    (SELECT COUNT(*) FROM dp_pull_advertising_current_head) +
    (SELECT COUNT(*) FROM dp_pull_schedule_rotation
      WHERE next_operation_ordinal<>0 OR version_no<>0 OR gmt_updated<>gmt_create) +
    (SELECT COUNT(*) FROM dp_pull_schedule_epoch_sequence
      WHERE last_epoch_no<>0 OR version_no<>0 OR gmt_updated<>gmt_create) +
    (SELECT COUNT(*) FROM dp_pull_schedule_manifest_seal) +
    (SELECT COUNT(*) FROM dp_pull_schedule_source_epoch) +
    (SELECT COUNT(*) FROM dp_pull_schedule_source_scope) +
    (SELECT COUNT(*) FROM dp_pull_schedule_dp08_member_stage_head) +
    (SELECT COUNT(*) FROM dp_pull_schedule_dp08_member_stage_item) +
    (SELECT COUNT(*) FROM dp_pull_dp08_member_set) +
    (SELECT COUNT(*) FROM dp_pull_dp08_member_set_item) +
    (SELECT COUNT(*) FROM dp_pull_dp08_task_member_progress) +
    (SELECT COUNT(*) FROM dp_pull_backoff_hold) +
    (SELECT COUNT(*) FROM dp_pull_emergency_claim_hold);"
}
rollback_managed_release_data() {
  assert_no_backend_jvms || return 1
  if [ "$DP_RUNTIME_BOOTSTRAPPED" = 0 ]; then
    rollback_dp08_legacy_cohort || return 1
    rollback_dp_runtime_legacy_cohort || return 1
    require_legacy_cutover_ready
    return
  fi
  [ "$(dp_runtime_new_work_count)" = 0 ] || return 1
  local cutover_count=""
  cutover_count="$(dp_runtime_db_scalar "SELECT COUNT(*) FROM dp_pull_schedule_cutover;")"
  if [ "$cutover_count" != 0 ]; then
    [ "$cutover_count" = 11 ] || return 1
    verify_dp_runtime_database_binding || return 1
  fi
  rollback_dp08_legacy_cohort || return 1
  rollback_dp_runtime_legacy_cohort || return 1
  dp_runtime_db_scalar "START TRANSACTION;
    DELETE anchor FROM dp_pull_schedule_anchor anchor
      JOIN dp_pull_schedule_cutover cutover
        ON cutover.operation_code=anchor.operation_code
       AND cutover.cutover_key=anchor.cutover_key
      WHERE cutover.cutover_key='dp-runtime-$EXPECTED_COMMIT';
    DELETE binding FROM dp_pull_scope_binding_epoch binding
      JOIN dp_pull_scope_admission admission ON admission.scope_key=binding.scope_key
      WHERE admission.cutover_key='dp-runtime-$EXPECTED_COMMIT';
    DELETE FROM dp_pull_schedule_cutover
      WHERE cutover_key='dp-runtime-$EXPECTED_COMMIT';
    DELETE FROM dp_pull_scope_admission
      WHERE cutover_key='dp-runtime-$EXPECTED_COMMIT';
    COMMIT;"
  [ "$(dp_runtime_db_scalar 'SELECT COUNT(*) FROM dp_pull_schedule_cutover;')" = 0 ]
  require_legacy_cutover_ready
  DP_RUNTIME_BOOTSTRAPPED=0
}
'''


__all__ = ["build_dp_runtime_bootstrap_shell"]
