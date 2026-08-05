import re
import tempfile
import unittest
from pathlib import Path

from scripts.tests.test_release_maintenance_probe import (
    build_script,
    function_from,
    run_bash,
)


def post_switch_external_block(script):
    start = script.index('external_health=""; capture_status')
    return script[start : script.index("stop_maintenance_responder", start)]


def real_rollback_harness(script, scenario, directory):
    rollback = function_from(script, "rollback_cutover", "validate_cutover")
    current = '''current_upstream_port() {
  grep -Eo '127\\.0\\.0\\.1:[0-9]+' "$NGINX_UPSTREAM_FILE" | head -n 1 | cut -d: -f2
}'''
    switch = function_from(script, "switch_nginx_to_port", "maintenance_response_status")
    capture = function_from(script, "capture_status", "post_switch_external_health")
    external = function_from(script, "post_switch_external_health", "start_maintenance_responder")
    upstream = directory / "upstream.inc"
    actions = directory / "actions"
    external_failed = directory / "external-failed"
    upstream_backup = directory / "upstream.before"
    active_listener = directory / "active-listener"
    target_listener = directory / "target-listener"
    maintenance_listener = directory / "maintenance-listener"
    target_listener.touch()
    maintenance_listener.touch()
    upstream_backup.write_text("server 127.0.0.1:18087;\n")
    if scenario == "external":
        upstream.write_text("server 127.0.0.1:18088;\n")
        failure = post_switch_external_block(script)
        fail_external = 1
    else:
        upstream.write_text("managed upstream without a loopback target\n")
        failure = 'switch_nginx_to_port "$TARGET_PORT"\n'
        fail_external = 0
    return run_bash(f'''set -Eeuo pipefail
ACTIVE_PORT=18087
TARGET_PORT=18088
MAINTENANCE_PORT=18089
ACTIVE_JAR_PATH=/app/blue-green/blue/nuono-next-backend-0.0.1-SNAPSHOT.jar
EXPECTED_ACTIVE_JAR_SHA256={'b' * 64}
EXTERNAL_HEALTH_URL=https://www.nuoon.com/ai/actuator/health
NGINX_UPSTREAM_FILE={str(upstream)!r}
UPSTREAM_BACKUP={str(upstream_backup)!r}
ACTIONS={str(actions)!r}
EXTERNAL_FAILED={str(external_failed)!r}
ACTIVE_LISTENER={str(active_listener)!r}
TARGET_LISTENER={str(target_listener)!r}
MAINTENANCE_LISTENER={str(maintenance_listener)!r}
FAIL_EXTERNAL={fail_external}
ROLLBACK_RUNNING=0
MAINTENANCE_ROUTED=1
NEW_START_ATTEMPTED=1
OLD_STOPPED=1
ACTIVE_HEALTH=UNAVAILABLE
record() {{ printf '%s\n' "$1" >> "$ACTIONS"; }}
write_port() {{ printf 'server 127.0.0.1:%s;\n' "$1" > "$NGINX_UPSTREAM_FILE"; }}
switch_nginx_to_maintenance() {{
  record "ensure-maintenance:$BASH_SUBSHELL"
  : > "$MAINTENANCE_LISTENER"
  write_port "$MAINTENANCE_PORT"
}}
stop_target_runtime() {{ record stop-target; rm -f "$TARGET_LISTENER"; }}
pid_for_port() {{
  [ "$1" != "$ACTIVE_PORT" ] || [ ! -f "$ACTIVE_LISTENER" ] || printf 101
  [ "$1" != "$TARGET_PORT" ] || [ ! -f "$TARGET_LISTENER" ] || printf 102
  [ "$1" != "$MAINTENANCE_PORT" ] || [ ! -f "$MAINTENANCE_LISTENER" ] || printf 103
}}
exact_listener_pid_for_jar() {{
  [ "$1" != "$ACTIVE_PORT" ] || [ ! -f "$ACTIVE_LISTENER" ] || printf 101
}}
reverify_active_runtime_payloads() {{ :; }}
assert_only_backend_jvm() {{ :; }}
restart_old_runtime() {{ record restart-old; : > "$ACTIVE_LISTENER"; ACTIVE_HEALTH=UP; }}
wait_for_health() {{ record wait-old; }}
health_status() {{
  if [ "$1" = "$ACTIVE_PORT" ]; then printf '%s' "$ACTIVE_HEALTH"; else printf UNAVAILABLE; fi
}}
restore_nginx_to_active() {{ record restore-upstream; write_port "$ACTIVE_PORT"; }}
stop_maintenance_responder() {{ record stop-maintenance; rm -f "$MAINTENANCE_LISTENER"; }}
curl() {{
  if [ "$FAIL_EXTERNAL" = 1 ] && [ ! -f "$EXTERNAL_FAILED" ]; then
    : > "$EXTERNAL_FAILED"
    printf '{{"status":"UP"}}'
    return 22
  fi
  record rollback-external-UP
  printf '{{"status":"UP"}}'
}}
emit() {{ printf '%s=%s\n' "$1" "$2"; }}
write_upstream_port() {{ :; }}
nginx() {{ :; }}
sleep() {{ :; }}
{capture}
{external}
{current}
{switch}
{rollback}
trap rollback_cutover ERR
{failure}
'''), {
        "actions": actions,
        "active_listener": active_listener,
        "target_listener": target_listener,
        "maintenance_listener": maintenance_listener,
        "upstream": upstream,
    }


