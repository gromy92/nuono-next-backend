import importlib.util
import datetime as dt
import hashlib
import json
import shlex
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).parents[1]
MODULE_PATH = SCRIPT_DIR / "release_cutover_maintenance.py"
SOURCE_ROOT = SCRIPT_DIR.parent


def load_module():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def secure_file_source(script):
    function = script[
        script.index("secure_file_operation()"):
        script.index("assert_dp10_probe_marker()")
    ]
    return function.split('python3 - "$@" <<\'PY\'\n', 1)[1].split("\nPY", 1)[0]


class ReleaseDp10OpenApiProbeTest(unittest.TestCase):
    def build_script(self):
        return load_module().build_single_scheduler_cutover_script(
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
            release_name="dp10-probe-test",
            external_health_url="https://www.nuoon.com/ai/actuator/health",
            app_dir="/app",
        )

    def test_probe_precedes_every_maintenance_or_runtime_mutation(self):
        execution = self.build_script().split("\nvalidate_cutover\n", 1)[1]
        probe = execution.index("run_dp10_openapi_probe")
        prepare = execution.index(
            'secure_file_operation directory "$TARGET_SLOT_DIR"'
        )
        maintenance = execution.index("start_maintenance_responder")
        old_stop = execution.index('stop_pid "$ACTIVE_PID"')

        self.assertLess(probe, prepare)
        self.assertLess(probe, maintenance)
        self.assertLess(probe, old_stop)

    def test_probe_is_exact_candidate_one_shot_without_a_refresh_bypass_flag(self):
        script = self.build_script()

        self.assertIn(
            'timeout --signal=TERM --kill-after=5s 95s java -jar \\\n'
            '    "$STAGED_JAR" dp10-openapi-contract-probe',
            script,
        )
        self.assertIn("command -v timeout", script)
        self.assertIn('--manifest-commit "$EXPECTED_COMMIT"', script)
        self.assertIn('--expected-jar-sha256 "$EXPECTED_JAR_SHA256"', script)
        self.assertNotIn("refresh-token", script.lower())
        self.assertNotIn("provider-result", script)
        self.assertNotIn("evidence-input", script)

    def test_probe_refresh_is_bound_to_the_exact_managed_canary_revision(self):
        source = (SOURCE_ROOT / (
            "src/main/java/com/nuono/next/procurement/aliorder/"
            "Ali1688Dp10OpenApiProbeAuthorizationSource.java"
        )).read_text()
        updater = (SOURCE_ROOT / (
            "src/main/java/com/nuono/next/procurement/aliorder/"
            "Ali1688Dp10OpenApiProbeAuthorizationUpdater.java"
        )).read_text()

        self.assertIn("NUONO_DP10_OPEN_API_PROBE_CANARY_OWNER_USER_ID", source)
        self.assertIn("NUONO_DP10_OPEN_API_PROBE_CANARY_PROVIDER_ACCOUNT_ID", source)
        self.assertIn("auth.owner_user_id = ?", source)
        self.assertIn("BINARY auth.provider_account_id = BINARY ?", source)
        self.assertIn("statement.setLong(1, canaryOwner)", source)
        self.assertIn("statement.setString(2, canaryAccount)", source)
        self.assertIn("auth.refresh_token_cipher", source)
        self.assertIn("auth.gmt_updated AS authorization_revision", source)
        self.assertIn("BINARY provider_account_id = BINARY ?", updater)
        self.assertIn("access_token_cipher <=> ?", updater)
        self.assertIn("refresh_token_cipher <=> ?", updater)
        self.assertIn("expires_at <=> ? AND gmt_updated <=> ?", updater)
        self.assertIn("connection.setAutoCommit(false)", updater)
        self.assertIn("if (affected == 1)", updater)
        self.assertIn("connection.rollback()", updater)

    def test_nonce_and_candidate_bindings_are_validated_before_external_calls(self):
        source = (SOURCE_ROOT / (
            "src/main/java/com/nuono/next/procurement/aliorder/"
            "Ali1688Dp10OpenApiProbeCommand.java"
        )).read_text()

        external = source.index("new Ali1688Dp10OpenApiProbeAuthorizationSource().select")
        self.assertLess(source.index("String nonce = requireNonce()"), external)
        self.assertLess(source.index("PROBE_JAR_SHA_MISMATCH"), external)
        self.assertLess(source.index('40,\n                    "PROBE_COMMIT_INVALID"'), external)

    def test_evidence_is_rechecked_before_start_and_before_cutover(self):
        execution = self.build_script().split("\nvalidate_cutover\n", 1)[1]
        target_start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"')
        target_switch = execution.index('switch_nginx_to_port "$TARGET_PORT"')
        checks = [
            index for index in range(len(execution))
            if execution.startswith("verify_dp10_probe_state", index)
        ]

        self.assertGreaterEqual(len(checks), 3)
        self.assertTrue(any(index < target_start for index in checks))
        self.assertTrue(any(target_start < index < target_switch for index in checks))
        self.assertIn('stat -c \'%a\' "$DP10_PROBE_DIR"', self.build_script())
        self.assertIn(
            'secure_file_operation verify "$DP10_PROBE_EVIDENCE_FILE" 600',
            self.build_script(),
        )

    def test_fresh_gate_rejects_stale_candidate_evidence(self):
        now = dt.datetime.now(dt.timezone.utc)
        stale = self.probe_evidence(now - dt.timedelta(minutes=20))
        fresh = self.probe_evidence(now - dt.timedelta(minutes=1))

        self.assertNotEqual(0, self.verify_evidence(stale).returncode)
        self.assertEqual(0, self.verify_evidence(fresh).returncode)

    def test_verified_proof_is_atomically_persisted_in_target_slot(self):
        script = self.build_script()
        execution = script.split("\nvalidate_cutover\n", 1)[1]

        self.assertLess(
            execution.index("persist_dp10_probe_for_target"),
            execution.index("prepare_dp10_probe_runtime_environment"),
        )
        self.assertLess(
            execution.index("prepare_dp10_probe_runtime_environment"),
            execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"'),
        )
        self.assertIn('$TARGET_SLOT_DIR/.release-evidence/', script)
        self.assertIn("os.link(", script)
        self.assertIn(
            '"$DP10_SLOT_EVIDENCE_FILE" 600 600 600 '
            '"$DP10_PROBE_EVIDENCE_SHA256"',
            script,
        )
        self.assertNotIn('>> "$TARGET_SLOT_DIR/.env"', script)

    def test_candidate_runtime_env_has_one_execution_mode_and_no_retired_toggle(self):
        script = self.build_script()

        self.assertIn(
            "runtime_format+='NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME\\n'",
            script,
        )
        self.assertIn(
            'grep -Fxc "NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME"',
            script,
        )
        self.assertIn(
            "! grep -Eq '^NUONO_DATA_PULL_(EXECUTION_MODE|RUNTIME_ENABLED)='",
            script,
        )
        self.assertIn(
            "! grep -Eq '^NUONO_DATA_PULL_RUNTIME_ENABLED='",
            script,
        )

    def test_slot_persistence_is_create_new_0600_and_byte_identical(self):
        script = self.build_script()
        python_source = secure_file_source(script)
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "source.json"
            target_dir = directory / "slot-evidence"
            target_dir.mkdir(mode=0o700)
            target = target_dir / "evidence.json"
            payload = b'{"contract":"proven"}\n'
            source.write_bytes(payload)
            source.chmod(0o600)
            arguments = [
                "python3", "-", "install", str(source), str(target),
                "600", "600", "600", hashlib.sha256(payload).hexdigest(),
                "create-new", "",
            ]

            first = subprocess.run(
                arguments,
                input=python_source,
                text=True,
                capture_output=True,
                check=False,
            )
            second = subprocess.run(
                arguments,
                input=python_source,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(0, first.returncode, first.stderr)
            self.assertNotEqual(0, second.returncode)
            self.assertEqual(payload, target.read_bytes())
            self.assertEqual(0o600, target.stat().st_mode & 0o777)
            self.assertEqual([target], list(target_dir.iterdir()))

    def test_rollback_restarts_predecessor_with_its_own_slot_environment(self):
        script = self.build_script()
        restart = script[
            script.index("restart_old_runtime()"):
            script.index("restore_nginx_to_active()")
        ]

        self.assertIn('start_runtime "$ACTIVE_RUN_DIR" "$ACTIVE_PORT"', restart)
        self.assertNotIn("DP10_SLOT_EVIDENCE_FILE", restart)

    def test_one_shot_dispatches_before_spring_web_or_scheduler_start(self):
        source = (SOURCE_ROOT / "src/main/java/com/nuono/next/NuonoNextApplication.java").read_text()
        dispatch = source.index("Ali1688Dp10OpenApiProbeCommand.handles(args)")
        spring = source.index("SpringApplication.run")

        self.assertLess(dispatch, spring)
        self.assertIn("System.exit(Ali1688Dp10OpenApiProbeCommand.run(args))", source)

    def test_generated_script_is_valid_bash(self):
        result = subprocess.run(
            ["bash", "-n"],
            input=self.build_script(),
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def probe_evidence(self, verified_at):
        expires_at = verified_at + dt.timedelta(minutes=10)
        return {
            "schema": "nuono.dp10-openapi-execution-contract/v1",
            "type": "DP10_OPEN_API_EXECUTION_CONTRACT",
            "nonce_sha256": "d" * 64,
            "manifest_commit": "c" * 40,
            "candidate_jar_sha256": "a" * 64,
            "endpoint_fingerprint_sha256": "e" * 64,
            "app_key_fingerprint_sha256": "f" * 64,
            "current_list_contract": "CONTRACT_PROVEN",
            "history_list_contract": "CONTRACT_PROVEN",
            "detail_contract": "CONTRACT_PROVEN",
            "verified_at": verified_at.isoformat().replace("+00:00", "Z"),
            "expires_at": expires_at.isoformat().replace("+00:00", "Z"),
        }

    def verify_evidence(self, evidence):
        script = self.build_script()
        function = script[
            script.index("secure_file_operation()"):
            script.index("verify_dp10_probe_state()")
        ]
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "evidence.json"
            raw = (json.dumps(evidence, separators=(",", ":")) + "\n").encode()
            path.write_bytes(raw)
            path.chmod(0o600)
            body = "\n".join((
                "set -Eeuo pipefail",
                f"DP10_PROBE_EVIDENCE_FILE={shlex.quote(str(path))}",
                f"DP10_PROBE_EVIDENCE_SHA256={hashlib.sha256(raw).hexdigest()}",
                f"EXPECTED_COMMIT={'c' * 40}",
                f"EXPECTED_JAR_SHA256={'a' * 64}",
                f"DP10_PROBE_NONCE_SHA256={'d' * 64}",
                function,
                "verify_dp10_probe_json",
            ))
            return subprocess.run(
                ["bash", "-c", body],
                text=True,
                capture_output=True,
                check=False,
            )


if __name__ == "__main__":
    unittest.main()
