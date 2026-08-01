import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.artifact import RUNNER_RELATIVE_PATHS  # noqa: E402

MODULE_PATH = SCRIPT_DIR / "release_cutover_maintenance.py"


def load_module():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleaseSchemaCutoverTest(unittest.TestCase):
    def additive_script(self):
        return load_module().build_additive_schema_migration_script(
            staged_jar="/staged/backend.jar",
            expected_jar_sha256="a" * 64,
            expected_commit="b" * 40,
            expected_182_sha256="1" * 64,
            expected_189_sha256="5" * 64,
            expected_190_sha256="2" * 64,
            expected_204_sha256="3" * 64,
            expected_205_sha256="4" * 64,
            approved_managed_migrations=(
                "231_procurement_fulfillment_balance_quantity_invariant.sql",
            ),
            app_dir="/app",
            release_name="schema-test",
        )

    def irreversible_script(self):
        return load_module().build_irreversible_schema_cutover_script(
            expected_jar_sha256="a" * 64,
            expected_commit="b" * 40,
            expected_182_sha256="1" * 64,
            expected_189_sha256="3" * 64,
            expected_206_sha256="2" * 64,
            active_slot="green",
            active_port=18088,
            standby_port=18087,
            maintenance_port=18089,
            nginx_upstream_file="/managed/upstream.inc",
            release_name="schema-test",
            external_health_url="https://www.nuoon.com/ai/actuator/health",
            app_dir="/app",
        )

    def test_182_is_conditionally_applied_before_204_and_205(self):
        script = self.additive_script()
        execution = script[script.index("validate_additive_migrations\n") :]

        forward = execution.index("run_forward_schema_migrations")
        migration_182 = execution.index("ensure_migration_182")
        migration_204 = execution.index('apply_migration "$MIGRATION_204"')
        migration_205 = execution.index('apply_migration "$MIGRATION_205"')

        self.assertLess(forward, migration_182)
        self.assertLess(migration_182, migration_204)
        self.assertLess(migration_204, migration_205)
        self.assertIn("SKIPPED_READY", script)
        self.assertIn("APPLIED_FROM_EXACT_LEGACY", script)
        self.assertIn("APPLIED_DATA_REPAIR", script)
        self.assertIn("BLOCKED_PARTIAL_UNSAFE", script)

    def test_189_repairs_every_mappable_barcode_before_later_additive_migrations(self):
        script = self.additive_script()
        execution = script[script.index("validate_additive_migrations\n") :]

        migration_182 = execution.index("ensure_migration_182")
        migration_189 = execution.index('apply_migration "$MIGRATION_189"')
        postcheck_189 = execution.index("postcheck_migration_189")
        migration_204 = execution.index('apply_migration "$MIGRATION_204"')

        self.assertLess(migration_182, migration_189)
        self.assertLess(migration_189, postcheck_189)
        self.assertLess(postcheck_189, migration_204)
        self.assertIn("189_product_barcode_store_identity_repair.sql", script)
        self.assertIn('"$EXPECTED_189_SHA256"', script)
        self.assertIn("MIGRATION_189_FULL_ROW_BLOCKERS", script)
        self.assertIn("MIGRATION_189_SHA256", script)

        migration_sql = (
            Path(__file__).parents[2]
            / "src/main/resources/db/init/189_product_barcode_store_identity_repair.sql"
        ).read_text(encoding="utf-8")
        self.assertIn("JOIN `product_variant`", migration_sql)
        self.assertIn("pb.logical_store_id = pv.logical_store_id", migration_sql)
        self.assertNotIn("is_deleted", migration_sql)

    def test_206_has_bounded_row_and_metadata_lock_waits(self):
        migration_sql = (
            Path(__file__).parents[2]
            / "src/main/resources/db/init/206_product_barcode_store_uniqueness.sql"
        ).read_text(encoding="utf-8")

        self.assertIn("SET SESSION innodb_lock_wait_timeout = 5", migration_sql)
        self.assertIn("SET SESSION lock_wait_timeout = 5", migration_sql)

    def test_182_contract_proves_exact_columns_and_index_order(self):
        script = self.additive_script()

        for column in ("product_master_id", "logical_store_id", "partner_sku"):
            self.assertIn(column, script)
        self.assertIn("column_type = 'varchar(100)'", script)
        self.assertIn("column_type = 'bigint'", script)
        self.assertIn("sub_part IS NULL", script)
        self.assertIn("idx_product_barcode_master", script)
        self.assertIn("idx_product_barcode_store_psku", script)
        self.assertIn("seq_in_index = 3 AND column_name = 'is_deleted'", script)
        self.assertIn("MIGRATION_182_RELATION_BLOCKERS", script)
        self.assertIn("MIGRATION_182_DATA_BLOCKERS", script)

    def test_182_and_206_are_extracted_from_same_active_frozen_jar(self):
        script = self.irreversible_script()
        validation = script[
            script.index("validate_irreversible_cutover()")
            : script.index("trap handle_irreversible_failure ERR")
        ]

        self.assertIn('"$ACTIVE_JAR"', validation)
        self.assertIn("182_product_barcode_psku_identity.sql", validation)
        self.assertIn("189_product_barcode_store_identity_repair.sql", validation)
        self.assertIn("206_product_barcode_store_uniqueness.sql", validation)
        self.assertIn('"$EXPECTED_182_SHA256"', validation)
        self.assertIn('"$EXPECTED_189_SHA256"', validation)
        self.assertIn('"$EXPECTED_206_SHA256"', validation)
        self.assertLess(
            validation.index("postcheck_migration_182"),
            validation.index("precheck_migration_206"),
        )

    def test_182_is_proven_before_json_503_or_runtime_stop(self):
        script = self.irreversible_script()
        execution = script[script.index("trap handle_irreversible_failure ERR") :]

        validation = execution.index("validate_irreversible_cutover")
        maintenance = execution.index("start_maintenance_responder")
        runtime_stop = execution.index('stop_pid "$ACTIVE_PID"')

        self.assertLess(validation, maintenance)
        self.assertLess(maintenance, runtime_stop)
        self.assertIn("postcheck_migration_182", script)
        self.assertIn('"$(backend_jvm_count)" = 0', execution)

    def test_additive_uses_a_frozen_release_only_database_account(self):
        script = self.additive_script()

        self.assertNotIn('source "$APP_DIR/.env"', script)
        self.assertNotIn('cat "$APP_DIR/.env"', script)
        self.assertIn('MIGRATION_CNF_SOURCE="$APP_DIR/.migration.cnf"', script)
        self.assertNotIn("NUONO_NEXT_DB_PASSWORD", script)
        self.assertNotIn("NUONO_NEXT_DB_USERNAME", script)
        self.assertIn("os.O_EXCL", script)
        self.assertIn("0o600", script)
        self.assertIn('--defaults-file="$MYSQL_CNF"', script)
        self.assertIn("--no-login-paths", script)
        self.assertIn('MYSQL_LOGIN_PATH_ARGS=(--no-login-paths)', script)
        self.assertIn('"${MYSQL_LOGIN_PATH_ARGS[@]}"', script)
        self.assertIn('rm -f -- "$MYSQL_CNF"', script)
        self.assertIn('FROZEN_JAR="$WORK_DIR/staged-backend.jar"', script)
        self.assertIn("freeze_staged_jar", script)
        self.assertNotIn('unzip -p "$STAGED_JAR"', script)
        self.assertIn('--staged-jar "$FROZEN_JAR"', script)
        self.assertIn('--host="$EXPECTED_DB_HOST"', script)
        self.assertIn('--port="$EXPECTED_DB_PORT"', script)
        self.assertIn('--database="$EXPECTED_SCHEMA"', script)
        self.assertIn('--expected-host "$EXPECTED_DB_HOST"', script)
        self.assertIn('--expected-port "$EXPECTED_DB_PORT"', script)

    def test_additive_extracts_every_runner_file_bound_to_the_jar(self):
        script = self.additive_script()

        for relative in RUNNER_RELATIVE_PATHS:
            self.assertIn(relative.as_posix(), script)

    def test_additive_passes_only_explicit_managed_approvals(self):
        script = self.additive_script()

        self.assertIn(
            "APPROVED_MANAGED_MIGRATIONS="
            "231_procurement_fulfillment_balance_quantity_invariant.sql",
            script,
        )
        self.assertIn(
            'runner_args+=(--approve-managed "$migration_key")',
            script,
        )
        self.assertIn('result="$("${runner_args[@]}")"', script)

    def test_irreversible_credentials_remain_private_and_cleaned(self):
        script = self.irreversible_script()

        self.assertNotIn('source "$APP_DIR/.env"', script)
        self.assertNotIn('cat "$APP_DIR/.env"', script)
        self.assertIn("os.O_EXCL, 0o600", script)
        self.assertIn('rm -f -- "$MYSQL_CNF"', script)


if __name__ == "__main__":
    unittest.main()