class ReleaseTrapSafetyTest(unittest.TestCase):
    def test_capture_preserves_absent_err_trap_and_failure_status(self):
        script = build_script()
        capture = function_from(script, "capture_status", "post_switch_external_health")
        result = run_bash(f'''set -uo pipefail
{capture}
looks_successful() {{ printf UP; return 22; }}
value=""
if capture_status value looks_successful; then rc=0; else rc="$?"; fi
printf 'rc=%s\nvalue=%s\ntrap=%s\n' "$rc" "$value" "$(trap -p ERR)"
''')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "rc=22\nvalue=UP\ntrap=\n")

    def test_post_switch_failures_execute_one_complete_parent_rollback(self):
        script = build_script()
        for scenario in ("external", "upstream"):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as temp:
                result, state = real_rollback_harness(script, scenario, Path(temp))
                self.assertEqual(result.returncode, 0, result.stderr)
                action_sequence = (
                    state["actions"].read_text().splitlines()
                    if state["actions"].exists()
                    else []
                )
                self.assertEqual(
                    action_sequence,
                    [
                        "ensure-maintenance:0",
                        "stop-target",
                        "restart-old",
                        "wait-old",
                        "restore-upstream",
                        "stop-maintenance",
                        "rollback-external-UP",
                    ],
                )
                self.assertEqual(
                    result.stdout,
                    "ROLLBACK_RESULT=PASS\nCUTOVER_RESULT=FAILED_ROLLED_BACK\n",
                )
                self.assertTrue(state["active_listener"].exists())
                self.assertFalse(state["target_listener"].exists())
                self.assertFalse(state["maintenance_listener"].exists())
                self.assertEqual(
                    state["upstream"].read_text(),
                    "server 127.0.0.1:18087;\n",
                )

    def test_wrong_body_503_rolls_back_only_in_parent(self):
        script = build_script()
        retry = function_from(script, "wait_for_external_maintenance", "start_maintenance_responder")
        capture = function_from(script, "capture_status", "post_switch_external_health")
        switch = function_from(script, "switch_nginx_to_maintenance", "stop_pid")
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            calls = Path(temp) / "rollback-calls"
            result = run_bash(f'''set -Eeuo pipefail
MAINTENANCE_PORT=18089
MAINTENANCE_ROUTED=0
MAINTENANCE_DIR={str(directory)!r}
ROLLBACK_CALLS={str(calls)!r}
switch_nginx_to_port() {{ :; }}
maintenance_response_status() {{ printf 503; }}
external_maintenance_status() {{
  printf 'not the governed maintenance body' > "$MAINTENANCE_DIR/external-response.json"
  printf 503
}}
sleep() {{ :; }}
emit() {{ :; }}
rollback_cutover() {{ printf '%s\n' "$BASH_SUBSHELL" >> "$ROLLBACK_CALLS"; exit 97; }}
{retry}
{capture}
{switch}
trap rollback_cutover ERR
switch_nginx_to_maintenance
''')
            rollback_calls = calls.read_text().splitlines() if calls.exists() else []
        self.assertEqual(result.returncode, 97)
        self.assertEqual(rollback_calls, ["0"])

    def test_health_failures_roll_back_only_in_parent(self):
        script = build_script()
        health = function_from(script, "health_status", "wait_for_health")
        capture = function_from(script, "capture_status", "post_switch_external_health")
        for scenario in ("transport", "parser"):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as temp:
                calls = Path(temp) / "rollback-calls"
                curl_body = (
                    '''printf '{"status":"UP"}'; return 22'''
                    if scenario == "transport"
                    else '''for _ in {1..10000}; do printf '{"status":"UP"}\\n'; done'''
                )
                result = run_bash(f'''set -Eeuo pipefail
ROLLBACK_CALLS={str(calls)!r}
curl() {{ {curl_body}; }}
rollback_cutover() {{ printf '%s\n' "$BASH_SUBSHELL" >> "$ROLLBACK_CALLS"; exit 97; }}
{capture}
{health}
trap rollback_cutover ERR
[ "$(health_status 18088)" = UP ]
''')
                rollback_calls = calls.read_text().splitlines() if calls.exists() else []
            self.assertEqual(result.returncode, 97, result.stderr)
            self.assertEqual(rollback_calls, ["0"])

    def test_trap_window_status_helpers_are_total(self):
        script = build_script()
        trap_window = script[
            script.index("trap rollback_cutover ERR") : script.index("trap - ERR", script.index("trap rollback_cutover ERR"))
        ]
        health = function_from(script, "health_status", "wait_for_health")
        pid = function_from(script, "pid_for_port", "process_jar_path")
        current = function_from(script, "current_upstream_port", "write_upstream_port")
        port_switch = function_from(script, "switch_nginx_to_port", "maintenance_response_status")
        switch = function_from(script, "switch_nginx_to_maintenance", "stop_pid")
        self.assertNotIn("$(seq ", script)
        self.assertNotIn("|| true", current)
        self.assertIn("capture_status current_port current_upstream_port", port_switch)
        self.assertIn("capture_status external_status wait_for_external_maintenance", switch)
        self.assertIn("capture_status external_health post_switch_external_health", trap_window)
        self.assertIn("capture_status body loopback_health_body", health)
        self.assertIn("capture_status parsed parse_health_body", health)
        self.assertIn("|| true", pid)
        self.assertEqual(
            set(re.findall(r"\$\(([a-z_][a-z0-9_]*)", trap_window)),
            {
                "exact_listener_pid_for_jar",
                "pid_for_port",
                "topology_cas_sha256",
                "wait_for_unique_target_jvm",
            },
        )


if __name__ == "__main__":
    unittest.main()
