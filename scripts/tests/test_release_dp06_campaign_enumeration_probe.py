from __future__ import annotations

import datetime as dt
import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).parents[2]
MODULE_PATH = ROOT / "scripts/release_dp06_campaign_enumeration_probe.py"
CONTRACT_ENTRY = (
    "BOOT-INF/classes/META-INF/nuono/"
    "dp06-campaign-enumeration-native-contract-v1.json"
)
NOW = dt.datetime(2026, 8, 4, 4, 0, tzinfo=dt.timezone.utc)


def load_module():
    spec = importlib.util.spec_from_file_location("dp06_probe", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleaseDp06CampaignEnumerationProbeTest(unittest.TestCase):
    def test_candidate_declared_blocker_cannot_emit_proven_evidence(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as raw:
            fixture = Fixture(Path(raw), contract=self.blocked_contract())

            with self.assertRaises(module.ProbeFailure) as raised:
                module.verify_probe(
                    fixture.jar,
                    fixture.manifest,
                    fixture.capture,
                    fixture.evidence,
                    now=NOW,
                )

            self.assertEqual(
                "DP06_UPSTREAM_AUTHORITY_UNPROVEN",
                raised.exception.code,
            )
            self.assertFalse(fixture.evidence.exists())

    def test_reviewed_native_fields_emit_candidate_bound_proof(self):
        module = load_module()
        body = self.proven_body()
        with tempfile.TemporaryDirectory() as raw:
            fixture = Fixture(
                Path(raw),
                contract=self.proven_contract(),
                response_body=body,
            )

            proof = module.verify_probe(
                fixture.jar,
                fixture.manifest,
                fixture.capture,
                fixture.evidence,
                now=NOW,
            )

            self.assertEqual("CONTRACT_PROVEN", proof["status"])
            self.assertEqual(fixture.commit, proof["manifest_commit"])
            self.assertEqual(sha256(fixture.jar.read_bytes()), proof["candidate_jar_sha256"])
            self.assertEqual(2, proof["declared_campaign_count"])
            self.assertEqual(1, proof["active_campaign_count"])
            self.assertEqual(1, proof["dashboard_call_count"])
            with zipfile.ZipFile(fixture.jar) as archive:
                self.assertEqual(sha256(archive.read(CONTRACT_ENTRY)), proof["source_identity_sha256"])
            self.assertEqual(0o600, fixture.evidence.stat().st_mode & 0o777)
            self.assertEqual(proof, json.loads(fixture.evidence.read_bytes()))

    def test_synthetic_parser_authority_is_never_provider_evidence(self):
        module = load_module()
        body = self.proven_body()
        body["campaignCollectionAuthority"] = {
            "generationToken": "test-only",
            "asOfUtc": "2026-08-04T03:58:30Z",
            "declaredCampaignCount": 2,
            "complete": True,
        }
        with tempfile.TemporaryDirectory() as raw:
            fixture = Fixture(Path(raw), self.proven_contract(), body)
            self.assert_failure(
                module, fixture, "DP06_SYNTHETIC_AUTHORITY_REJECTED",
            )

    def test_paginated_endpoint_or_candidate_drift_fails_closed(self):
        module = load_module()
        with tempfile.TemporaryDirectory() as raw:
            fixture = Fixture(Path(raw), self.proven_contract(), self.proven_body())
            capture = json.loads(fixture.capture.read_bytes())
            capture["dashboard_call"]["path"] = (
                "/_svc/productads/v2/noon/metrics/campaigns"
            )
            fixture.capture.write_bytes(canonical(capture))
            fixture.capture.chmod(0o600)
            self.assert_failure(
                module, fixture, "DP06_DASHBOARD_CALL_CONTRACT_INVALID",
            )

        with tempfile.TemporaryDirectory() as raw:
            fixture = Fixture(Path(raw), self.proven_contract(), self.proven_body())
            fixture.jar.write_bytes(fixture.jar.read_bytes() + b"drift")
            self.assert_failure(module, fixture, "DP06_CANDIDATE_BINDING_MISMATCH")

        with tempfile.TemporaryDirectory() as raw:
            fixture = Fixture(Path(raw), self.proven_contract(), self.proven_body())
            fixture.capture.chmod(0o644)
            self.assert_failure(module, fixture, "DP06_CAPTURE_FILE_INVALID")

    def test_checked_in_candidate_contract_remains_explicitly_blocked(self):
        contract_path = ROOT / (
            "src/main/resources/META-INF/nuono/"
            "dp06-campaign-enumeration-native-contract-v1.json"
        )
        contract = json.loads(contract_path.read_bytes())

        self.assertEqual("BLOCKED", contract["status"])
        self.assertEqual(
            {
                "PROVIDER_GENERATION_TOKEN_FIELD_UNPROVEN",
                "PROVIDER_AS_OF_UTC_FIELD_UNPROVEN",
                "PROVIDER_DECLARED_CAMPAIGN_COUNT_FIELD_UNPROVEN",
                "PROVIDER_COMPLETE_FLAG_FIELD_UNPROVEN",
                "SINGLE_DASHBOARD_TOTAL_COVERAGE_UNPROVEN",
            },
            set(contract["blockers"]),
        )

    def test_cli_reports_only_sanitized_blocker_code(self):
        with tempfile.TemporaryDirectory() as raw:
            fixture = Fixture(Path(raw), self.blocked_contract())
            result = subprocess.run(
                [
                    sys.executable, str(MODULE_PATH),
                    "--candidate-jar", str(fixture.jar),
                    "--release-manifest", str(fixture.manifest),
                    "--capture-file", str(fixture.capture),
                    "--evidence-file", str(fixture.evidence),
                ],
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(22, result.returncode)
            self.assertEqual(
                "DP06_COMPLETE_CAMPAIGN_ENUMERATION=FAIL:"
                "DP06_UPSTREAM_AUTHORITY_UNPROVEN\n",
                result.stderr,
            )
            self.assertEqual("", result.stdout)

    def blocked_contract(self) -> dict[str, object]:
        return {
            "schema": "nuono.dp06-campaign-enumeration-native-contract/v1",
            "status": "BLOCKED",
            "endpoint": "/_svc/productads/v2/noon/metrics",
            "response_paths": {
                "generation_token": "",
                "provider_as_of_utc": "",
                "declared_campaign_count": "",
                "complete": "",
            },
            "blockers": [
                "PROVIDER_GENERATION_TOKEN_FIELD_UNPROVEN",
                "PROVIDER_AS_OF_UTC_FIELD_UNPROVEN",
                "PROVIDER_DECLARED_CAMPAIGN_COUNT_FIELD_UNPROVEN",
                "PROVIDER_COMPLETE_FLAG_FIELD_UNPROVEN",
                "SINGLE_DASHBOARD_TOTAL_COVERAGE_UNPROVEN",
            ],
        }

    def proven_contract(self) -> dict[str, object]:
        return {
            "schema": "nuono.dp06-campaign-enumeration-native-contract/v1",
            "status": "PROVEN",
            "endpoint": "/_svc/productads/v2/noon/metrics",
            "response_paths": {
                "generation_token": "/collectionMetadata/generation",
                "provider_as_of_utc": "/collectionMetadata/asOfUtc",
                "declared_campaign_count": "/collectionMetadata/total",
                "complete": "/collectionMetadata/complete",
            },
            "blockers": [],
        }

    def proven_body(self) -> dict[str, object]:
        return {
            "campaigns": [
                {"campaignCode": "C-LIVE", "effectiveStatus": "live"},
                {"campaignCode": "C-PAUSED", "effectiveStatus": "paused"},
            ],
            "current": {"campaignMetrics": {"C-LIVE": {"views": 1}}},
            "collectionMetadata": {
                "generation": "provider-generation-7",
                "asOfUtc": "2026-08-04T03:58:30Z",
                "total": 2,
                "complete": True,
            },
        }

    def assert_failure(self, module, fixture, code: str) -> None:
        with self.assertRaises(module.ProbeFailure) as raised:
            module.verify_probe(
                fixture.jar, fixture.manifest, fixture.capture,
                fixture.evidence, now=NOW,
            )
        self.assertEqual(code, raised.exception.code)
        self.assertFalse(fixture.evidence.exists())


class Fixture:
    def __init__(
            self,
            root: Path,
            contract: dict[str, object],
            response_body: dict[str, object] | None = None,
    ):
        self.root = root
        self.jar = root / "nuono-next-backend.jar"
        self.manifest = root / "release-manifest.json"
        self.capture = root / "dp06-capture.json"
        self.evidence = root / "dp06-evidence.json"
        self.commit = "c" * 40
        self._write_jar(contract)
        self._write_manifest()
        self._write_capture(response_body)

    def _write_jar(self, contract: dict[str, object]) -> None:
        payload = canonical(contract)
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr(CONTRACT_ENTRY, payload)

    def _write_manifest(self) -> None:
        payload = {
            "schema_version": 1,
            "component": "backend",
            "deployable": True,
            "event": "push",
            "ref": "refs/heads/master",
            "commit": self.commit,
            "files": [{
                "path": "nuono-next-backend.jar",
                "sha256": sha256(self.jar.read_bytes()),
                "size": self.jar.stat().st_size,
            }],
        }
        self.manifest.write_bytes(canonical(payload))

    def _write_capture(self, response_body: dict[str, object] | None) -> None:
        payload = {
            "schema": "nuono.dp06-dashboard-boundary-capture/v1",
            "manifest_commit": self.commit,
            "candidate_jar_sha256": sha256(self.jar.read_bytes()),
            "captured_at": "2026-08-04T03:59:00Z",
            "scope_sha256": "d" * 64,
            "dashboard_call": {
                "count": 1,
                "method": "POST",
                "path": "/_svc/productads/v2/noon/metrics",
                "request_body": {
                    "startDate": "2026-08-03",
                    "endDate": "2026-08-03",
                    "campaignFilters": {},
                    "isNamshi": False,
                },
            },
            "response": {
                "status": 200,
                "content_type": "application/json",
                "body": response_body or {
                    "campaigns": [],
                    "current": {"campaignMetrics": {}},
                },
            },
        }
        self.capture.write_bytes(canonical(payload))
        self.capture.chmod(0o600)


def canonical(payload: dict[str, object]) -> bytes:
    return (json.dumps(payload, sort_keys=True, separators=(",", ":")) + "\n").encode()


def sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


if __name__ == "__main__":
    unittest.main()
