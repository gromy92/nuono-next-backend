import unittest

from scripts.tests.test_release_maintenance_probe import (
    build_script,
    function_from,
    run_bash,
)


class ReleaseRuntimeReadinessTest(unittest.TestCase):
    def test_runtime_health_wait_precedes_the_final_cutover_assertion(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        root_health = execution.index('wait_for_health "$TARGET_PORT"')
        runtime_health = execution.index("wait_for_dp_runtime_health")
        readiness = execution.index("assert_target_release_ready", runtime_health)

        self.assertLess(root_health, runtime_health)
        self.assertLess(runtime_health, readiness)

    def test_runtime_health_wait_is_bounded_and_retries_until_up(self):
        function = function_from(
            build_script(), "wait_for_dp_runtime_health", "assert_target_release_ready"
        )
        self.assertIn("for attempt in {1..30}", function)
        result = run_bash(f'''set -uo pipefail
dp_runtime_health_status() {{
  [ "$attempt" -ge 3 ] && printf UP || printf UNAVAILABLE
}}
sleep() {{ :; }}
{function}
wait_for_dp_runtime_health
''')

        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
