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
