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
    def test_runtime_upgrade_reattests_candidate_from_canonical_environment(self):
        script = build_script()
        execution = script.split("\nvalidate_cutover\n", 1)[1]

        self.assertIn('SOURCE_ENV_FILE="$ACTIVE_RUN_DIR/.env"', execution)
        self.assertIn('SOURCE_ENV_SHA256="$ACTIVE_ENV_SHA256"', execution)
        self.assertIn(
            'prepare_dp_runtime_base_env "$APP_DIR/.env"',
            script,
        )
        self.assertIn("run_dp10_openapi_probe", execution)
        self.assertIn("persist_dp10_probe_for_target", execution)
        self.assertIn("prepare_dp10_probe_runtime_environment", execution)
        self.assertIn("NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT=%s", script)
        self.assertIn("NUONO_DP_RUNTIME_RELEASE_SCHEMA_BINDING_SHA256=%s", script)
        self.assertIn("NUONO_DP_RUNTIME_RELEASE_CUTOVER_BINDING_SHA256=%s", script)
        self.assertNotIn("prepare_legacy_base_env", execution)

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
        self.assertIn('DP_RUNTIME_HEALTH "$(dp_runtime_health_status)"', script)
        self.assertIn("run_dp10_openapi_probe", script)
        self.assertIn("capture_dp_runtime_database_binding", script)
        self.assertIn("verify_dp_runtime_database_binding", script)
        execution = script.split("\nvalidate_cutover\n", 1)[1]
        for forbidden in (
            "prepare_dp_runtime_cutover",
            "finalize_dp_runtime_legacy_cutover",
            "bootstrap_dp_runtime_cutover",
            "run_dp_runtime_cutover_manifest",
            "run_dp_report_download_probe",
            "UPDATE dp_pull_",
            "INSERT INTO dp_pull_",
        ):
            self.assertNotIn(forbidden, execution)

    def test_single_scheduler_order_does_not_mutate_dp(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        probe = execution.index("run_dp10_openapi_probe")
        maintenance = execution.index("start_maintenance_responder")
        stop = execution.index('stop_pid "$ACTIVE_PID"', maintenance)
        no_jvm = execution.index("assert_no_backend_jvms", stop)
        start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"', no_jvm)
        health = execution.index("assert_target_release_ready", start)
        route_target = execution.index('switch_nginx_to_port "$TARGET_PORT"', health)

        self.assertLess(probe, maintenance)
        self.assertLess(maintenance, stop)
        self.assertLess(stop, no_jvm)
        self.assertLess(no_jvm, start)
        self.assertLess(start, health)
        self.assertLess(health, route_target)

    def test_candidate_evidence_is_bound_before_old_runtime_stops(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        probe = execution.index("run_dp10_openapi_probe")
        persist = execution.index("persist_dp10_probe_for_target", probe)
        prepare_env = execution.index("prepare_dp10_probe_runtime_environment", persist)
        verify = execution.index("verify_dp10_probe_state", prepare_env)
        stop = execution.index('stop_pid "$ACTIVE_PID"', verify)

        self.assertLess(probe, persist)
        self.assertLess(persist, prepare_env)
        self.assertLess(prepare_env, verify)
        self.assertLess(verify, stop)

    def test_runtime_and_legacy_preservation_are_mutually_exclusive(self):
        with self.assertRaisesRegex(ValueError, "only one DP preservation mode"):
            load_module().build_single_scheduler_cutover_script(
                preserve_dp_legacy=True,
                preserve_dp_runtime=True,
            )


if __name__ == "__main__":
    unittest.main()
