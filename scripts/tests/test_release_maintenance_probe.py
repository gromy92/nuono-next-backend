import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "release_cutover_maintenance.py"


def build_script():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
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
        release_name="maintenance-retry-test",
        external_health_url="https://www.nuoon.com/ai/actuator/health",
        app_dir="/app",
    )


def function_from(script, name, next_name):
    return script[script.index(f"{name}()") : script.index(f"{next_name}()")]


def run_bash(body):
    return subprocess.run(
        ["bash", "-c", body],
        check=False,
        text=True,
        capture_output=True,
    )


def sequence_harness(retry, directory):
    return f'''set -Eeuo pipefail
MAINTENANCE_DIR={str(directory)!r}
next_status() {{
  local sequence_file="$1" index_file="$2" index status
  index="$(cat "$index_file")"
  status="$(sed -n "${{index}}p" "$sequence_file")"
  [ -n "$status" ] || status="$(tail -n 1 "$sequence_file")"
  printf '%s\n' "$((index + 1))" > "$index_file"
  printf '%s' "$status"
}}
maintenance_response_status() {{
  next_status "$MAINTENANCE_DIR/local.sequence" "$MAINTENANCE_DIR/local.index"
}}
external_maintenance_status() {{
  local status
  status="$(next_status "$MAINTENANCE_DIR/external.sequence" "$MAINTENANCE_DIR/external.index")"
  if [ "$status" = 503 ]; then
    printf '%s' '{{"status":503,"message":"服务正在更新，请稍后重试"}}' \
      > "$MAINTENANCE_DIR/external-response.json"
  else
    printf '%s' 'temporary proxy failure' > "$MAINTENANCE_DIR/external-response.json"
  fi
  printf '%s' "$status"
}}
sleep() {{ :; }}
{retry}
set +e
result="$(wait_for_external_maintenance)"
rc="$?"
set -e
printf 'rc=%s\nresult=%s\nlocal_calls=%s\nexternal_calls=%s\n' \
  "$rc" "$result" \
  "$(( $(cat "$MAINTENANCE_DIR/local.index") - 1 ))" \
  "$(( $(cat "$MAINTENANCE_DIR/external.index") - 1 ))"
'''


def run_probe(local_statuses, external_statuses):
    with tempfile.TemporaryDirectory() as temp:
        directory = Path(temp)
        (directory / "local.sequence").write_text("\n".join(local_statuses) + "\n")
        (directory / "external.sequence").write_text("\n".join(external_statuses) + "\n")
        (directory / "local.index").write_text("1\n")
        (directory / "external.index").write_text("1\n")
        script = build_script()
        retry = function_from(script, "wait_for_external_maintenance", "start_maintenance_responder")
        return run_bash(sequence_harness(retry, directory))


class ReleaseMaintenanceProbeTest(unittest.TestCase):
    def test_external_probe_template_remains_fail_closed(self):
        script = build_script()
        retry = function_from(script, "wait_for_external_maintenance", "start_maintenance_responder")
        switch = function_from(script, "switch_nginx_to_maintenance", "stop_pid")

        self.assertIn("for external_attempt in $(seq 1 15)", retry)
        self.assertLess(
            retry.index('"$(maintenance_response_status)"'),
            retry.index('external_status="$(external_maintenance_status)"'),
        )
        self.assertIn('grep -F -q "服务正在更新，请稍后重试"', retry)
        self.assertIn('if ! external_status="$(wait_for_external_maintenance)"; then', switch)
        self.assertIn('switch_nginx_to_port "$MAINTENANCE_PORT" || return 1', switch)

    def test_transient_external_502_then_503_succeeds(self):
        result = run_probe(["503"], ["502", "503"])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout,
            "rc=0\nresult=503\nlocal_calls=2\nexternal_calls=2\n",
        )

    def test_external_retry_exhaustion_fails_after_15_attempts(self):
        result = run_probe(["503"], ["502"])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout,
            "rc=1\nresult=502\nlocal_calls=15\nexternal_calls=15\n",
        )

    def test_local_responder_loss_stops_external_probes(self):
        result = run_probe(["503", "000"], ["502", "503"])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout,
            "rc=1\nresult=502\nlocal_calls=2\nexternal_calls=1\n",
        )

    def test_switch_failure_propagates_under_conditional_caller(self):
        script = build_script()
        switch = function_from(script, "switch_nginx_to_maintenance", "stop_pid")
        result = run_bash(f'''set -Eeuo pipefail
MAINTENANCE_PORT=18089
MAINTENANCE_ROUTED=0
switch_nginx_to_port() {{ return 1; }}
maintenance_response_status() {{ touch "$1"; printf 503; }}
wait_for_external_maintenance() {{ touch "$1"; printf 503; }}
emit() {{ :; }}
{switch}
if switch_nginx_to_maintenance; then rc=0; else rc="$?"; fi
printf 'rc=%s\nrouted=%s\n' "$rc" "$MAINTENANCE_ROUTED"
''')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "rc=1\nrouted=0\n")


if __name__ == "__main__":
    unittest.main()
