import importlib.util
import json
import socket
import subprocess
import tempfile
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "release_cutover_maintenance.py"


def load_module():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleaseCutoverMaintenanceTest(unittest.TestCase):
    def build_additive_script(self):
        module = load_module()
        return module.build_additive_schema_migration_script(
            staged_jar="/staged/backend.jar",
            expected_jar_sha256="a" * 64,
            expected_commit="b" * 40,
            expected_182_sha256="1" * 64,
            expected_190_sha256="c" * 64,
            expected_204_sha256="d" * 64,
            expected_205_sha256="e" * 64,
            app_dir="/app",
            release_name="schema-cutover-test",
        )

    def build_irreversible_script(self):
        module = load_module()
        return module.build_irreversible_schema_cutover_script(
            expected_jar_sha256="a" * 64,
            expected_commit="b" * 40,
            expected_182_sha256="1" * 64,
            expected_206_sha256="c" * 64,
            active_slot="green",
            active_port=18088,
            standby_port=18087,
            maintenance_port=18089,
            nginx_upstream_file="/managed/upstream.inc",
            release_name="schema-cutover-test",
            external_health_url="https://www.nuoon.com/ai/actuator/health",
            app_dir="/app",
        )

    def build_script(self):
        module = load_module()
        return module.build_single_scheduler_cutover_script(
            staged_jar="/staged/backend.jar",
            expected_jar_sha256="a" * 64,
            expected_active_jar_sha256="b" * 64,
            active_slot="blue",
            target_slot="green",
            active_port=18087,
            target_port=18088,
            maintenance_port=18089,
            nginx_upstream_file="/managed/upstream.inc",
            release_name="availability-test",
            external_health_url="https://www.nuoon.com/ai/actuator/health",
            app_dir="/app",
        )

    def test_maintenance_owns_nginx_before_old_jvm_stops(self):
        script = self.build_script()
        execution = script[script.index("trap rollback_cutover ERR") :]

        responder = execution.index("start_maintenance_responder")
        maintenance_switch = execution.index("switch_nginx_to_maintenance")
        old_stop = execution.index('kill "$ACTIVE_PID"')
        target_start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"')
        target_ready = execution.index('wait_for_health "$TARGET_PORT"')
        target_switch = execution.index('switch_nginx_to_port "$TARGET_PORT"')
        maintenance_stop = execution.index("stop_maintenance_responder", target_switch)

        self.assertLess(responder, maintenance_switch)
        self.assertLess(maintenance_switch, old_stop)
        self.assertLess(old_stop, target_start)
        self.assertLess(target_start, target_ready)
        self.assertLess(target_ready, target_switch)
        self.assertLess(target_switch, maintenance_stop)
        self.assertIn('NGINX_CURRENT_PORT "$MAINTENANCE_PORT"', script)
        self.assertIn("SINGLE_SCHEDULER_GUARD PASS", execution)

    def test_additive_migrations_are_ordered_and_postchecked(self):
        script = self.build_additive_script()
        execution = script[script.index("validate_additive_migrations") :]

        prerequisite = execution.index("require_migration_190")
        migration_204 = execution.index('apply_migration "$MIGRATION_204"')
        postcheck_204 = execution.index("postcheck_migration_204")
        migration_205 = execution.index('apply_migration "$MIGRATION_205"')
        postcheck_205 = execution.index("postcheck_migration_205")

        self.assertLess(prerequisite, migration_204)
        self.assertLess(migration_204, postcheck_204)
        self.assertLess(postcheck_204, migration_205)
        self.assertLess(migration_205, postcheck_205)
        self.assertIn("ADDITIVE_SCHEMA_RESULT PASS", execution)
        self.assertNotIn("206_product_barcode_store_uniqueness.sql", script)

    def test_failure_after_206_starts_keeps_maintenance_for_repair_forward(self):
        script = self.build_irreversible_script()
        failure_handler = script[
            script.index("handle_irreversible_failure()")
            : script.index("validate_irreversible_cutover()")
        ]
        irreversible_branch = failure_handler[
            failure_handler.index('if [ "$IRREVERSIBLE_STARTED" = 1 ]')
            : failure_handler.index("fi", failure_handler.index('if [ "$IRREVERSIBLE_STARTED" = 1 ]'))
        ]

        self.assertIn("IRREVERSIBLE_SCHEMA_RESULT REPAIR_FORWARD_REQUIRED", irreversible_branch)
        self.assertIn("MAINTENANCE_STATUS HELD", irreversible_branch)
        self.assertIn("ensure_repair_forward_maintenance", irreversible_branch)
        self.assertIn("MAINTENANCE_STATUS ROUTE_REPAIR_REQUIRED", irreversible_branch)
        self.assertNotIn("restart_same_new_runtime", irreversible_branch)
        self.assertNotIn("switch_nginx_to_active", irreversible_branch)

    def test_failure_before_206_restores_only_the_same_new_jar(self):
        script = self.build_irreversible_script()
        failure_handler = script[
            script.index("handle_irreversible_failure()")
            : script.index("validate_irreversible_cutover()")
        ]

        self.assertIn("restart_same_new_runtime", failure_handler)
        self.assertIn("switch_nginx_to_active", failure_handler)
        self.assertIn("stop_maintenance_responder", failure_handler)
        self.assertIn('MAINTENANCE_STARTED" = 0', failure_handler)
        self.assertIn("FAILED_BEFORE_206", failure_handler)
        self.assertNotIn("restart_old_runtime", failure_handler)
        self.assertNotIn("EXPECTED_ACTIVE_JAR_SHA256", script)

    def test_undrained_work_fails_before_runtime_stop_and_migration_206(self):
        script = self.build_irreversible_script()
        execution = script[script.index("trap handle_irreversible_failure ERR") :]

        maintenance_switch = execution.index("switch_nginx_to_maintenance")
        first_drain = execution.index("assert_drained", maintenance_switch)
        runtime_stop = execution.index('stop_pid "$ACTIVE_PID"')
        second_drain = execution.index("assert_drained", runtime_stop)
        irreversible_start = execution.index("IRREVERSIBLE_STARTED=1")

        self.assertLess(maintenance_switch, first_drain)
        self.assertLess(first_drain, runtime_stop)
        self.assertLess(runtime_stop, second_drain)
        self.assertLess(second_drain, irreversible_start)
        self.assertIn("product_listing_task", script)
        self.assertIn("product_publish_task", script)
        self.assertIn("product_delete_write_retry_scheduled", script)
        self.assertIn("$.rebuildAction", script)
        self.assertIn("listing_running", script)
        self.assertIn("product_image_suite", script)
        self.assertIn("noon_pull_task", script)
        self.assertIn("noon_auth_identity_recovery", script)
        self.assertIn("lease_owner IS NOT NULL", script)

    def test_migration_or_postcheck_failure_enters_repair_forward_with_503_held(self):
        script = self.build_irreversible_script()
        execution = script[script.index("trap handle_irreversible_failure ERR") :]

        irreversible_start = execution.index("IRREVERSIBLE_STARTED=1")
        migration = execution.index('apply_migration "$MIGRATION_206"')
        postcheck = execution.index("postcheck_migration_206")
        restart = execution.index("restart_same_new_runtime")

        self.assertLess(irreversible_start, migration)
        self.assertLess(migration, postcheck)
        self.assertLess(postcheck, restart)
        self.assertIn("REPAIR_FORWARD_REQUIRED", script)
        self.assertIn("MAINTENANCE_STATUS HELD", script)

    def test_success_restarts_the_exact_same_new_jar_before_traffic_returns(self):
        script = self.build_irreversible_script()
        restart_function = script[
            script.index("restart_same_new_runtime()")
            : script.index("handle_irreversible_failure()")
        ]
        execution = script[script.index("trap handle_irreversible_failure ERR") :]

        postcheck = execution.index("postcheck_migration_206")
        restart = execution.index("restart_same_new_runtime")
        active_switch = execution.index("switch_nginx_to_active")

        self.assertIn('ACTIVE_JAR="$ACTIVE_RUN_DIR/$JAR_NAME"', script)
        self.assertIn('"$(sha256_file "$ACTIVE_JAR")" = "$EXPECTED_JAR_SHA256"', restart_function)
        self.assertIn('process_uses_jar "$NEW_PID" "$ACTIVE_JAR"', restart_function)
        self.assertLess(postcheck, restart)
        self.assertLess(restart, active_switch)

    def test_generated_schema_scripts_are_valid_bash(self):
        for script in (self.build_additive_script(), self.build_irreversible_script()):
            with self.subTest(script=script.splitlines()[2]):
                result = subprocess.run(
                    ["bash", "-n"],
                    input=script,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertEqual(0, result.returncode, result.stderr)

    def test_maintenance_response_is_controlled_json_503(self):
        script = self.build_script()

        self.assertIn('self.send_response(503, "Service Unavailable")', script)
        self.assertIn('self.send_header("Content-Type", "application/json; charset=utf-8")', script)
        self.assertIn('self.send_header("Retry-After", "5")', script)
        self.assertIn("服务正在更新，请稍后重试", script)
        self.assertIn("127.0.0.1", script)
        self.assertIn('maintenance_status="$(maintenance_response_status)"', script)
        self.assertIn('[ "$maintenance_status" = "503" ]', script)
        self.assertIn('external_status="$(external_maintenance_status)"', script)
        self.assertIn('[ "$external_status" = "503" ]', script)

    def test_embedded_responder_serves_json_503_on_loopback(self):
        script = self.build_script()
        marker = 'cat > "$MAINTENANCE_DIR/server.py" <<\'PY\'\n'
        server_source = script.split(marker, 1)[1].split("\nPY\n", 1)[0]
        with socket.socket() as reserved:
            reserved.bind(("127.0.0.1", 0))
            port = reserved.getsockname()[1]
        with tempfile.TemporaryDirectory() as directory:
            server_path = Path(directory) / "server.py"
            server_path.write_text(server_source, encoding="utf-8")
            process = subprocess.Popen(
                ["python3", str(server_path), str(port)],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            try:
                for _ in range(30):
                    try:
                        urllib.request.urlopen(f"http://127.0.0.1:{port}/api/probe", timeout=1)
                    except urllib.error.HTTPError as error:
                        if error.code == 503:
                            payload = json.loads(error.read().decode())
                            self.assertEqual("application/json; charset=utf-8", error.headers["Content-Type"])
                            self.assertEqual("5", error.headers["Retry-After"])
                            error.close()
                            self.assertEqual(
                                {"status": 503, "message": "服务正在更新，请稍后重试"},
                                payload,
                            )
                            break
                        error.close()
                    except OSError:
                        time.sleep(0.05)
                else:
                    self.fail("maintenance responder did not return JSON 503")
            finally:
                process.terminate()
                process.wait(timeout=5)

    def test_rollback_routes_to_maintenance_before_stopping_new_runtime(self):
        script = self.build_script()
        rollback = script[script.index("rollback_cutover()") : script.index("validate_cutover()")]

        maintenance_switch = rollback.index("switch_nginx_to_maintenance")
        new_stop = rollback.index("stop_target_runtime")
        old_restart = rollback.index("restart_old_runtime")
        old_ready = rollback.index('wait_for_health "$ACTIVE_PORT"')
        old_switch = rollback.index("restore_nginx_to_active")
        maintenance_stop = rollback.index("stop_maintenance_responder")

        self.assertIn("BLOCKED_MAINTENANCE_UNAVAILABLE", rollback[:new_stop])
        self.assertNotIn("switch_nginx_to_maintenance || true", rollback)
        self.assertLess(maintenance_switch, new_stop)
        self.assertLess(new_stop, old_restart)
        self.assertLess(old_restart, old_ready)
        self.assertLess(old_ready, old_switch)
        self.assertLess(old_switch, maintenance_stop)

    def test_generated_cutover_script_is_valid_bash(self):
        result = subprocess.run(
            ["bash", "-n"],
            input=self.build_script(),
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
