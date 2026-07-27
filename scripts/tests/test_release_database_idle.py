import importlib.util
import subprocess
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "release_cutover_maintenance.py"


def load_module():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleaseDatabaseIdleTest(unittest.TestCase):
    def build_script(self):
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
            release_name="database-idle-test",
            external_health_url="https://www.nuoon.com/ai/actuator/health",
            app_dir="/app",
        )

    def run_probe(self, db_scalar_body, mysql_body):
        script = self.build_script()
        definitions = script[: script.index("trap handle_irreversible_failure ERR")]
        harness = (
            definitions
            + "\ndb_scalar() {\n"
            + db_scalar_body
            + "\n}\nmysql() {\n"
            + mysql_body
            + "\n}\nassert_database_idle\n"
        )
        return subprocess.run(
            ["bash"],
            input=harness,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_bounded_explicit_lock_proof_precedes_206(self):
        script = self.build_script()
        execution = script[script.index("trap handle_irreversible_failure ERR") :]

        runtime_stop = execution.index('stop_pid "$ACTIVE_PID"')
        idle_check = execution.index("assert_database_idle", runtime_stop)
        irreversible_start = execution.index("IRREVERSIBLE_STARTED=1")

        self.assertLess(runtime_stop, idle_check)
        self.assertLess(idle_check, irreversible_start)
        self.assertIn("SET SESSION lock_wait_timeout = 5", script)
        self.assertIn("SET SESSION innodb_lock_wait_timeout = 5", script)
        self.assertIn("SET autocommit = 0", script)
        self.assertIn("LOCK TABLES product_barcode WRITE, product_variant READ", script)
        self.assertIn("@@innodb_table_locks = 1", script)
        self.assertIn("COMMIT", script)
        self.assertIn("UNLOCK TABLES", script)

    def test_least_privilege_requires_explicit_lock_proof(self):
        result = self.run_probe(
            """
case "$1" in
  *information_schema.innodb_trx*) return 1 ;;
  *performance_schema*) return 1 ;;
  *) printf '0\\n' ;;
esac
""",
            "printf 'ACQUIRED\\n'",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(
            "PERFORMANCE_SCHEMA_LOCK_EVIDENCE=UNAVAILABLE_EXPLICIT_LOCK_REQUIRED",
            result.stdout,
        )
        self.assertIn(
            "INNODB_TRANSACTION_EVIDENCE=UNAVAILABLE_EXPLICIT_LOCK_REQUIRED",
            result.stdout,
        )
        self.assertIn("DATABASE_TABLE_LOCK_PROBE=ACQUIRED_AND_RELEASED", result.stdout)
        self.assertIn("DATABASE_LOCK_BLOCKERS=0", result.stdout)

    def test_lock_acquisition_failure_is_closed(self):
        result = self.run_probe(
            """
case "$1" in
  *performance_schema*) return 1 ;;
  *) printf '0\\n' ;;
esac
""",
            "return 1",
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("DATABASE_TABLE_LOCK_PROBE=FAILED", result.stdout)
        self.assertNotIn("DATABASE_LOCK_BLOCKERS=0", result.stdout)

    def test_visible_activity_is_closed(self):
        result = self.run_probe(
            """
case "$1" in
  *information_schema.processlist*) printf '1\\n' ;;
  *) printf '0\\n' ;;
esac
""",
            "printf 'ACQUIRED\\n'",
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("INFORMATION_SCHEMA_IDLE_BLOCKERS=1", result.stdout)
        self.assertNotIn("DATABASE_TABLE_LOCK_PROBE=ACQUIRED_AND_RELEASED", result.stdout)

    def test_performance_schema_wait_is_closed(self):
        result = self.run_probe(
            """
case "$1" in
  *performance_schema*) printf '1\\n' ;;
  *) printf '0\\n' ;;
esac
""",
            "printf 'ACQUIRED\\n'",
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("PERFORMANCE_SCHEMA_LOCK_BLOCKERS=1", result.stdout)
        self.assertNotIn("DATABASE_TABLE_LOCK_PROBE=ACQUIRED_AND_RELEASED", result.stdout)


if __name__ == "__main__":
    unittest.main()
