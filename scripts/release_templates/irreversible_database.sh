prepare_mysql_client() {
  python3 - "$APP_DIR/.env" "$MYSQL_CNF" <<'PY'
from pathlib import Path
from urllib.parse import urlsplit
import os
import sys

env_path = Path(sys.argv[1])
cnf_path = Path(sys.argv[2])
values = {}
for raw_line in env_path.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    if key in {
        "NUONO_NEXT_DB_URL",
        "NUONO_NEXT_DB_USERNAME",
        "NUONO_NEXT_DB_PASSWORD",
    }:
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        values[key] = value

required = {
    "NUONO_NEXT_DB_URL",
    "NUONO_NEXT_DB_USERNAME",
    "NUONO_NEXT_DB_PASSWORD",
}
if set(values) != required:
    raise SystemExit("database environment keys are incomplete")
url = values["NUONO_NEXT_DB_URL"]
if not url.startswith("jdbc:mysql://"):
    raise SystemExit("database URL is not an explicit jdbc:mysql target")
parsed = urlsplit(url.removeprefix("jdbc:"))
database = parsed.path.lstrip("/")
if not parsed.hostname or not database:
    raise SystemExit("database URL is missing host or schema")

def quote(value: str) -> str:
    if "\n" in value or "\r" in value:
        raise SystemExit("database option contains a newline")
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'

content = "\n".join([
    "[client]",
    f"host={quote(parsed.hostname)}",
    f"port={parsed.port or 3306}",
    f"user={quote(values['NUONO_NEXT_DB_USERNAME'])}",
    f"password={quote(values['NUONO_NEXT_DB_PASSWORD'])}",
    f"database={quote(database)}",
    "default-character-set=utf8mb4",
    "",
])
fd = os.open(cnf_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as handle:
    handle.write(content)
PY
}
db_scalar() {
  mysql --defaults-extra-file="$MYSQL_CNF" --batch --skip-column-names --raw -e "$1"
}
apply_migration() {
  mysql --defaults-extra-file="$MYSQL_CNF" < "$1"
}
drain_blocker_count() {
  db_scalar "
    SELECT
      (SELECT COUNT(*) FROM product_listing_task
       WHERE LOWER(status) IN ('running', 'submitted'))
      + (SELECT COUNT(*) FROM product_publish_task
         WHERE LOWER(status) IN (
           'queued', 'running', 'submitted', 'verifying',
           'pending_effective', 'write_unknown', 'verify_timeout',
           'write_retry_scheduled', 'product_delete_queued',
           'product_delete_running', 'product_delete_submitted',
           'product_delete_verifying', 'product_delete_pending_effective',
           'product_delete_verify_timeout',
           'product_delete_write_retry_scheduled'
         )
         OR active_lock_key IS NOT NULL
         OR locked_by IS NOT NULL)
      + (SELECT COUNT(*) FROM product_publish_task
         WHERE is_deleted = b'0'
           AND task_type = 'product-delete'
           AND status = 'synced'
           AND JSON_VALID(request_json)
           AND JSON_UNQUOTE(
             JSON_EXTRACT(request_json, '$.rebuildAction')
           ) = 'product-rebuild'
           AND (
             result_json IS NULL
             OR NOT JSON_VALID(result_json)
             OR JSON_EXTRACT(result_json, '$.rebuild.status') IS NULL
             OR JSON_UNQUOTE(
               JSON_EXTRACT(result_json, '$.rebuild.status')
             ) IN (
               'listing_submitted', 'listing_running',
               'listing_already_submitted', 'noon_auth_required'
             )
           ))
      + (SELECT COUNT(*) FROM product_image_suite
         WHERE suite_status IN (
           'PENDING_GENERATION', 'GENERATING', 'REGENERATING', 'PUBLISHING'
         ))
      + (SELECT COUNT(*) FROM noon_pull_task
         WHERE status IN ('QUEUED', 'RUNNING', 'BLOCKED_AUTH'))
      + (SELECT COUNT(*) FROM noon_auth_identity_recovery
         WHERE status NOT IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')
            OR lease_owner IS NOT NULL
            OR lease_token IS NOT NULL
            OR lease_until > NOW())
      + (SELECT COUNT(*) FROM noon_auth_identity_recovery_item
         WHERE status IN ('PENDING', 'VALIDATING'))
      + (SELECT COUNT(*) FROM product_listing_reauthentication_attempt
         WHERE status IN ('PENDING', 'VERIFYING'));
  "
}
assert_drained() {
  local blockers
  blockers="$(drain_blocker_count)"
  emit DRAIN_BLOCKERS "$blockers"
  [ "$blockers" = 0 ]
}
assert_database_idle() {
  local blockers
  blockers="$(db_scalar "
    SELECT
      (SELECT COUNT(*) FROM information_schema.innodb_trx)
      + (SELECT COUNT(*) FROM performance_schema.metadata_locks
         WHERE lock_status = 'PENDING')
      + (SELECT COUNT(*) FROM performance_schema.data_lock_waits);
  ")"
  emit DATABASE_LOCK_BLOCKERS "$blockers"
  [ "$blockers" = 0 ]
}
precheck_migration_206() {
  local blockers
  blockers="$(db_scalar "
    SELECT
      SUM(pv.id IS NULL)
      + SUM(pv.logical_store_id IS NULL)
      + SUM(pb.logical_store_id IS NULL)
      + SUM(pb.logical_store_id <> pv.logical_store_id)
      + SUM(pm.id IS NULL)
      + SUM(pv.logical_store_id <> pm.logical_store_id)
    FROM product_barcode pb
    LEFT JOIN product_variant pv ON pv.id = pb.variant_id
    LEFT JOIN product_master pm ON pm.id = pv.product_master_id;
  ")"
  emit MIGRATION_206_RELATION_BLOCKERS "$blockers"
  [ "$blockers" = 0 ]
  local projected_duplicates
  projected_duplicates="$(db_scalar "
    SELECT COUNT(*) FROM (
      SELECT pv.logical_store_id, pb.barcode
      FROM product_barcode pb
      JOIN product_variant pv ON pv.id = pb.variant_id
      GROUP BY pv.logical_store_id, pb.barcode
      HAVING COUNT(*) > 1
    ) duplicate_groups;
  ")"
  emit MIGRATION_206_PROJECTED_DUPLICATES "$projected_duplicates"
  [ "$projected_duplicates" = 0 ]
  local index_ready
  index_ready="$(db_scalar "
    SELECT IF(
      (SELECT COUNT(*) FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name = 'product_barcode'
         AND index_name = 'uk_product_barcode_barcode') = 1
      AND (SELECT COUNT(*) FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name = 'product_barcode'
         AND index_name = 'uk_product_barcode_barcode'
         AND non_unique = 0
         AND index_type = 'BTREE'
         AND sub_part IS NULL
         AND seq_in_index = 1
         AND column_name = 'barcode') = 1
      AND NOT EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'uk_product_barcode_store_barcode'
      ),
      1,
      0
    );
  ")"
  [ "$index_ready" = 1 ]
}
postcheck_migration_206() {
  local ready
  ready="$(db_scalar "
    SELECT IF(
      (SELECT is_nullable FROM information_schema.columns
       WHERE table_schema = DATABASE()
         AND table_name = 'product_barcode'
         AND column_name = 'logical_store_id') = 'NO'
      AND NOT EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'product_barcode'
          AND index_name = 'uk_product_barcode_barcode'
      )
      AND (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 'product_barcode'
             AND index_name = 'uk_product_barcode_store_barcode'
             AND non_unique = 0
             AND index_type = 'BTREE'
             AND sub_part IS NULL
             AND ((seq_in_index = 1 AND column_name = 'logical_store_id')
               OR (seq_in_index = 2 AND column_name = 'barcode'))) = 2
      AND (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 'product_barcode'
             AND index_name = 'uk_product_barcode_store_barcode') = 2
      AND NOT EXISTS(
        SELECT 1 FROM product_barcode WHERE logical_store_id IS NULL
      ),
      1,
      0
    );
  ")"
  [ "$ready" = 1 ]
}
validate_irreversible_cutover() {
  command -v mysql >/dev/null
  command -v unzip >/dev/null
  command -v python3 >/dev/null
  command -v lsof >/dev/null
  [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]]
  [ "$ACTIVE_PORT" != "$STANDBY_PORT" ]
  [ "$MAINTENANCE_PORT" != "$ACTIVE_PORT" ]
  [ "$MAINTENANCE_PORT" != "$STANDBY_PORT" ]
  grep -F -q NUONO_BLUE_GREEN_MANAGED "$NGINX_UPSTREAM_FILE"
  [ "$(current_upstream_port)" = "$ACTIVE_PORT" ]
  [ -z "$(pid_for_port "$STANDBY_PORT")" ]
  [ -z "$(pid_for_port "$MAINTENANCE_PORT")" ]
  [ "$(backend_jvm_count)" = 1 ]
  ACTIVE_PID="$(pid_for_port "$ACTIVE_PORT")"
  [ -n "$ACTIVE_PID" ]
  [ "$(health_status "$ACTIVE_PORT")" = UP ]
  [ -f "$ACTIVE_JAR" ]
  process_uses_jar "$ACTIVE_PID" "$ACTIVE_JAR"
  [ "$(sha256_file "$ACTIVE_JAR")" = "$EXPECTED_JAR_SHA256" ]
  mkdir -p "$MIGRATION_DIR"
  unzip -p "$ACTIVE_JAR" \
    "BOOT-INF/classes/db/init/182_product_barcode_psku_identity.sql" > "$MIGRATION_182"
  [ -s "$MIGRATION_182" ]
  [ "$(sha256_file "$MIGRATION_182")" = "$EXPECTED_182_SHA256" ]
  unzip -p "$ACTIVE_JAR" \
    "BOOT-INF/classes/db/init/206_product_barcode_store_uniqueness.sql" > "$MIGRATION_206"
  [ -s "$MIGRATION_206" ]
  [ "$(sha256_file "$MIGRATION_206")" = "$EXPECTED_206_SHA256" ]
  prepare_mysql_client
  assert_drained
  postcheck_migration_182
  precheck_migration_206
}
