import importlib.util
import subprocess
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
MODULE_PATH = SCRIPT_DIR / "release_cutover_maintenance.py"


def load_module():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def build_script(mode="LEGACY_DEFAULT"):
    return load_module().build_single_scheduler_cutover_script(
        staged_jar="/staged/backend.jar",
        expected_jar_sha256="a" * 64,
        expected_commit="c" * 40,
        expected_active_jar_sha256="b" * 64,
        expected_active_pid=4242,
        expected_nginx_upstream_sha256="d" * 64,
        expected_topology_cas_sha256="e" * 64,
        expected_dp_execution_mode=mode,
        preserve_dp_legacy=True,
        active_slot="blue",
        target_slot="green",
        active_port=18087,
        target_port=18088,
        maintenance_port=18089,
        nginx_upstream_file="/managed/upstream.inc",
        release_name="legacy-preserving-cutover-test",
        external_health_url="https://www.nuoon.com/ai/actuator/health",
        app_dir="/app",
    )


class ReleaseLegacySingleSchedulerCutoverTest(unittest.TestCase):
    def test_rendered_script_is_valid_bash(self):
        result = subprocess.run(
            ["bash", "-n"],
            input=build_script(),
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_explicit_legacy_mode_omits_every_dp_activation_and_database_path(self):
        script = build_script()

        self.assertIn("PRESERVE_DP_LEGACY=1", script)
        self.assertIn("EXPECTED_DP_EXECUTION_MODE=LEGACY_DEFAULT", script)
        self.assertIn("DP_RELEASE_MODE PRESERVE_LEGACY", script)
        self.assertIn("DP_DATA_WRITE_COUNT 0", script)
        for forbidden in (
            "prepare_dp_runtime_cutover",
            "finalize_dp_runtime_legacy_cutover",
            "bootstrap_dp_runtime_cutover",
            "run_dp_runtime_cutover_manifest",
            "run_dp10_openapi_probe",
            "run_dp_report_download_probe",
            "NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME",
            "NUONO_MANAGED_DP_RELEASE=1",
            ".migration.cnf",
            "dp_pull_schedule_cutover",
            "mysql --defaults",
        ):
            self.assertNotIn(forbidden, script)

    def test_single_scheduler_and_maintenance_order_is_preserved_without_dp_writes(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        maintenance = execution.index("start_maintenance_responder")
        route_maintenance = execution.index("switch_nginx_to_maintenance", maintenance)
        stop = execution.index('stop_pid "$ACTIVE_PID"', route_maintenance)
        no_jvm = execution.index("assert_no_backend_jvms", stop)
        start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"', no_jvm)
        route_target = execution.index('switch_nginx_to_port "$TARGET_PORT"', start)

        self.assertLess(maintenance, route_maintenance)
        self.assertLess(route_maintenance, stop)
        self.assertLess(stop, no_jvm)
        self.assertLess(no_jvm, start)
        self.assertLess(start, route_target)

    def test_active_and_target_processes_are_bound_to_the_same_observed_legacy_mode(self):
        script = build_script("LEGACY")
        execution = script.split("\nvalidate_cutover\n", 1)[1]

        self.assertIn("EXPECTED_DP_EXECUTION_MODE=LEGACY", script)
        self.assertGreaterEqual(
            script.count('legacy_process_mode "$ACTIVE_PID"'), 2
        )
        self.assertIn('legacy_process_mode "$NEW_PID"', script)
        self.assertIn('assert_legacy_env_contract "$APP_DIR/.env"', script)
        self.assertIn('assert_legacy_env_contract "$TARGET_SLOT_DIR/.env"', script)
        self.assertLess(
            execution.index('legacy_process_mode "$ACTIVE_PID"'),
            execution.index('stop_pid "$ACTIVE_PID"'),
        )

    def test_target_env_is_copied_and_only_slot_runtime_paths_are_appended(self):
        script = build_script()
        prepare = script[
            script.index("prepare_target_runtime_payloads()"):
            script.index("assert_source_payloads()")
        ]

        self.assertIn('secure_file_operation install "$APP_DIR/.env"', prepare)
        self.assertIn("NUONO_NEXT_APP_DIR=%s", prepare)
        self.assertIn("NUONO_NEXT_PORT=%s", prepare)
        self.assertNotIn("NUONO_DATA_PULL_EXECUTION_MODE", prepare)
        self.assertNotIn("NUONO_DP_RUNTIME", prepare)

    def test_non_legacy_observation_is_rejected_before_rendering(self):
        with self.assertRaisesRegex(ValueError, "observed LEGACY mode"):
            build_script("RUNTIME")


if __name__ == "__main__":
    unittest.main()
