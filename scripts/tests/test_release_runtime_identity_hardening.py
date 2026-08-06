import hashlib
import shutil
import socket
import subprocess
import tempfile
import time
import unittest
import zipfile
from pathlib import Path

from scripts.tests.test_release_maintenance_probe import (
    build_script,
    function_from,
    run_bash,
)
from scripts.tests.test_release_predecessor_rollback import (
    listener_identity_functions,
    reserve_port,
    start_listener,
)


def build_java_listener_jar(directory: Path) -> Path:
    source = directory / "DelayedListener.java"
    classes = directory / "classes"
    classes.mkdir()
    source.write_text(
        """import java.net.ServerSocket;
public final class DelayedListener {
  public static void main(String[] args) throws Exception {
    Thread.sleep(Long.parseLong(args[1]));
    try (ServerSocket server = new ServerSocket(Integer.parseInt(args[0]), 5,
        java.net.InetAddress.getLoopbackAddress())) {
      while (true) { server.accept().close(); }
    }
  }
}
""",
        encoding="utf-8",
    )
    result = subprocess.run(
        ["javac", "-d", str(classes), str(source)],
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode:
        raise AssertionError(result.stderr)
    jar = directory / "nuono-next-backend-0.0.1-SNAPSHOT.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        archive.writestr(
            "META-INF/MANIFEST.MF",
            "Manifest-Version: 1.0\nMain-Class: DelayedListener\n\n",
        )
        archive.write(classes / "DelayedListener.class", "DelayedListener.class")
    jar.chmod(0o600)
    return jar


