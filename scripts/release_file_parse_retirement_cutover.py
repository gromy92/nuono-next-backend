#!/usr/bin/env python3
"""Render the guarded runtime-drain cutover for file-parse retirement."""

from __future__ import annotations

import re
import shlex

from release_single_scheduler_cutover import build_single_scheduler_cutover_script


MIGRATION_KEY = "242_file_management_parse_retirement.sql"
PREREQUISITE_MANAGED_MIGRATION_KEYS = (
    "240_operations_competitor_snapshot_active_uniqueness.sql",
    "241_operations_competitor_correction_writer_fence.sql",
)
RUNNER_FILES = (
    "release_schema_migrations.py",
    "schema_migrations/__init__.py",
    "schema_migrations/artifact.py",
    "schema_migrations/catalog.py",
    "schema_migrations/core.py",
    "schema_migrations/model.py",
    "schema_migrations/mysql_client.py",
    "schema_migrations/mysql_database.py",
    "schema_migrations/mysql_history.py",
    "schema_migrations/mysql_history_sql.py",
    "schema_migrations/mysql_support.py",
    "schema_migrations/runner.py",
    "schema_migrations/state.py",
)


def _q(value: str | int) -> str:
    return shlex.quote(str(value))


def _insert_once(script: str, marker: str, addition: str) -> str:
    if script.count(marker) != 1:
        raise ValueError(f"cutover marker is not unique: {marker!r}")
    return script.replace(marker, marker + addition, 1)


