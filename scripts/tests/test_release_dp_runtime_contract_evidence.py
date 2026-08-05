from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPT_DIR = pathlib.Path(__file__).parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

from scripts.release_dp_runtime_contract_evidence import (
    build_dp_runtime_contract_evidence_shell,
)
from scripts.release_single_scheduler_cutover import build_single_scheduler_cutover_script


COMMIT = "c" * 40
JAR_SHA = "d" * 64
SOURCES = {
    "DP04_STABLE_SNAPSHOT": "PROVIDER_SNAPSHOT_AUTHORITY",
    "DP06_COMPLETE_CAMPAIGN_ENUMERATION": "PROVIDER_COMPLETE_CAMPAIGN_ENUMERATION",
    "DP07A_STABLE_SNAPSHOT": "PROVIDER_SNAPSHOT_AUTHORITY",
    "DP10_MODIFIED_TIME_VISIBILITY_CONTRACT": "PROVIDER_MODIFIED_TIME_VISIBILITY",
}


def build_cutover() -> str:
    return build_single_scheduler_cutover_script(
        staged_jar="/srv/app/staged.jar",
        expected_jar_sha256=JAR_SHA,
        expected_commit=COMMIT,
        expected_active_jar_sha256="e" * 64,
        expected_active_pid=123,
        expected_nginx_upstream_sha256="f" * 64,
        expected_topology_cas_sha256="a" * 64,
        active_slot="blue",
        target_slot="green",
        active_port=18087,
        target_port=18088,
        maintenance_port=18089,
        nginx_upstream_file="/etc/nginx/upstream.conf",
        release_name="dp-runtime",
        external_health_url="https://www.nuoon.com/ai/actuator/health",
        app_dir="/srv/app",
    )


