import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "release_cutover_maintenance.py"


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
            expected_190_sha256="2" * 64,
            expected_204_sha256="3" * 64,
            expected_205_sha256="4" * 64,
            app_dir="/app",
            release_name="schema-test",
        )

    def irreversible_script(self):
        return load_module().build_irreversible_schema_cutover_script(
            expected_jar_sha256="a" * 64,
            expected_commit="b" * 40,
            expected_182_sha256="1" * 64,
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

        migration_182 = execution.index("ensure_migration_182")
        migration_204 = execution.index('apply_migration "$MIGRATION_204"')
        migration_205 = execution.index('apply_migration "$MIGRATION_205"')

        self.assertLess(migration_182, migration_204)
        self.assertLess(migration_204, migration_205)
        self.assertIn("SKIPPED_READY", script)
        self.assertIn("APPLIED_FROM_EXACT_LEGACY", script)
        self.assertIn("APPLIED_DATA_REPAIR", script)
        self.assertIn("BLOCKED_PARTIAL_UNSAFE", script)

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
        self.assertIn("206_product_barcode_store_uniqueness.sql", validation)
        self.assertIn('"$EXPECTED_182_SHA256"', validation)
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

    def test_database_credentials_use_a_mode_0600_client_file_and_are_cleaned(self):
        for script in (self.additive_script(), self.irreversible_script()):
            with self.subTest(action=script.split("WORK_DIR=", 1)[1].splitlines()[0]):
                self.assertNotIn('source "$APP_DIR/.env"', script)
                self.assertNotIn('cat "$APP_DIR/.env"', script)
                self.assertIn("os.O_EXCL, 0o600", script)
                self.assertIn('--defaults-extra-file="$MYSQL_CNF"', script)
                self.assertIn('rm -f -- "$MYSQL_CNF"', script)


if __name__ == "__main__":
    unittest.main()
