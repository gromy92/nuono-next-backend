import base64
import shlex
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.tests.test_release_dp_runtime_cutover import build_script, cutover_fragment


class ReleaseDpRuntimeSecretEnvironmentTest(unittest.TestCase):
    def test_validation_precedes_any_cutover_mutation(self):
        script = build_script()
        validation_start = script.index("validate_cutover()")
        validation_call = script.index(
            "  require_dp_runtime_secret_environment", validation_start
        )
        validation_run = script.index("\nvalidate_cutover\n")
        mutation = script.index("prepare_dp_runtime_cutover", validation_run)

        self.assertLess(validation_call, validation_run)
        self.assertLess(validation_run, mutation)

    def test_exactly_one_base64_aes_256_key_is_required(self):
        fragment = cutover_fragment()
        function = fragment[
            fragment.index("require_dp_runtime_secret_environment()"):
            fragment.index("dp_runtime_mysql()")
        ]
        valid_key = base64.b64encode(bytes(range(32))).decode("ascii")
        short_key = base64.b64encode(bytes(range(16))).decode("ascii")
        cases = (
            ("", 1, "unavailable or ambiguous"),
            ("NUONO_DATA_PULL_REPORT_LOCATOR_KEY_BASE64=invalid!\n", 1, "not valid base64"),
            (
                f"NUONO_DATA_PULL_REPORT_LOCATOR_KEY_BASE64={short_key}\n",
                1,
                "must decode to 32 bytes",
            ),
            (f"NUONO_DATA_PULL_REPORT_LOCATOR_KEY_BASE64={valid_key}\n", 0, ""),
            (
                f"NUONO_DATA_PULL_REPORT_LOCATOR_KEY_BASE64={valid_key}\n"
                f"NUONO_DATA_PULL_REPORT_LOCATOR_KEY_BASE64={valid_key}\n",
                1,
                "unavailable or ambiguous",
            ),
        )
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            for payload, expected_code, expected_error in cases:
                with self.subTest(payload_lines=payload.count("\n")):
                    env_file.write_text(payload, encoding="utf-8")
                    result = subprocess.run(
                        ["bash", "-c", "\n".join((
                            "set -Eeuo pipefail",
                            "APP_DIR=" + shlex.quote(temporary),
                            function,
                            "require_dp_runtime_secret_environment",
                        ))],
                        text=True,
                        capture_output=True,
                        check=False,
                    )
                    self.assertEqual(expected_code, result.returncode)
                    self.assertIn(expected_error, result.stderr)


if __name__ == "__main__":
    unittest.main()