@unittest.skipUnless(
    shutil.which("java") and shutil.which("javac") and Path("/proc/self/cmdline").exists(),
    "Linux /proc and JDK required",
)
class ReleaseRuntimeIdentityProcessTest(unittest.TestCase):
    def test_exact_listener_accepts_only_real_java_single_jar_and_sha(self):
        functions = listener_identity_functions(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            jar = build_java_listener_jar(directory)
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            port = reserve_port()
            process = subprocess.Popen(
                ["java", "-jar", str(jar), str(port), "0"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            try:
                for _ in range(80):
                    try:
                        with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                            break
                    except OSError:
                        time.sleep(0.05)
                result = run_bash(f'''set -uo pipefail
{functions}
exact_listener_pid_for_jar {port} {str(jar)!r} {digest!r}
''')
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(str(process.pid), result.stdout)
            finally:
                process.terminate()
                process.wait(timeout=5)

    def test_global_census_resolves_a_relative_jar_against_process_cwd(self):
        functions = listener_identity_functions(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            app_dir = Path(temporary)
            jar = build_java_listener_jar(app_dir)
            process = subprocess.Popen(
                ["java", "-jar", jar.name, str(reserve_port()), "30000"],
                cwd=app_dir,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            try:
                result = run_bash(f'''set -uo pipefail
APP_DIR={str(app_dir)!r}
{functions}
backend_jvm_pids
''')
                self.assertEqual(str(process.pid), result.stdout.strip(), result.stderr)
            finally:
                process.terminate()
                process.wait(timeout=5)

    def test_rollback_finds_and_stops_exact_target_before_it_listens(self):
        functions = listener_identity_functions(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            app_dir = Path(temporary)
            target = app_dir / "blue-green" / "green"
            target.mkdir(parents=True)
            jar = build_java_listener_jar(target)
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            port = reserve_port()
            process = subprocess.Popen(
                ["java", "-jar", str(jar), str(port), "30000"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            try:
                for _ in range(50):
                    if Path(f"/proc/{process.pid}/cmdline").exists():
                        break
                    time.sleep(0.02)
                result = run_bash(f'''set -uo pipefail
APP_DIR={str(app_dir)!r}
TARGET_SLOT_DIR={str(target)!r}
TARGET_PORT={port}
JAR_NAME=nuono-next-backend-0.0.1-SNAPSHOT.jar
EXPECTED_JAR_SHA256={digest!r}
NEW_PID=""
{functions}
stop_pid() {{
  require_safe_pid "$1" || return 1
  kill -TERM -- "$1"
  for _ in {{1..50}}; do ! kill -0 -- "$1" 2>/dev/null && return 0; sleep 0.05; done
  return 1
}}
stop_target_runtime
''')
                self.assertEqual(0, result.returncode, result.stderr)
                process.wait(timeout=5)
            finally:
                if process.poll() is None:
                    process.terminate()
                    process.wait(timeout=5)

    def test_python_listener_with_jar_arguments_is_rejected(self):
        functions = listener_identity_functions(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            jar = directory / "candidate.jar"
            jar.write_bytes(b"candidate")
            jar.chmod(0o600)
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            port = reserve_port()
            process = start_listener(directory / "listener.py", port, jar)
            try:
                result = run_bash(f'''set -uo pipefail
{functions}
exact_listener_pid_for_jar {port} {str(jar)!r} {digest!r}
''')
                self.assertNotEqual(0, result.returncode)
                self.assertIsNone(process.poll())
            finally:
                process.terminate()
                process.wait(timeout=5)


class ReleaseRuntimeIdentityContractTest(unittest.TestCase):
    def test_listener_discovery_uses_a_root_owned_non_writable_lsof(self):
        script = build_script()

        self.assertIn("bind_trusted_lsof", script)
        self.assertIn('metadata.st_uid == 0', script)
        self.assertIn('not stat.S_IMODE(metadata.st_mode) & 0o022', script)
        self.assertIn('"$LSOF_BIN" -nP', script)
        self.assertIn('os.path.realpath(entry / "cwd")', script)
        self.assertIn('b"/libjvm.so"', script)

    def test_target_identity_and_dp_runtime_are_rechecked_across_cutover(self):
        script = build_script()
        execution = script.split("\nvalidate_cutover\n", 1)[1]
        target_health = execution.index('wait_for_health "$TARGET_PORT"')
        target_switch = execution.index('switch_nginx_to_port "$TARGET_PORT"')
        external = execution.index('capture_status external_health post_switch_external_health')
        readiness = [
            index for index in range(len(execution))
            if execution.startswith("assert_target_release_ready", index)
        ]
        self.assertGreaterEqual(len(readiness), 4)
        self.assertTrue(any(target_health < index < target_switch for index in readiness))
        self.assertTrue(any(target_switch < index < external for index in readiness))
        self.assertTrue(any(index > external for index in readiness))
        identity = function_from(script, "assert_target_runtime_identity", "stop_target_runtime")
        self.assertIn('= "$NEW_PID"', identity)
        self.assertIn('"$EXPECTED_JAR_SHA256"', identity)
        self.assertIn('assert_only_backend_jvm "$NEW_PID"', identity)

    def test_global_jvm_census_closes_every_start_stop_boundary(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        probe = execution.index("run_dp10_openapi_probe")
        old_stop = execution.index('stop_pid "$ACTIVE_PID"')
        zero = execution.index("assert_no_backend_jvms", old_stop)
        target_start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"')
        capture = execution.index('NEW_PID="$(wait_for_unique_target_jvm)"')
        self.assertIn('assert_only_backend_jvm "$ACTIVE_PID"', execution[:probe])
        self.assertIn('assert_only_backend_jvm "$ACTIVE_PID"', execution[probe:old_stop])
        self.assertLess(old_stop, zero)
        self.assertLess(zero, target_start)
        self.assertLess(target_start, capture)

    def test_maintenance_stop_never_reads_pidfile_or_accepts_unsafe_pid(self):
        script = build_script()
        stop = function_from(script, "stop_maintenance_responder", "switch_nginx_to_maintenance")
        self.assertNotIn("server.pid", script)
        self.assertIn("exact_maintenance_listener_pid", stop)
        self.assertIn("require_safe_pid", stop)
        self.assertNotIn("kill -1", script)
        self.assertIn('kill -TERM -- "$1"', script)

    def test_predecessor_payload_and_real_directory_are_frozen_for_rollback(self):
        script = build_script()
        freeze = function_from(script, "freeze_active_runtime_payloads", "reverify_active_runtime_payloads")
        reverify = function_from(script, "reverify_active_runtime_payloads", "wait_for_unique_target_jvm")
        rollback = function_from(script, "rollback_cutover", "dp_runtime_health_status")
        self.assertIn("require_real_runtime_directory", freeze)
        self.assertIn("ACTIVE_ENV_SHA256", freeze)
        self.assertIn("ACTIVE_START_SCRIPT_SHA256", freeze)
        self.assertIn("ACTIVE_ENV_SHA256", reverify)
        self.assertIn("ACTIVE_START_SCRIPT_SHA256", reverify)
        self.assertIn("BLOCKED_PREDECESSOR_PAYLOAD_DRIFT", rollback)

    def test_runtime_starts_in_clean_environment_with_managed_marker(self):
        script = build_script()
        start = function_from(script, "start_runtime", "listener_pids_for_port")
        self.assertIn("/usr/bin/env -i", start)
        self.assertIn("NUONO_MANAGED_DP_RELEASE=1", start)
        for forbidden in (
            "SPRING_APPLICATION_JSON", "SPRING_DATASOURCE_", "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS",
        ):
            self.assertIn(forbidden, script)
        self.assertIn("runtime_format+='NUONO_MANAGED_DP_RELEASE=1\\n'", script)
        self.assertIn('grep -Fxc "NUONO_MANAGED_DP_RELEASE=1"', script)
        self.assertIn("runtime_format+='NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME\\n'", script)
        self.assertIn('grep -Fxc "NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME"', script)

    def test_external_url_is_allowlisted_and_passed_after_option_terminator(self):
        from scripts import release_single_scheduler_cutover as module

        self.assertEqual(
            "https://www.nuoon.com/ai/actuator/health",
            module._validated_external_health_url(
                "https://www.nuoon.com/ai/actuator/health"
            ),
        )
        for value in (
            "http://www.nuoon.com/ai/actuator/health",
            "https://evil.example/ai/actuator/health",
            "https://www.nuoon.com:8443/ai/actuator/health",
            "https://www.nuoon.com/ai/actuator/health?next=evil",
        ):
            with self.subTest(value=value), self.assertRaises(ValueError):
                module._validated_external_health_url(value)
        self.assertIn('-- "$EXTERNAL_HEALTH_URL"', build_script())

    def test_dp_runtime_health_requires_top_level_status_up(self):
        function = function_from(
            build_script(), "dp_runtime_health_status", "assert_target_release_ready"
        )
        for body, expected in (
            ('{"status":"UP"}', "UP"),
            ('{"status":"DOWN","details":{"status":"UP"}}', "UNAVAILABLE"),
            ('{"details":{"status":"UP"}}', "UNAVAILABLE"),
        ):
            with self.subTest(body=body):
                result = run_bash(f'''set -uo pipefail
TARGET_PORT=18088
BODY={body!r}
curl() {{ printf '%s' "$BODY"; }}
{function}
dp_runtime_health_status
''')
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(expected, result.stdout)


if __name__ == "__main__":
    unittest.main()