def build_file_parse_retirement_cutover_script(
    *,
    staged_jar: str,
    expected_jar_sha256: str,
    expected_active_jar_sha256: str,
    expected_commit: str,
    active_slot: str,
    target_slot: str,
    active_port: int,
    target_port: int,
    maintenance_port: int,
    nginx_upstream_file: str,
    release_name: str,
    external_health_url: str,
    app_dir: str,
    mysql_defaults_file: str,
    expected_schema: str,
    expected_db_host: str,
    expected_db_port: int,
) -> str:
    if not re.fullmatch(r"[0-9a-f]{40}", expected_commit):
        raise ValueError("expected_commit must be a full lowercase Git SHA")
    if not mysql_defaults_file.startswith("/"):
        raise ValueError("mysql_defaults_file must be absolute")
    if not expected_schema or not expected_db_host:
        raise ValueError("expected database identity is required")
    base = build_single_scheduler_cutover_script(
        staged_jar=staged_jar,
        expected_jar_sha256=expected_jar_sha256,
        expected_active_jar_sha256=expected_active_jar_sha256,
        active_slot=active_slot,
        target_slot=target_slot,
        active_port=active_port,
        target_port=target_port,
        maintenance_port=maintenance_port,
        nginx_upstream_file=nginx_upstream_file,
        release_name=release_name,
        external_health_url=external_health_url,
        app_dir=app_dir,
    )
    runner_files = " ".join(_q(value) for value in RUNNER_FILES)
    prerequisite_approvals = " ".join(
        f"--approve-managed {_q(value)}"
        for value in PREREQUISITE_MANAGED_MIGRATION_KEYS
    )
    support = f"""
DRAIN_MIGRATION_KEY={_q(MIGRATION_KEY)}
DRAIN_EXPECTED_COMMIT={_q(expected_commit)}
DRAIN_MYSQL_CNF={_q(mysql_defaults_file)}
DRAIN_EXPECTED_SCHEMA={_q(expected_schema)}
DRAIN_EXPECTED_DB_HOST={_q(expected_db_host)}
DRAIN_EXPECTED_DB_PORT={_q(expected_db_port)}
DRAIN_RUNNER_DIR="$BACKUP_DIR/file-parse-retirement-runner"
DRAIN_RUNNER_MAIN="$DRAIN_RUNNER_DIR/release_schema_migrations.py"
DRAIN_RESULT_FILE="$BACKUP_DIR/file-parse-retirement-result.json"
DRAINED_RUNTIME_MIGRATION_STARTED=0
DRAIN_RUNNER_FILES=({runner_files})
backend_jvm_count() {{
  ps -eo args= | awk -v prefix="-jar $APP_DIR/" '
    $1 ~ /(^|\\/)java$/ && index($0, prefix) > 0 {{ count += 1 }}
    END {{ print count + 0 }}'
}}
prepare_runtime_drain_runner() {{
  command -v unzip >/dev/null
  command -v python3 >/dev/null
  [ -f "$DRAIN_MYSQL_CNF" ]
  [ "$(stat -c '%a' "$DRAIN_MYSQL_CNF")" = 600 ]
  mkdir -p "$DRAIN_RUNNER_DIR"
  local relative destination
  for relative in "${{DRAIN_RUNNER_FILES[@]}}"; do
    destination="$DRAIN_RUNNER_DIR/$relative"
    mkdir -p "$(dirname "$destination")"
    unzip -p "$STAGED_JAR" \
      "BOOT-INF/classes/release/schema-migrations/$relative" > "$destination"
    [ -s "$destination" ]
  done
  python3 -m py_compile "$DRAIN_RUNNER_MAIN" \
    "$DRAIN_RUNNER_DIR"/schema_migrations/*.py
}}
run_runtime_drain_migration() {{
  DRAINED_RUNTIME_MIGRATION_STARTED=1
  python3 "$DRAIN_RUNNER_MAIN" apply \
    --staged-jar "$STAGED_JAR" \
    --governed-jar-sha256 "$EXPECTED_JAR_SHA256" \
    --mysql-defaults-file "$DRAIN_MYSQL_CNF" \
    --expected-schema "$DRAIN_EXPECTED_SCHEMA" \
    --expected-host "$DRAIN_EXPECTED_DB_HOST" \
    --expected-port "$DRAIN_EXPECTED_DB_PORT" \
    --release-commit "$DRAIN_EXPECTED_COMMIT" \
    --installed-by "governed-cutover:$RELEASE_NAME" \
    {prerequisite_approvals} \
    --approve-managed "$DRAIN_MIGRATION_KEY" \
    --approve-runtime-drain "$DRAIN_MIGRATION_KEY" \
    > "$DRAIN_RESULT_FILE"
  grep -F -q '"result":"APPLIED"' "$DRAIN_RESULT_FILE"
  grep -F -q "$DRAIN_MIGRATION_KEY" "$DRAIN_RESULT_FILE"
  emit FILE_PARSE_RETIREMENT_MIGRATION PASS
}}
ensure_drained_failure_maintenance() {{
  if [ "$(maintenance_response_status)" != 503 ]; then
    MAINTENANCE_PID=""
    start_maintenance_responder || true
  fi
  [ "$(current_upstream_port)" = "$MAINTENANCE_PORT" ] ||
    switch_nginx_to_maintenance || true
}}
"""
    script = _insert_once(base, "ROLLBACK_RUNNING=0\n", support)
    script = _insert_once(
        script,
        "  ROLLBACK_RUNNING=1\n",
        "  if [ \"$DRAINED_RUNTIME_MIGRATION_STARTED\" = 1 ]; then\n"
        "    [ \"$NEW_START_ATTEMPTED\" = 0 ] || stop_target_runtime || true\n"
        "    ensure_drained_failure_maintenance || true\n"
        "    emit FILE_PARSE_RETIREMENT_MIGRATION REPAIR_FORWARD_REQUIRED\n"
        "    emit SAFE_OLD_JAR_ROLLBACK FORBIDDEN\n"
        "    exit \"$original_status\"\n"
        "  fi\n",
    )
    script = _insert_once(
        script,
        "validate_cutover\n",
        "[ \"$(backend_jvm_count)\" = 1 ]\n",
    )
    script = _insert_once(
        script,
        "[ \"$(sha256_file \"$TARGET_SLOT_DIR/$JAR_NAME\")\" = \"$EXPECTED_JAR_SHA256\" ]\n",
        "prepare_runtime_drain_runner\n",
    )
    script = _insert_once(
        script,
        "OLD_STOPPED=1\n",
        "[ \"$(backend_jvm_count)\" = 0 ]\n"
        "run_runtime_drain_migration\n",
    )
    script = _insert_once(
        script,
        "process_uses_jar \"$NEW_PID\" \"$TARGET_SLOT_DIR/$JAR_NAME\"\n",
        "[ \"$(backend_jvm_count)\" = 1 ]\n",
    )
    return script
