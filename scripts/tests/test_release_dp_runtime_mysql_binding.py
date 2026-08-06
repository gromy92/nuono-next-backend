import importlib.util
import shlex
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
MODULE_PATH = SCRIPT_DIR / "release_cutover_maintenance.py"


def load_module():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def build_script():
    return load_module().build_single_scheduler_cutover_script(
        staged_jar="/staged/backend.jar",
        expected_jar_sha256="a" * 64,
        expected_commit="c" * 40,
        expected_active_jar_sha256="b" * 64,
        expected_active_pid=4242,
        expected_nginx_upstream_sha256="d" * 64,
        expected_topology_cas_sha256="e" * 64,
        active_slot="blue",
        target_slot="green",
        active_port=18087,
        target_port=18088,
        maintenance_port=18089,
        nginx_upstream_file="/managed/upstream.inc",
        release_name="dp-runtime-mysql-binding-test",
        external_health_url="https://www.nuoon.com/ai/actuator/health",
        app_dir="/app",
    )


def cutover_fragment():
    script = build_script()
    return script[
        script.index('DP_RUNTIME_MYSQL_CNF="$APP_DIR/.migration.cnf"'):
        script.index("dp_runtime_health_status()")
    ]


class ReleaseDpRuntimeMysqlBindingTest(unittest.TestCase):
    def test_probe_binds_credentials_to_explicit_rds_tcp_target(self):
        with tempfile.TemporaryDirectory() as raw_root:
            root = Path(raw_root)
            capture = root / "mysql-args.txt"
            (root / ".env").write_text(
                "NUONO_NEXT_DB_URL='jdbc:mysql://db.example.internal:3307/nuonuoai?useSSL=true'\n",
                encoding="utf-8",
            )
            (root / ".migration.cnf").write_text(
                "[client]\nuser=release\npassword=secret\n", encoding="utf-8"
            )
            body = f"""
mysql() {{ printf '%s\\n' "$@" > {shlex.quote(str(capture))}; printf '1\\n'; }}
prepare_dp_runtime_database_target
dp_runtime_db_scalar 'SELECT 1'
"""
            result = subprocess.run(
                ["bash", "-c", "\n".join((
                    "set -Eeuo pipefail",
                    f"APP_DIR={shlex.quote(str(root))}",
                    f"BACKUP_DIR={shlex.quote(str(root / 'backup'))}",
                    "STAGED_JAR=/staged/backend.jar",
                    "EXPECTED_COMMIT=" + "c" * 40,
                    "EXPECTED_JAR_SHA256=" + "a" * 64,
                    cutover_fragment(),
                    body,
                ))],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            arguments = capture.read_text(encoding="utf-8").splitlines()
            self.assertIn(f"--defaults-extra-file={root / '.migration.cnf'}", arguments)
            self.assertIn("--skip-reconnect", arguments)
            self.assertIn("--protocol=TCP", arguments)
            self.assertIn("--host=db.example.internal", arguments)
            self.assertIn("--port=3307", arguments)
            self.assertIn("--database=nuonuoai", arguments)

    def test_bootstrap_reuses_the_same_explicit_database_binding(self):
        script = build_script()

        self.assertIn(
            'dp_runtime_mysql --batch --raw < "$DP_RUNTIME_BOOTSTRAP_SQL"', script
        )
        self.assertEqual(1, script.count("dp_runtime_mysql()"))

    def test_database_url_credentials_are_rejected_before_mysql(self):
        with tempfile.TemporaryDirectory() as raw_root:
            root = Path(raw_root)
            (root / ".env").write_text(
                "NUONO_NEXT_DB_URL=jdbc:mysql://user:password@db.example/nuonuoai\n",
                encoding="utf-8",
            )
            (root / ".migration.cnf").write_text("[client]\n", encoding="utf-8")
            result = subprocess.run(
                ["bash", "-c", "\n".join((
                    "set -Eeuo pipefail",
                    f"APP_DIR={shlex.quote(str(root))}",
                    f"BACKUP_DIR={shlex.quote(str(root / 'backup'))}",
                    "STAGED_JAR=/staged/backend.jar",
                    "EXPECTED_COMMIT=" + "c" * 40,
                    "EXPECTED_JAR_SHA256=" + "a" * 64,
                    cutover_fragment(),
                    "prepare_dp_runtime_database_target",
                ))],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("must not contain credentials", result.stderr)


if __name__ == "__main__":
    unittest.main()
