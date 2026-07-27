STAMP="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="$APP_DIR/backups/$RELEASE_NAME-$STAMP/additive-schema"
MIGRATION_DIR="$WORK_DIR/migrations"
MYSQL_CNF="$WORK_DIR/mysql.cnf"
MIGRATION_182="$MIGRATION_DIR/182_product_barcode_psku_identity.sql"
MIGRATION_190="$MIGRATION_DIR/190_noon_shared_email_auth_recovery.sql"
MIGRATION_204="$MIGRATION_DIR/204_product_listing_workflow_attempt_claim.sql"
MIGRATION_205="$MIGRATION_DIR/205_product_listing_reauthentication_attempt.sql"

emit() { printf '%s=%s\n' "$1" "$2"; }
sha256_file() { sha256sum "$1" | awk '{print $1}'; }
cleanup_additive_migrations() {
  [ ! -f "$MYSQL_CNF" ] || rm -f -- "$MYSQL_CNF"
}
trap cleanup_additive_migrations EXIT INT TERM

extract_migration() {
  local filename="$1"
  local destination="$2"
  local expected_sha="$3"
  unzip -p "$STAGED_JAR" "BOOT-INF/classes/db/init/$filename" > "$destination"
  [ -s "$destination" ]
  [ "$(sha256_file "$destination")" = "$expected_sha" ]
}

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

require_migration_190() {
  local ready
  ready="$(db_scalar "
    SELECT IF(
      (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema = DATABASE()
         AND table_name IN (
           'noon_auth_identity_recovery',
           'noon_auth_identity_send_ledger',
           'noon_project_auth_state',
           'noon_auth_identity_recovery_item'
         )) = 4
      AND EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'noon_auth_identity_recovery'
          AND column_name = 'send_budget_epoch'
      )
      AND EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'noon_project_auth_state'
          AND column_name = 'binding_fingerprint'
      )
      AND EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'noon_project_auth_state'
          AND column_name = 'config_fingerprint'
      )
      AND EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'noon_pull_task'
          AND column_name = 'auth_recovery_id'
      ),
      1,
      0
    );
  ")"
  [ "$ready" = "1" ]
}

apply_migration() {
  mysql --defaults-extra-file="$MYSQL_CNF" < "$1"
}

postcheck_migration_204() {
  local ready
  ready="$(db_scalar "
    SELECT IF(
      EXISTS(
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'product_listing_real_run_attempt_claim'
      )
      AND (
        SELECT COUNT(*) FROM product_listing_real_run_attempt_claim
      ) = (
        SELECT COUNT(*) FROM (
          SELECT owner_user_id, source_task_id
          FROM product_listing_task
          WHERE mode = 'REAL_RUN' AND source_task_id IS NOT NULL
          GROUP BY owner_user_id, source_task_id
        ) expected_claims
      ),
      1,
      0
    );
  ")"
  [ "$ready" = "1" ]
}

postcheck_migration_205() {
  local ready
  ready="$(db_scalar "
    SELECT IF(
      EXISTS(
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'product_listing_reauthentication_attempt'
      )
      AND (
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'product_listing_reauthentication_attempt'
          AND column_name IN (
            'real_run_task_id',
            'recovery_id',
            'recovery_item_id',
            'requested_auth_version',
            'resume_action',
            'status'
          )
      ) = 6,
      1,
      0
    );
  ")"
  [ "$ready" = "1" ]
}

validate_additive_migrations() {
  command -v mysql >/dev/null
  command -v unzip >/dev/null
  command -v python3 >/dev/null
  [[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]]
  [ -f "$STAGED_JAR" ]
  [ "$(sha256_file "$STAGED_JAR")" = "$EXPECTED_JAR_SHA256" ]
  [ -f "$APP_DIR/.env" ]
  mkdir -p "$MIGRATION_DIR"
  extract_migration "182_product_barcode_psku_identity.sql" "$MIGRATION_182" "$EXPECTED_182_SHA256"
  extract_migration "190_noon_shared_email_auth_recovery.sql" "$MIGRATION_190" "$EXPECTED_190_SHA256"
  extract_migration "204_product_listing_workflow_attempt_claim.sql" "$MIGRATION_204" "$EXPECTED_204_SHA256"
  extract_migration "205_product_listing_reauthentication_attempt.sql" "$MIGRATION_205" "$EXPECTED_205_SHA256"
  prepare_mysql_client
}
