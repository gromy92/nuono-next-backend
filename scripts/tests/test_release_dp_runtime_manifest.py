import datetime as dt
import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from zoneinfo import ZoneInfo

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
        "boundaryPolicy": "SAFE_PREDECESSOR_OR_FALLBACK_BOUNDARY",
        "operationCount": 11,
        "cohortSha256": cohort,
        "operations": operations,
    }


def manifest_with_scope(boundary_days_before):
    result = manifest()
    observed = dt.datetime.fromisoformat(
        result["sourceObservedAtUtc"].replace("Z", "+00:00")
    )
    shanghai = ZoneInfo("Asia/Shanghai")
    boundary_date = observed.astimezone(shanghai).date() - dt.timedelta(
        days=boundary_days_before
    )
    boundary = dt.datetime.combine(
        boundary_date, dt.time.min, tzinfo=shanghai
    ).astimezone(dt.timezone.utc)
    scope = {
        "scopeKey": "NOON-test-scope",
        "scopeNamespace": "NOON",
        "ownerUserId": 307,
        "logicalStoreId": 108065,
        "accountKey": "account-307",
        "egressKey": "egress-cn-1",
        "projectCode": "PRJ108065",
        "storeCode": "STR108065-NSA",
        "siteCode": "SA",
        "sourceBindingSha256": "1" * 64,
        "reconcileAfterUtc": boundary.isoformat(timespec="milliseconds").replace(
            "+00:00", "Z"
        ),
        "boundaryKind": result["boundaryPolicy"],
        "boundaryEvidenceSha256": "2" * 64,
        "anchorEvidenceSha256": "3" * 64,
        "binding": None,
    }
    result["operations"][0]["expectedScopeCount"] = 1
    result["operations"][0]["scopes"] = [scope]
    result["cohortSha256"] = hashlib.sha256(json.dumps(
        result["operations"], ensure_ascii=False,
        separators=(",", ":"), sort_keys=True,
    ).encode()).hexdigest()
    return result


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

    def test_shell_verifier_accepts_retained_predecessor_boundary_but_not_future(self):
        with tempfile.TemporaryDirectory() as directory:
            for days_before, expected in ((10, 0), (0, 1)):
                path = Path(directory) / f"manifest-{days_before}.json"
                path.write_text(
                    json.dumps(manifest_with_scope(days_before)), encoding="utf-8"
                )
                os.chmod(path, 0o600)
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                result = run_fragment(f'''
secure_file_operation() {{ sha256sum "$2" | awk '{{print $1}}'; }}
verify_dp_runtime_manifest_json "{path}" "{digest}"
''')
                if expected == 0:
                    self.assertEqual(0, result.returncode, result.stderr)
                else:
                    self.assertNotEqual(0, result.returncode)

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