class ReleaseDpRuntimeContractEvidenceTest(unittest.TestCase):
    def test_shipped_candidate_policy_is_empty_and_cannot_unlock_runtime(self):
        policy_path = SCRIPT_DIR.parent / (
            "src/main/resources/META-INF/nuono/"
            "dp-runtime-provider-contract-policy-v1.json"
        )
        policy = json.loads(policy_path.read_text(encoding="utf-8"))
        self.assertTrue(policy["requirements"])
        self.assertTrue(all(
            item["approved_source_sha256"] == []
            for item in policy["requirements"]
        ))

    def test_managed_source_is_verified_before_maintenance_and_bound_to_slot(self):
        script = build_cutover()
        load = script.index("load_dp_runtime_contract_evidence\n", script.index("validate_cutover"))
        maintenance = script.index("switch_nginx_to_maintenance\n", load)
        persist = script.index("persist_dp_runtime_contract_evidence_for_target\n", load)
        environment = script.index("prepare_dp10_probe_runtime_environment\n", persist)
        start = script.index('start_runtime "$TARGET_SLOT_DIR"', environment)

        self.assertLess(load, maintenance)
        self.assertLess(persist, environment)
        self.assertLess(environment, start)
        self.assertIn("$APP_DIR/.dp-runtime-provider-contracts.json", script)
        self.assertIn("dp-runtime-contract-evidence.json", script)
        for key in (
            "NUONO_DP_RUNTIME_CONTRACT_EVIDENCE_FILE",
            "NUONO_DP_RUNTIME_CONTRACT_EVIDENCE_SHA256",
            "NUONO_DP_RUNTIME_RELEASE_ENV_SHA256_FILE",
        ):
            self.assertIn(f"runtime_format+='{key}=", script)
            self.assertIn(f'grep -Fxc "{key}=', script)

    def test_exact_fresh_bundle_passes_but_semantic_or_expiry_drift_blocks(self):
        with tempfile.TemporaryDirectory() as raw:
            app = pathlib.Path(raw)
            source = app / ".dp-runtime-provider-contracts.json"
            jar, jar_sha = self.write_policy_jar(app, approved=True)
            payload = self.payload(jar_sha)
            self.write_source(source, payload)
            self.assertEqual(0, self.run_load(app, jar, jar_sha).returncode)

            payload["evidence"][1]["status"] = "READY"
            self.write_source(source, payload)
            self.assertNotEqual(0, self.run_load(app, jar, jar_sha).returncode)

            payload = self.payload(jar_sha)
            payload["evidence"][3]["expires_at"] = (
                dt.datetime.now(dt.timezone.utc) - dt.timedelta(seconds=1)
            ).isoformat().replace("+00:00", "Z")
            self.write_source(source, payload)
            self.assertNotEqual(0, self.run_load(app, jar, jar_sha).returncode)

    def test_bundle_rejects_candidate_or_source_identity_drift(self):
        with tempfile.TemporaryDirectory() as raw:
            app = pathlib.Path(raw)
            source = app / ".dp-runtime-provider-contracts.json"
            jar, jar_sha = self.write_policy_jar(app, approved=True)
            payload = self.payload(jar_sha)
            payload["manifest_commit"] = "e" * 40
            self.write_source(source, payload)
            self.assertNotEqual(0, self.run_load(app, jar, jar_sha).returncode)

            payload = self.payload(jar_sha)
            payload["evidence"][0]["source_identity_sha256"] = "unbound"
            self.write_source(source, payload)
            self.assertNotEqual(0, self.run_load(app, jar, jar_sha).returncode)

    def test_empty_or_nonmatching_candidate_allowlist_blocks_valid_dynamic_bundle(self):
        with tempfile.TemporaryDirectory() as raw:
            app = pathlib.Path(raw)
            source = app / ".dp-runtime-provider-contracts.json"
            jar, jar_sha = self.write_policy_jar(app, approved=False)
            self.write_source(source, self.payload(jar_sha))

            result = self.run_load(app, jar, jar_sha)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("not candidate-approved", result.stderr)

    def payload(self, jar_sha: str) -> dict[str, object]:
        now = dt.datetime.now(dt.timezone.utc)
        evidence = []
        for index, (requirement, source) in enumerate(SOURCES.items(), start=1):
            evidence.append({
                "requirement": requirement,
                "status": "CONTRACT_PROVEN",
                "source_kind": source,
                "source_identity_sha256": format(index, "x") * 64,
                "verified_at": (now - dt.timedelta(minutes=1)).isoformat().replace(
                    "+00:00", "Z"
                ),
                "expires_at": (now + dt.timedelta(hours=1)).isoformat().replace(
                    "+00:00", "Z"
                ),
            })
        return {
            "schema": "nuono.dp-runtime-provider-contracts/v1",
            "type": "DP_RUNTIME_PROVIDER_CONTRACTS",
            "manifest_commit": COMMIT,
            "candidate_jar_sha256": jar_sha,
            "evidence": evidence,
        }

    def write_policy_jar(self, app: pathlib.Path, approved: bool) -> tuple[pathlib.Path, str]:
        requirements = []
        for index, (requirement, source) in enumerate(SOURCES.items(), start=1):
            requirements.append({
                "requirement": requirement,
                "source_kind": source,
                "approved_source_sha256": [format(index, "x") * 64] if approved else [],
            })
        policy = {
            "schema": "nuono.dp-runtime-provider-contract-policy/v1",
            "requirements": requirements,
        }
        jar = app / "candidate.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr(
                "BOOT-INF/classes/META-INF/nuono/"
                "dp-runtime-provider-contract-policy-v1.json",
                json.dumps(policy),
            )
        jar_sha = hashlib.sha256(jar.read_bytes()).hexdigest()
        return jar, jar_sha

    def write_source(self, path: pathlib.Path, payload: dict[str, object]) -> None:
        path.write_text(json.dumps(payload), encoding="utf-8")
        path.chmod(0o600)

    def run_load(
        self,
        app: pathlib.Path,
        jar: pathlib.Path,
        jar_sha: str,
    ) -> subprocess.CompletedProcess[str]:
        fragment = build_dp_runtime_contract_evidence_shell()
        script = f'''set -euo pipefail
APP_DIR={app!s}
STAGED_JAR={jar!s}
EXPECTED_COMMIT={COMMIT}
EXPECTED_JAR_SHA256={jar_sha}
secure_file_operation() {{
  [ "$1" = verify ] || return 1
  local file="$2" expected="$4" actual
  actual="$(python3 - "$file" <<'PY'
import hashlib, pathlib, stat, sys
path = pathlib.Path(sys.argv[1])
if stat.S_IMODE(path.stat().st_mode) != 0o600:
    raise SystemExit(1)
print(hashlib.sha256(path.read_bytes()).hexdigest())
PY
)"
  [ "$expected" = - ] || [ "$expected" = "$actual" ] || return 1
  printf '%s' "$actual"
}}
{fragment}
load_dp_runtime_contract_evidence
'''
        return subprocess.run(
            ["bash", "-c", script],
            text=True,
            capture_output=True,
            env={"PATH": os.environ["PATH"]},
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
