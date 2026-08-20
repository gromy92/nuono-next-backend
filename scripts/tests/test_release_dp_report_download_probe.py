import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_DIR = Path(__file__).parents[1]
MODULE_PATH = SCRIPT_DIR / "release_single_scheduler_cutover.py"
sys.path.insert(0, str(SCRIPT_DIR))


def build_script():
    spec = importlib.util.spec_from_file_location("release_single_scheduler_cutover", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.build_single_scheduler_cutover_script(
        staged_jar="/staged/backend.jar",
        expected_jar_sha256="a" * 64,
        expected_commit="c" * 40,
        expected_active_jar_sha256="b" * 64,
        expected_active_pid=4242,
        expected_nginx_upstream_sha256="d" * 64,
        expected_topology_cas_sha256="e" * 64,
        active_slot="blue",
        target_slot="green",
        active_port=18087,
        target_port=18088,
        maintenance_port=18089,
        nginx_upstream_file="/managed/upstream.inc",
        release_name="dp-runtime-cutover-test",
        external_health_url="https://www.nuoon.com/ai/actuator/health",
        app_dir="/app",
    )


def embedded_probe():
    script = build_script()
    start = script.index("import datetime as dt, email.utils")
    end = script.index("\nPY\n  unset DP_REPORT_PROBE_NONCE", start)
    return script[start:end]


class FakeResponse:
    def __init__(self, status, headers, url, body=b"x"):
        self.status = status
        self.headers = headers
        self.url = url
        self.body = body
        self.offset = 0

    def read(self, size=-1):
        if size < 0:
            size = len(self.body) - self.offset
        result = self.body[self.offset:self.offset + size]
        self.offset += len(result)
        return result

    def geturl(self):
        return self.url

    def close(self):
        return None


class ReleaseDpReportDownloadProbeTest(unittest.TestCase):
    def test_probe_is_candidate_bound_and_requires_secure_ephemeral_source(self):
        script = build_script()

        self.assertIn("candidate Jar report download probe marker mismatch", script)
        self.assertIn("candidate Jar validator rejection marker mismatch", script)
        self.assertIn("candidate Jar report source command is missing", script)
        self.assertIn('dp-report-download-probe-source', script)
        self.assertIn('DP_REPORT_PROBE_SOURCE_FILE="$DP_REPORT_PROBE_DIR/source-url"', script)
        self.assertIn('--env-file "$APP_DIR/.env"', script)
        self.assertNotIn('"$APP_DIR/.dp-report-download-probe-url"', script)
        self.assertIn(
            'secure_file_operation verify "$DP_REPORT_PROBE_SOURCE_FILE" 600',
            script,
        )
        self.assertIn('"manifest_commit": commit', script)
        self.assertIn('"candidate_jar_sha256": jar_sha', script)
        self.assertIn('parsed.hostname != "storage.googleapis.com"', script)
        self.assertIn('/noonprd-mp-gcs--partner-impex/', script)
        self.assertIn('data["manifest_commit"] != commit', script)
        self.assertIn(
            'data["candidate_jar_sha256"] != jar_sha',
            script,
        )

    def test_probe_proves_range_and_both_if_range_outcomes(self):
        script = build_script()

        required = (
            'request("bytes=0-0", exact_body=True)',
            'request(\n    "bytes=1-1", validator, exact_body=True)',
            '"bytes=1-1", stale, exact_partial_body=True)',
            'matching_status != 206',
            'stale_status == 206',
            '"range_contract": "CONTRACT_PROVEN"',
            '"matching_if_range_contract": "CONTRACT_PROVEN"',
            '"stale_if_range_contract": stale_contract',
        )
        for marker in required:
            self.assertIn(marker, script)
        self.assertIn(
            'response.read(2 if exact_body or exact_partial_body else 1)',
            script,
        )
        self.assertIn('if exact_body and (len(body) != 1 or response.read(1))', script)

    def test_only_strong_etag_or_valid_last_modified_can_pass(self):
        script = build_script()

        self.assertIn('not etag.lower().startswith("w/")', script)
        self.assertIn('validator_kind, validator = "STRONG_ETAG", etag', script)
        self.assertIn('validator_kind, validator = "LAST_MODIFIED", last_modified', script)
        self.assertIn(
            'data["validator_kind"] not in {"STRONG_ETAG", "LAST_MODIFIED"}',
            script,
        )
        self.assertIn("report transport resumable validator absent", script)

    def test_probe_uses_managed_ca_bundle_without_disabling_tls_verification(self):
        script = build_script()

        self.assertIn("import certifi", script)
        self.assertIn(
            "tls_context = ssl.create_default_context(cafile=certifi.where())",
            script,
        )
        self.assertIn("timeout=20, context=tls_context", script)
        self.assertNotIn("_create_unverified_context", script)
        self.assertNotIn("CERT_NONE", script)

    def test_probe_and_fresh_evidence_gate_precede_service_mutation(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        probe = execution.index("run_dp_report_download_probe")
        verify = execution.index("verify_dp_report_probe_state", probe)
        maintenance = execution.index("start_maintenance_responder")
        stop = execution.index('stop_pid "$ACTIVE_PID"')

        self.assertLess(probe, verify)
        self.assertLess(verify, maintenance)
        self.assertLess(verify, stop)

    def test_missing_stale_contract_or_expired_evidence_is_fail_closed(self):
        script = build_script()

        self.assertIn('"CLIENT_VALIDATOR_REJECTION_PROVEN"', script)
        self.assertIn('"SERVER_FULL_BODY_PROVEN"', script)
        self.assertIn('"nuono.dp-report-download-transport/v2"', script)
        self.assertIn('expires <= now', script)
        self.assertIn(
            '(data["initial_status"], data["matching_status"]) != ("206", "206")',
            script,
        )

    def test_ignored_stale_if_range_is_accepted_only_with_the_live_validator(self):
        url = "https://storage.googleapis.com/noonprd-mp-gcs--partner-impex/report.csv?Expires=9999999999"
        headers = {
            "Content-Range": "bytes 0-0/10", "Content-Length": "1",
            "Content-Encoding": "identity", "ETag": '"live"',
        }
        responses = [
            FakeResponse(206, headers, url),
            FakeResponse(206, {**headers, "Content-Range": "bytes 1-1/10"}, url),
            FakeResponse(206, {**headers, "Content-Range": "bytes 1-1/10"}, url),
        ]
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source"
            evidence = Path(directory) / "evidence"
            source.write_text(url, encoding="utf-8")
            argv = ["probe", str(source), str(evidence), "nonce", "c" * 40, "a" * 64]
            with mock.patch.object(sys, "argv", argv), mock.patch(
                    "urllib.request.urlopen", side_effect=responses):
                exec(compile(embedded_probe(), "<embedded-probe>", "exec"), {})
            payload = json.loads(evidence.read_text(encoding="utf-8"))
        self.assertEqual("206", payload["stale_status"])
        self.assertEqual(
            "CLIENT_VALIDATOR_REJECTION_PROVEN",
            payload["stale_if_range_contract"],
        )

    def test_ignored_stale_if_range_with_a_changed_validator_fails_closed(self):
        url = "https://storage.googleapis.com/noonprd-mp-gcs--partner-impex/report.csv?Expires=9999999999"
        headers = {
            "Content-Range": "bytes 0-0/10", "Content-Length": "1",
            "Content-Encoding": "identity", "ETag": '"live"',
        }
        responses = [
            FakeResponse(206, headers, url),
            FakeResponse(206, {**headers, "Content-Range": "bytes 1-1/10"}, url),
            FakeResponse(206, {
                **headers, "Content-Range": "bytes 1-1/10", "ETag": '"changed"',
            }, url),
        ]
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source"
            evidence = Path(directory) / "evidence"
            source.write_text(url, encoding="utf-8")
            argv = ["probe", str(source), str(evidence), "nonce", "c" * 40, "a" * 64]
            with mock.patch.object(sys, "argv", argv), mock.patch(
                    "urllib.request.urlopen", side_effect=responses):
                with self.assertRaisesRegex(SystemExit, "ignored stale If-Range"):
                    exec(compile(embedded_probe(), "<embedded-probe>", "exec"), {})
            self.assertFalse(evidence.exists())

    def test_generated_release_script_is_valid_bash(self):
        result = subprocess.run(
            ["bash", "-n"], input=build_script(), text=True,
            capture_output=True, check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
