import datetime as dt
import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.tests.test_release_dp_runtime_cutover import cutover_fragment


COMMIT = "c" * 40
JAR_SHA = "a" * 64


def manifest():
    observed = dt.datetime.now(dt.timezone.utc).replace(microsecond=123000)
    operations = [
        {
            "operationCode": operation,
            "expectedScopeCount": 0,
            "anchorManifestSha256": "b" * 64,
            "scopes": [],
        }
        for operation in (
            "DP01", "DP02", "DP03", "DP04", "DP05", "DP06",
            "DP07A", "DP07B", "DP08A", "DP08B", "DP10",
        )
    ]
    cohort = hashlib.sha256(json.dumps(
        operations, ensure_ascii=False, separators=(",", ":"), sort_keys=True,
    ).encode()).hexdigest()
    stamp = lambda value: value.isoformat(timespec="milliseconds").replace("+00:00", "Z")
    return {
        "schema": "nuono.dp-runtime-cutover-manifest/v1",
        "type": "DP_RUNTIME_CUTOVER_MANIFEST",
        "manifestCommit": COMMIT,
        "candidateJarSha256": JAR_SHA,
        "cutoverKey": "dp-runtime-" + COMMIT,
        "sourceObservedAtUtc": stamp(observed),
        "generatedAtUtc": stamp(observed),
        "expiresAtUtc": stamp(observed + dt.timedelta(minutes=30)),
        "boundaryPolicy": "SAFE_FALLBACK_PREVIOUS_BUSINESS_DAY",
        "operationCount": 11,
        "cohortSha256": cohort,
        "operations": operations,
    }


def run_fragment(body):
    script = "\n".join((
        "set -Eeuo pipefail", "APP_DIR=/app", "BACKUP_DIR=/backup",
        "STAGED_JAR=/staged/backend.jar", f"EXPECTED_COMMIT={COMMIT}",
        f"EXPECTED_JAR_SHA256={JAR_SHA}", cutover_fragment(), body,
    ))
    return subprocess.run(["bash", "-c", script], text=True, capture_output=True, check=False)


class ReleaseDpRuntimeManifestTest(unittest.TestCase):
    def test_shell_verifier_recomputes_the_canonical_cohort_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps(manifest(), ensure_ascii=False), encoding="utf-8")
            os.chmod(path, 0o600)
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            result = run_fragment(f'''
secure_file_operation() {{ sha256sum "$2" | awk '{{print $1}}'; }}
verify_dp_runtime_manifest_json "{path}" "{digest}"
''')
            self.assertEqual(0, result.returncode, result.stderr)

    def test_bootstrap_writer_emits_one_fk_ordered_transaction_for_all_operations(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "manifest.json"
            target = Path(directory) / "bootstrap.sql"
            source.write_text(json.dumps(manifest()), encoding="utf-8")
            result = run_fragment(f'''
DP_RUNTIME_RECHECK_MANIFEST="{source}"
DP_RUNTIME_BOOTSTRAP_SQL="{target}"
[ "$(write_dp_runtime_bootstrap_sql)" = $'0\\t0\\t0' ]
''')
            self.assertEqual(0, result.returncode, result.stderr)
            sql = target.read_text(encoding="utf-8")
            self.assertTrue(sql.startswith("SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;\nSTART TRANSACTION;"))
            self.assertEqual(11, sql.count("INSERT INTO dp_pull_schedule_cutover"))
            self.assertTrue(sql.endswith("COMMIT;\n"))


if __name__ == "__main__":
    unittest.main()
