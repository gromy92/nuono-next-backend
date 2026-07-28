umask 077
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="$APP_DIR/backups/$RELEASE_NAME-$STAMP/additive-schema"
MIGRATION_DIR="$WORK_DIR/migrations"
FROZEN_JAR="$WORK_DIR/staged-backend.jar"
MYSQL_CNF="$WORK_DIR/mysql.cnf"
MIGRATION_CNF_SOURCE="$APP_DIR/.migration.cnf"
RUNNER_DIR="$WORK_DIR/schema-migration-runner"
RUNNER_MAIN="$RUNNER_DIR/release_schema_migrations.py"
MIGRATION_182="$MIGRATION_DIR/182_product_barcode_psku_identity.sql"
MIGRATION_189="$MIGRATION_DIR/189_product_barcode_store_identity_repair.sql"
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
  unzip -p "$FROZEN_JAR" "BOOT-INF/classes/db/init/$filename" > "$destination"
  [ -s "$destination" ]
  [ "$(sha256_file "$destination")" = "$expected_sha" ]
}

extract_runner_file() {
  local relative="$1"
  local destination="$RUNNER_DIR/$relative"
  mkdir -p "$(dirname "$destination")"
  unzip -p \
    "$FROZEN_JAR" \
    "BOOT-INF/classes/release/schema-migrations/$relative" \
    > "$destination"
  [ -s "$destination" ]
}

extract_schema_migration_runner() {
  local relative
  for relative in \
    release_schema_migrations.py \
    schema_migrations/__init__.py \
    schema_migrations/artifact.py \
    schema_migrations/catalog.py \
    schema_migrations/core.py \
    schema_migrations/model.py \
    schema_migrations/mysql_client.py \
    schema_migrations/mysql_database.py \
    schema_migrations/mysql_history.py \
    schema_migrations/mysql_history_sql.py \
    schema_migrations/mysql_support.py \
    schema_migrations/runner.py \
    schema_migrations/state.py
  do
    extract_runner_file "$relative"
  done
}

prepare_mysql_client() {
  python3 - "$APP_DIR/.env" "$MIGRATION_CNF_SOURCE" "$MYSQL_CNF" <<'PY'
from pathlib import Path
from urllib.parse import urlsplit
import os
import stat
import sys

env_path = Path(sys.argv[1])
source_cnf = Path(sys.argv[2])
cnf_path = Path(sys.argv[3])
database_url = None
for raw_line in env_path.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    if key == "NUONO_NEXT_DB_URL":
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        database_url = value

url = database_url or ""
if not url.startswith("jdbc:mysql://"):
    raise SystemExit("database URL is not an explicit jdbc:mysql target")
parsed = urlsplit(url.removeprefix("jdbc:"))
database = parsed.path.lstrip("/")
if not parsed.hostname or not database or "/" in database:
    raise SystemExit("database URL is missing host or schema")

metadata = source_cnf.lstat()
if not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode):
    raise SystemExit("migration defaults file must be a regular non-symlink")
if metadata.st_mode & 0o077:
    raise SystemExit("migration defaults file must have mode 0600")
if metadata.st_uid != os.getuid():
    raise SystemExit("migration defaults file must be owned by the release user")
if metadata.st_size > 65536:
    raise SystemExit("migration defaults file is unexpectedly large")
source_fd = os.open(source_cnf, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
try:
    opened = os.fstat(source_fd)
    if not stat.S_ISREG(opened.st_mode):
        raise SystemExit("opened migration defaults file is not regular")
    if (opened.st_dev, opened.st_ino) != (metadata.st_dev, metadata.st_ino):
        raise SystemExit("migration defaults file changed while opening")
    if opened.st_mode & 0o077 or opened.st_uid != os.getuid():
        raise SystemExit("opened migration defaults file permissions changed")
    if opened.st_size > 65536:
        raise SystemExit("opened migration defaults file is unexpectedly large")
    target_fd = os.open(
        cnf_path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
        0o600,
    )
except BaseException:
    os.close(source_fd)
    raise
with os.fdopen(source_fd, "rb") as source, os.fdopen(target_fd, "wb") as target:
    while True:
        chunk = source.read(1024 * 1024)
        if not chunk:
            break
        target.write(chunk)
print(f"{parsed.hostname}\t{parsed.port or 3306}\t{database}")
PY
}

db_scalar() {
  mysql \
    --defaults-file="$MYSQL_CNF" \
    --no-login-paths \
    --skip-reconnect \
    --protocol=TCP \
    --host="$EXPECTED_DB_HOST" \
    --port="$EXPECTED_DB_PORT" \
    --database="$EXPECTED_SCHEMA" \
    --batch --skip-column-names --raw -e "$1"
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
  mysql \
    --defaults-file="$MYSQL_CNF" \
    --no-login-paths \
    --skip-reconnect \
    --protocol=TCP \
    --host="$EXPECTED_DB_HOST" \
    --port="$EXPECTED_DB_PORT" \
    --database="$EXPECTED_SCHEMA" \
    < "$1"
}

run_forward_schema_migrations() {
  local result
  result="$(
    python3 "$RUNNER_MAIN" apply \
      --staged-jar "$FROZEN_JAR" \
      --governed-jar-sha256 "$EXPECTED_JAR_SHA256" \
      --mysql-defaults-file "$MYSQL_CNF" \
      --expected-schema "$EXPECTED_SCHEMA" \
      --expected-host "$EXPECTED_DB_HOST" \
      --expected-port "$EXPECTED_DB_PORT" \
      --release-commit "$EXPECTED_COMMIT" \
      --installed-by "governed-cutover:$RELEASE_NAME"
  )"
  emit FORWARD_SCHEMA_MIGRATIONS "$result"
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
  [ -f "$APP_DIR/.env" ]
  [ -f "$MIGRATION_CNF_SOURCE" ]
  mkdir -p "$MIGRATION_DIR"
  freeze_staged_jar
  extract_migration "182_product_barcode_psku_identity.sql" "$MIGRATION_182" "$EXPECTED_182_SHA256"
  extract_migration "189_product_barcode_store_identity_repair.sql" "$MIGRATION_189" "$EXPECTED_189_SHA256"
  extract_migration "190_noon_shared_email_auth_recovery.sql" "$MIGRATION_190" "$EXPECTED_190_SHA256"
  extract_migration "204_product_listing_workflow_attempt_claim.sql" "$MIGRATION_204" "$EXPECTED_204_SHA256"
  extract_migration "205_product_listing_reauthentication_attempt.sql" "$MIGRATION_205" "$EXPECTED_205_SHA256"
  extract_schema_migration_runner
  local target
  target="$(prepare_mysql_client)"
  IFS=$'\t' read -r EXPECTED_DB_HOST EXPECTED_DB_PORT EXPECTED_SCHEMA <<< "$target"
  [ -n "$EXPECTED_DB_HOST" ]
  [[ "$EXPECTED_DB_PORT" =~ ^[0-9]+$ ]]
  [ -n "$EXPECTED_SCHEMA" ]
}
