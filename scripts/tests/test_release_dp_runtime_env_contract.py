import hashlib
import os
import shlex
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.tests.test_release_dp_runtime_cutover import build_script


class ReleaseDpRuntimeEnvContractTest(unittest.TestCase):
    def function(self):
        script = build_script()
        start = script.index('DP_RUNTIME_BASE_ENV_FILE=""')
        end = script.index("DP_RUNTIME_MYSQL_CNF", start)
        return script[start:end]

    def test_generated_slot_keys_are_replaced_without_touching_secrets(self):
        source_payload = (
            "NUONO_NEXT_DB_PASSWORD=database-secret\n"
            "NUONO_DATA_PULL_REPORT_LOCATOR_KEY_BASE64=locator-secret\n"
            "NUONO_NEXT_APP_DIR=/canonical\n"
            "NUONO_NEXT_PORT=18080\n"
        )
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source.env"
            target = root / "base.env"
            source.write_text(source_payload, encoding="utf-8")
            source.chmod(0o600)
            expected_sha = hashlib.sha256(source_payload.encode()).hexdigest()
            result = self.run_function(source, expected_sha, target)

            self.assertEqual(0, result.returncode, result.stderr)
            payload = target.read_text(encoding="utf-8")
            self.assertIn("NUONO_NEXT_DB_PASSWORD=database-secret", payload)
            self.assertIn("NUONO_DATA_PULL_REPORT_LOCATOR_KEY_BASE64=locator-secret", payload)
            self.assertNotIn("NUONO_NEXT_APP_DIR", payload)
            self.assertNotIn("NUONO_NEXT_PORT", payload)
            self.assertEqual(0o600, target.stat().st_mode & 0o777)

    def test_duplicate_canonical_key_is_rejected(self):
        payload = "BUSINESS_KEY=one\nBUSINESS_KEY=two\n"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source.env"
            source.write_text(payload, encoding="utf-8")
            source.chmod(0o600)
            result = self.run_function(
                source,
                hashlib.sha256(payload.encode()).hexdigest(),
                root / "base.env",
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("duplicate key", result.stderr)

    def run_function(self, source, expected_sha, target):
        return subprocess.run(
            ["bash", "-c", "\n".join((
                "set -Eeuo pipefail",
                self.function(),
                "prepare_dp_runtime_base_env "
                + " ".join(map(shlex.quote, (str(source), expected_sha, str(target)))),
            ))],
            text=True,
            capture_output=True,
            check=False,
            env={"PATH": os.environ.get("PATH", "/usr/bin:/bin")},
        )


if __name__ == "__main__":
    unittest.main()
