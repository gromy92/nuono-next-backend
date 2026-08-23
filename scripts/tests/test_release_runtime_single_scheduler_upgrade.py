import hashlib
import importlib.util
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
        expected_dp_execution_mode="RUNTIME",
        preserve_dp_runtime=True,
        active_slot="blue",
        target_slot="green",
        active_port=18087,
        target_port=18088,
        maintenance_port=18089,
        nginx_upstream_file="/managed/upstream.inc",
        release_name="runtime-preserving-upgrade-test",
        external_health_url="https://www.nuoon.com/ai/actuator/health",
        app_dir="/app",
    )


class ReleaseRuntimeSingleSchedulerUpgradeTest(unittest.TestCase):
    def test_active_slot_environment_is_the_runtime_upgrade_source(self):
        script = build_script()
        execution = script.split("\nvalidate_cutover\n", 1)[1]

        self.assertIn('SOURCE_ENV_FILE="$ACTIVE_RUN_DIR/.env"', execution)
        self.assertIn('SOURCE_ENV_SHA256="$ACTIVE_ENV_SHA256"', execution)
        self.assertIn(
            'prepare_legacy_base_env "$SOURCE_ENV_FILE" "$SOURCE_ENV_SHA256"',
            execution,
        )
        self.assertNotIn(
            'prepare_legacy_base_env "$APP_DIR/.env"',
            execution,
        )

    def test_script_is_valid_bash_and_preserves_existing_dp_data(self):
        script = build_script()
        result = subprocess.run(
            ["bash", "-n"], input=script, text=True, capture_output=True, check=False
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("PRESERVE_DP_MODE=1", script)
        self.assertIn("PRESERVE_DP_LEGACY=0", script)
        self.assertIn("EXPECTED_DP_EXECUTION_MODE=RUNTIME", script)
        self.assertIn("DP_RELEASE_MODE PRESERVE_RUNTIME", script)
        self.assertIn("DP_DATA_WRITE_COUNT 0", script)
        self.assertIn("dp_runtime_health_status", script)
        self.assertIn("DP_RUNTIME_HEALTH UP", script)
        for forbidden in (
            "prepare_dp_runtime_cutover",
            "finalize_dp_runtime_legacy_cutover",
            "bootstrap_dp_runtime_cutover",
            "run_dp_runtime_cutover_manifest",
            "run_dp10_openapi_probe",
            "run_dp_report_download_probe",
            ".migration.cnf",
            "dp_pull_schedule_cutover",
            "mysql --defaults",
        ):
            self.assertNotIn(forbidden, script)

    def test_single_scheduler_order_does_not_mutate_dp(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        maintenance = execution.index("start_maintenance_responder")
        stop = execution.index('stop_pid "$ACTIVE_PID"', maintenance)
        no_jvm = execution.index("assert_no_backend_jvms", stop)
        start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"', no_jvm)
        health = execution.index("assert_target_release_ready", start)
        route_target = execution.index('switch_nginx_to_port "$TARGET_PORT"', health)

        self.assertLess(maintenance, stop)
        self.assertLess(stop, no_jvm)
        self.assertLess(no_jvm, start)
        self.assertLess(start, health)
        self.assertLess(health, route_target)

    def test_existing_runtime_environment_is_copied_exactly(self):
        script = build_script()
        helper = script[
            script.index("legacy_env_mode()"):
            script.index("prepare_target_runtime_payloads()")
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            root.chmod(0o700)
            source = root / "source.env"
            target = root / "preserved.env"
            payload = (
                "NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME\n"
                "NUONO_DP10_OPEN_API_PROBE_CANARY_OWNER_USER_ID=307\n"
                "NUONO_DP10_OPEN_API_PROBE_CANARY_PROVIDER_ACCOUNT_ID=42\n"
            )
            source.write_text(payload, encoding="utf-8")
            source.chmod(0o600)
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            command = (
                helper
                + '\nEXPECTED_DP_EXECUTION_MODE=RUNTIME\n'
                + 'assert_legacy_source_env_contract "$1"\n'
                + 'prepare_legacy_base_env "$1" "$2" "$3"\n'
                + 'assert_legacy_target_env_contract "$3"\n'
                + 'printf "%s\\n%s\\n" "$LEGACY_CANARY_DISPOSITION" "$LEGACY_BASE_ENV_SHA256"'
            )
            result = subprocess.run(
                ["bash", "-c", command, "bash", str(source), digest, str(target)],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(payload, target.read_text(encoding="utf-8"))
            self.assertEqual("PRESERVED_RUNTIME_ENVIRONMENT", result.stdout.splitlines()[0])
            self.assertEqual(digest, result.stdout.splitlines()[1])

    def test_runtime_and_legacy_preservation_are_mutually_exclusive(self):
        with self.assertRaisesRegex(ValueError, "only one DP preservation mode"):
            load_module().build_single_scheduler_cutover_script(
                preserve_dp_legacy=True,
                preserve_dp_runtime=True,
            )


if __name__ == "__main__":
    unittest.main()
