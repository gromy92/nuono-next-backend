import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "release_cutover_maintenance.py"


def load_module():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleaseSchemaDrainTest(unittest.TestCase):
    def irreversible_script(self):
        return load_module().build_irreversible_schema_cutover_script(
            expected_jar_sha256="a" * 64,
            expected_commit="b" * 40,
            expected_182_sha256="1" * 64,
            expected_189_sha256="2" * 64,
            expected_206_sha256="3" * 64,
            active_slot="green",
            active_port=18088,
            standby_port=18087,
            maintenance_port=18089,
            nginx_upstream_file="/managed/upstream.inc",
            release_name="schema-drain-test",
            external_health_url="https://www.nuoon.com/ai/actuator/health",
            app_dir="/app",
        )

    def drain_contract(self):
        script = self.irreversible_script()
        return script[
            script.index("schema_write_blocker_count()")
            : script.index("precheck_migration_206()")
        ]

    def test_non_product_noon_backlog_is_preserved_without_becoming_a_schema_blocker(self):
        contract = self.drain_contract()

        self.assertIn("data_domain = 'PRODUCT'", contract)
        self.assertIn("status = 'RUNNING'", contract)
        self.assertIn("locked_by IS NOT NULL", contract)
        self.assertIn("PRESERVED_NOON_BACKLOG", contract)
        self.assertNotIn(
            "WHERE status IN ('QUEUED', 'RUNNING', 'BLOCKED_AUTH'))",
            contract,
        )

    def test_drain_is_read_only_and_jvm_stop_precedes_database_proofs(self):
        script = self.irreversible_script()
        contract = self.drain_contract()
        execution = script[script.index("trap handle_irreversible_failure ERR") :]

        self.assertNotRegex(contract, r"\b(?:UPDATE|DELETE|INSERT)\s+")
        maintenance = execution.index("switch_nginx_to_maintenance")
        first_drain = execution.index("assert_drained", maintenance)
        stop_jvm = execution.index('stop_pid "$ACTIVE_PID"')
        second_drain = execution.index("assert_drained", stop_jvm)
        database_idle = execution.index("assert_database_idle", second_drain)
        precheck = execution.index("precheck_migration_206", database_idle)
        irreversible = execution.index("IRREVERSIBLE_STARTED=1", precheck)
        migration = execution.index('apply_migration "$MIGRATION_206"', irreversible)

        self.assertLess(maintenance, first_drain)
        self.assertLess(first_drain, stop_jvm)
        self.assertLess(stop_jvm, second_drain)
        self.assertLess(second_drain, database_idle)
        self.assertLess(database_idle, precheck)
        self.assertLess(precheck, irreversible)
        self.assertLess(irreversible, migration)

    def test_unknown_running_noon_domain_fails_closed(self):
        contract = self.drain_contract()

        self.assertIn("data_domain IS NULL", contract)
        for safe_domain in (
            "SALES",
            "ORDER",
            "FINANCE_TRANSACTION",
            "NOON_ADVERTISING",
            "OFFICIAL_WAREHOUSE_INVENTORY",
            "OFFICIAL_WAREHOUSE_FBN_RECEIVED",
        ):
            self.assertIn(f"'{safe_domain}'", contract)
        self.assertIn("data_domain NOT IN", contract)

    def test_only_active_recovery_and_reauthentication_work_blocks_schema_cutover(self):
        contract = self.drain_contract()

        self.assertIn("lease_owner IS NOT NULL", contract)
        self.assertIn("lease_token IS NOT NULL", contract)
        self.assertIn("lease_until > NOW()", contract)
        self.assertIn("status = 'VALIDATING'", contract)
        self.assertIn("status = 'VERIFYING'", contract)
        self.assertNotIn("status NOT IN ('COMPLETED', 'FAILED_FINAL', 'CANCELLED')", contract)
        self.assertNotIn("status IN ('PENDING', 'VALIDATING')", contract)
        self.assertNotIn("status IN ('PENDING', 'VERIFYING')", contract)

    def test_expired_consistent_recovery_lease_is_preserved_but_malformed_lease_fails_closed(self):
        contract = self.drain_contract()

        self.assertIn("WHERE lease_until > NOW()", contract)
        self.assertIn("(lease_owner IS NULL) <> (lease_token IS NULL)", contract)
        self.assertIn("lease_until IS NULL", contract)
        self.assertNotIn(
            "WHERE lease_owner IS NOT NULL\n"
            "            OR lease_token IS NOT NULL\n"
            "            OR lease_until > NOW()",
            contract,
        )


if __name__ == "__main__":
    unittest.main()
