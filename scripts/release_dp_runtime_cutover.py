#!/usr/bin/env python3
"""Fail-closed database and legacy-writer fence for the managed DP cutover."""
from __future__ import annotations
from release_dp_runtime_legacy_drain import build_dp_runtime_legacy_drain_shell


def build_dp_runtime_cutover_shell() -> str:
    return r'''
DP_RUNTIME_MYSQL_CNF="$APP_DIR/.migration.cnf"
DP_RUNTIME_DB_HOST=""
DP_RUNTIME_DB_PORT=""
DP_RUNTIME_DB_SCHEMA=""
DP_RUNTIME_SCHEMA_BINDING_SHA256=""
DP_RUNTIME_CUTOVER_BINDING_SHA256=""
DP_RUNTIME_CUTOVER_OPERATION_COUNT=""

dp_runtime_mysql() {
  [ -n "$DP_RUNTIME_DB_HOST" ]
  [[ "$DP_RUNTIME_DB_PORT" =~ ^[0-9]+$ ]]
  [ -n "$DP_RUNTIME_DB_SCHEMA" ]
  mysql --defaults-extra-file="$DP_RUNTIME_MYSQL_CNF" \
    --connect-timeout=10 --skip-reconnect --protocol=TCP \
    --host="$DP_RUNTIME_DB_HOST" --port="$DP_RUNTIME_DB_PORT" \
    --database="$DP_RUNTIME_DB_SCHEMA" "$@"
}
dp_runtime_db_scalar() {
  dp_runtime_mysql --batch --skip-column-names --raw -e "$1"
}
dp_runtime_database_target() {
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
try:
    port = parsed.port or 3306
except ValueError as error:
    raise SystemExit("managed DP database port is invalid") from error
if parsed.username is not None or parsed.password is not None:
    raise SystemExit("managed DP database URL must not contain credentials")
if parsed.fragment:
    raise SystemExit("managed DP database URL fragment is forbidden")
if not parsed.hostname or not schema or "/" in schema:
    raise SystemExit("managed DP database target is invalid")
if not 1 <= port <= 65535:
    raise SystemExit("managed DP database port is invalid")
if any(character.isspace() or ord(character) < 32 for character in parsed.hostname):
    raise SystemExit("managed DP database host is invalid")
if (schema.startswith("-") or any(
        character.isspace() or ord(character) < 32 for character in schema)):
    raise SystemExit("managed DP database schema is invalid")
print(parsed.hostname, port, schema, sep="\t")
PY
}
prepare_dp_runtime_database_target() {
  local row=""
  row="$(dp_runtime_database_target)"
  IFS=$'\t' read -r DP_RUNTIME_DB_HOST DP_RUNTIME_DB_PORT \
    DP_RUNTIME_DB_SCHEMA <<<"$row"
  [ -n "$DP_RUNTIME_DB_HOST" ]
  [[ "$DP_RUNTIME_DB_PORT" =~ ^[0-9]+$ ]]
  [ "$DP_RUNTIME_DB_PORT" -ge 1 ] && [ "$DP_RUNTIME_DB_PORT" -le 65535 ]
  [ -n "$DP_RUNTIME_DB_SCHEMA" ]
}
dp_runtime_database_binding() {
  dp_runtime_db_scalar "
    WITH required_migration AS (
      SELECT '243_dp_pull_runtime.sql' migration_key UNION ALL
      SELECT '244_dp_pull_report_bounded_apply.sql' UNION ALL
      SELECT '245_dp_pull_snapshot_bounded_apply.sql' UNION ALL
      SELECT '246_dp_pull_advertising_generation.sql' UNION ALL
      SELECT '247_dp_pull_schedule_core.sql' UNION ALL
      SELECT '248_dp_pull_dp08_member_retention.sql' UNION ALL
      SELECT '250_dp_pull_advertising_campaign_pagination.sql'
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
    WHERE schema_binding.migration_count=7;"
}
dp_runtime_schema_binding() {
  dp_runtime_db_scalar "
    WITH required_migration AS (
      SELECT '243_dp_pull_runtime.sql' migration_key UNION ALL
      SELECT '244_dp_pull_report_bounded_apply.sql' UNION ALL
      SELECT '245_dp_pull_snapshot_bounded_apply.sql' UNION ALL
      SELECT '246_dp_pull_advertising_generation.sql' UNION ALL
      SELECT '247_dp_pull_schedule_core.sql' UNION ALL
      SELECT '248_dp_pull_dp08_member_retention.sql' UNION ALL
      SELECT '250_dp_pull_advertising_campaign_pagination.sql'
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
    SELECT binding_sha256 FROM schema_binding WHERE migration_count=7;"
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
''' + build_dp_runtime_legacy_drain_shell()


__all__ = ["build_dp_runtime_cutover_shell"]
