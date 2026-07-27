import importlib.util
import sqlite3
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

    def noon_schema_blocker_count(self, rows):
        contract = self.drain_contract()
        start = contract.index("(SELECT COUNT(*) FROM noon_pull_task")
        end = contract.index("+ (SELECT COUNT(*) FROM noon_auth_identity_recovery", start)
        query = contract[start:end].strip()[1:-1].replace("b'0'", "0")
        connection = sqlite3.connect(":memory:")
        self.addCleanup(connection.close)
        connection.execute(
            """
            CREATE TABLE noon_pull_task (
              status TEXT,
              data_domain TEXT,
              locked_by TEXT,
              is_deleted INTEGER
            )
            """
        )
        connection.executemany(
            """
            INSERT INTO noon_pull_task(status, data_domain, locked_by, is_deleted)
            VALUES (?, ?, ?, 0)
            """,
            rows,
        )
        return connection.execute(query).fetchone()[0]

    def test_terminal_product_lock_history_is_not_schema_writing_work(self):
        rows = [
            (status, "PRODUCT", "historical-worker")
            for status in (
                "FAILED",
                "PARTIAL",
                "SUCCEEDED",
                "SKIPPED",
                "CANCELLED",
                "BLOCKED_AUTH",
            )
        ]

        self.assertEqual(0, self.noon_schema_blocker_count(rows))

    def test_executable_product_and_unknown_domain_tasks_block_schema_cutover(self):
        rows = [
            ("QUEUED", "PRODUCT", None),
            ("RUNNING", "PRODUCT", "active-worker"),
            ("QUEUED", None, None),
            ("RUNNING", "FUTURE_SCHEMA_DOMAIN", "active-worker"),
        ]

        self.assertEqual(4, self.noon_schema_blocker_count(rows))

    def test_known_non_product_backlog_is_not_schema_writing_work(self):
        rows = [
            (status, domain, "historical-or-report-worker")
            for domain in (
                "SALES",
                "ORDER",
                "FINANCE_TRANSACTION",
                "NOON_ADVERTISING",
                "OFFICIAL_WAREHOUSE_INVENTORY",
                "OFFICIAL_WAREHOUSE_FBN_RECEIVED",
            )
            for status in ("QUEUED", "RUNNING")
        ]

        self.assertEqual(0, self.noon_schema_blocker_count(rows))

    def test_non_product_noon_backlog_is_preserved_without_becoming_a_schema_blocker(self):
        contract = self.drain_contract()

        self.assertIn("data_domain = 'PRODUCT'", contract)
        self.assertIn("status IN ('QUEUED', 'RUNNING')", contract)
        self.assertNotIn("(status = 'RUNNING' OR locked_by IS NOT NULL)", contract)
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
