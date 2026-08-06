import hashlib
import socket
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

from scripts.tests.test_release_maintenance_probe import (
    build_script,
    function_from,
)


def reserve_port():
    with socket.socket() as reserved:
        reserved.bind(("127.0.0.1", 0))
        return reserved.getsockname()[1]


def start_listener(server_path, port, jar_path):
    server_path.write_text(
        """import socket, sys
server = socket.socket()
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind((\"127.0.0.1\", int(sys.argv[1])))
server.listen()
while True:
    connection, _ = server.accept()
    connection.close()
""",
        encoding="utf-8",
    )
    process = subprocess.Popen(
        ["python3", str(server_path), str(port), "-jar", str(jar_path)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    for _ in range(40):
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                return process
        except OSError:
            time.sleep(0.05)
    process.terminate()
    process.wait(timeout=5)
    raise AssertionError("temporary listener did not start")


def listener_identity_functions(script):
    secure_files = function_from(
        script, "secure_file_operation", "assert_dp10_probe_marker"
    )
    listeners = function_from(script, "bind_trusted_lsof", "process_jar_path")
    identity = function_from(script, "process_jar_path", "restart_old_runtime")
    return secure_files + listeners + identity


class ReleasePredecessorRollbackTest(unittest.TestCase):
    def test_exact_listener_identity_rejects_python_jar_argument_masquerade(self):
        functions = listener_identity_functions(build_script())
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            expected_jar = directory / "predecessor.jar"
            expected_jar.write_bytes(b"expected predecessor")
            expected_sha = hashlib.sha256(expected_jar.read_bytes()).hexdigest()
            port = reserve_port()
            process = start_listener(directory / "listener.py", port, expected_jar)
            try:
                exact = self.run_identity(functions, port, expected_jar, expected_sha)
                self.assertNotEqual(0, exact.returncode)
                self.assertIsNone(process.poll())
            finally:
                process.terminate()
                process.wait(timeout=5)

    def test_rollback_keeps_maintenance_for_a_stranger_listener(self):
        script = build_script()
        identity = listener_identity_functions(script)
        rollback = function_from(script, "rollback_cutover", "validate_cutover")
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            expected_jar = directory / "predecessor.jar"
            stranger_jar = directory / "stranger.jar"
            expected_jar.write_bytes(b"expected predecessor")
            stranger_jar.write_bytes(b"stranger runtime")
            expected_sha = hashlib.sha256(expected_jar.read_bytes()).hexdigest()
            active_port, target_port, maintenance_port = (
                reserve_port(), reserve_port(), reserve_port()
            )
            process = start_listener(directory / "listener.py", active_port, stranger_jar)
            try:
                actions = directory / "actions"
                result = subprocess.run(
                    ["bash", "-c", self.rollback_harness(
                        identity, rollback, active_port, target_port,
                        maintenance_port, expected_jar, expected_sha, actions,
                    )],
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("maintenance\n", actions.read_text(encoding="utf-8"))
                self.assertEqual(
                    "ROLLBACK_RESULT=BLOCKED_PREDECESSOR_MISMATCH\n"
                    "CUTOVER_RESULT=FAILED_MAINTENANCE_PROTECTED\n",
                    result.stdout,
                )
            finally:
                process.terminate()
                process.wait(timeout=5)

    def test_rollback_does_not_kill_a_stranger_target_listener_or_trust_pidfile(self):
        script = build_script()
        identity = listener_identity_functions(script)
        rollback = function_from(script, "rollback_cutover", "validate_cutover")
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            target_slot = directory / "green"
            target_slot.mkdir()
            expected_jar = target_slot / "nuono-next-backend-0.0.1-SNAPSHOT.jar"
            stranger_jar = directory / "stranger.jar"
            expected_jar.write_bytes(b"expected target")
            stranger_jar.write_bytes(b"stranger runtime")
            expected_sha = hashlib.sha256(expected_jar.read_bytes()).hexdigest()
            active_port, target_port, maintenance_port = (
                reserve_port(), reserve_port(), reserve_port()
            )
            process = start_listener(directory / "listener.py", target_port, stranger_jar)
            (target_slot / "nuono-next.pid").write_text(f"{process.pid}\n", encoding="utf-8")
            try:
                actions = directory / "actions"
                result = subprocess.run(
                    ["bash", "-c", f'''set -uo pipefail
ACTIVE_PORT={active_port}
TARGET_PORT={target_port}
MAINTENANCE_PORT={maintenance_port}
TARGET_SLOT_DIR={str(target_slot)!r}
JAR_NAME=nuono-next-backend-0.0.1-SNAPSHOT.jar
EXPECTED_JAR_SHA256={expected_sha!r}
ACTIVE_JAR_PATH=/unused/predecessor.jar
EXPECTED_ACTIVE_JAR_SHA256={'a' * 64}
EXTERNAL_HEALTH_URL=https://www.nuoon.com/ai/actuator/health
ACTIONS={str(actions)!r}
ROLLBACK_RUNNING=0
MAINTENANCE_ROUTED=1
NEW_START_ATTEMPTED=1
OLD_STOPPED=1
UPSTREAM_BACKUP=/unused
emit() {{ printf '%s=%s\n' "$1" "$2"; }}
switch_nginx_to_maintenance() {{ printf 'maintenance\n' >> "$ACTIONS"; }}
stop_pid() {{ printf 'kill:%s\n' "$1" >> "$ACTIONS"; kill "$1"; }}
{identity}
reverify_active_runtime_payloads() {{ :; }}
{rollback}
false
rollback_cutover
'''],
                    text=True,
                    capture_output=True,
                    check=False,
                )
                self.assertNotEqual(0, result.returncode)
                self.assertIsNone(process.poll(), "stranger target listener was killed")
                self.assertEqual("maintenance\n", actions.read_text(encoding="utf-8"))
                self.assertEqual(
                    "ROLLBACK_RESULT=BLOCKED_TARGET_MISMATCH\n"
                    "CUTOVER_RESULT=FAILED_MAINTENANCE_PROTECTED\n",
                    result.stdout,
                )
            finally:
                process.terminate()
                process.wait(timeout=5)

    def run_identity(self, functions, port, jar_path, expected_sha):
        return subprocess.run(
            ["bash", "-c", f'''set -uo pipefail
sha256_file() {{ sha256sum "$1" | awk '{{print $1}}'; }}
{functions}
exact_listener_pid_for_jar {port} {str(jar_path)!r} {expected_sha!r}
'''],
            text=True,
            capture_output=True,
            check=False,
        )

    def rollback_harness(
            self, identity, rollback, active_port, target_port,
            maintenance_port, expected_jar, expected_sha, actions,
    ):
        return f'''set -uo pipefail
ACTIVE_PORT={active_port}
TARGET_PORT={target_port}
MAINTENANCE_PORT={maintenance_port}
ACTIVE_JAR_PATH={str(expected_jar)!r}
EXPECTED_ACTIVE_JAR_SHA256={expected_sha!r}
EXTERNAL_HEALTH_URL=https://www.nuoon.com/ai/actuator/health
ACTIONS={str(actions)!r}
ROLLBACK_RUNNING=0
MAINTENANCE_ROUTED=1
NEW_START_ATTEMPTED=0
OLD_STOPPED=1
UPSTREAM_BACKUP=/unused
sha256_file() {{ sha256sum "$1" | awk '{{print $1}}'; }}
emit() {{ printf '%s=%s\n' "$1" "$2"; }}
switch_nginx_to_maintenance() {{ printf 'maintenance\n' >> "$ACTIONS"; }}
stop_target_runtime() {{ printf 'stop-target\n' >> "$ACTIONS"; }}
restart_old_runtime() {{ printf 'restart\n' >> "$ACTIONS"; }}
wait_for_health() {{ return 0; }}
health_status() {{ printf UP; }}
restore_nginx_to_active() {{ printf 'restore\n' >> "$ACTIONS"; }}
stop_maintenance_responder() {{ printf 'stop-maintenance\n' >> "$ACTIONS"; }}
current_upstream_port() {{ printf '%s' "$ACTIVE_PORT"; }}
curl() {{ printf '{{"status":"UP"}}'; }}
{identity}
reverify_active_runtime_payloads() {{ :; }}
{rollback}
false
rollback_cutover
'''


if __name__ == "__main__":
    unittest.main()
