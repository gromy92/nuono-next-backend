import hashlib
import importlib.util
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "release_cutover_maintenance.py"


def build_script():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
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
        release_name="runtime-env-attestation-test",
        external_health_url="https://www.nuoon.com/ai/actuator/health",
        app_dir="/app",
    )


def secure_file_source(script):
    function = script[
        script.index("secure_file_operation()"):
        script.index("assert_dp10_probe_marker()")
    ]
    return function.split('python3 - "$@" <<\'PY\'\n', 1)[1].split("\nPY", 1)[0]


def run_secure(python_source, *arguments):
    return subprocess.run(
        ["python3", "-", *map(str, arguments)],
        input=python_source,
        text=True,
        capture_output=True,
        check=False,
    )


class ReleaseDp10RuntimeEnvironmentAttestationTest(unittest.TestCase):
    def test_final_env_precedes_private_attestation_and_all_fresh_checks(self):
        script = build_script()
        prepare = script[
            script.index("prepare_dp10_probe_runtime_environment()"):
            script.index("pid_for_port()")
        ]
        execution = script.split("\nvalidate_cutover\n", 1)[1]
        target_start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"')
        target_switch = execution.index('switch_nginx_to_port "$TARGET_PORT"')
        checks = [
            index for index in range(len(execution))
            if execution.startswith("verify_dp10_probe_state", index)
        ]

        self.assertLess(
            prepare.index("RUNTIME_ENV_SHA256_FILE=%s"),
            prepare.index('TARGET_ENV_SHA256="$(secure_file_operation install'),
        )
        self.assertLess(
            prepare.index('TARGET_ENV_SHA256="$(secure_file_operation install'),
            prepare.index("secure_file_operation write"),
        )
        self.assertIn(
            '"$(dirname "$DP10_RUNTIME_ENV_ATTESTATION_FILE")" = \\\n'
            '      "$(dirname "$DP10_SLOT_EVIDENCE_FILE")"',
            script,
        )
        self.assertIn('[ ! -L "$TARGET_SLOT_DIR/.release-evidence" ]', script)
        self.assertIn(
            'secure_file_operation verify "$APP_DIR/.env" 600',
            script,
        )
        self.assertIn(
            '"$TARGET_SLOT_DIR/.env" 600 600 600',
            prepare,
        )
        self.assertNotIn('>> "$TARGET_SLOT_DIR/.env"', script)
        self.assertNotIn('cp "$APP_DIR/.env"', script)
        self.assertNotIn('cp "$STAGED_JAR"', script)
        self.assertNotIn('cp "$APP_DIR/start-nuono-next-test.sh"', script)
        self.assertIn(
            "runtime_format+='NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME\\n'",
            prepare,
        )
        self.assertIn(
            'grep -Fxc "NUONO_DATA_PULL_EXECUTION_MODE=RUNTIME"',
            script,
        )
        self.assertIn(
            "! grep -Eq '^NUONO_DATA_PULL_RUNTIME_ENABLED='",
            script,
        )
        self.assertTrue(any(index < target_start for index in checks))
        self.assertTrue(any(target_start < index < target_switch for index in checks))
        self.assertNotIn("emit DP10_RUNTIME_ENV", script)
        self.assertNotIn("emit TARGET_ENV_SHA256", script)
        self.assertIn("os.O_NOFOLLOW", script)
        self.assertIn("os.O_CREAT | os.O_EXCL", script)
        self.assertIn("os.replace(", script)
        self.assertIn("os.fsync(parent_descriptor)", script)

    def test_atomic_writer_is_create_new_0600_and_exact_full_env_sha(self):
        script = build_script()
        python_source = secure_file_source(script)
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            env = directory / ".env"
            env.write_bytes(b"APP_SECRET=protected\nCANARY=stable\n")
            release = directory / "release"
            release.mkdir(mode=0o700)
            attestation = release / "runtime-env.sha256"
            digest = hashlib.sha256(env.read_bytes()).hexdigest()
            payload = digest + "\n"

            first = run_secure(
                python_source, "write", attestation, "600", "600",
                "create-new", "utf8", payload,
            )
            second = run_secure(
                python_source, "write", attestation, "600", "600",
                "create-new", "utf8", payload,
            )

            self.assertEqual(0, first.returncode, first.stderr)
            self.assertNotEqual(0, second.returncode)
            self.assertEqual(payload.encode(), attestation.read_bytes())
            self.assertEqual(0o600, attestation.stat().st_mode & 0o777)
            self.assertEqual(1, attestation.stat().st_nlink)
            self.assertEqual([attestation], list(release.iterdir()))

    def test_runtime_env_jar_and_start_script_are_atomically_replaced(self):
        python_source = secure_file_source(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source_dir = directory / "source"
            target_dir = directory / "target"
            source_dir.mkdir(mode=0o700)
            target_dir.mkdir(mode=0o700)
            fixtures = (
                ("env", b"SECRET=value\n", 0o600, 0o600, "600", "\nPORT=18088\n"),
                ("backend.jar", b"candidate-jar", 0o644, 0o600, "600,640,644", ""),
                ("start.sh", b"#!/bin/sh\nexit 0\n", 0o755, 0o700, "700,750,755", ""),
            )
            for name, payload, source_mode, target_mode, modes, suffix in fixtures:
                with self.subTest(name=name):
                    source = source_dir / name
                    target = target_dir / name
                    source.write_bytes(payload)
                    source.chmod(source_mode)
                    target.write_bytes(b"old")
                    target.chmod(source_mode)
                    old_inode = target.stat().st_ino
                    result = run_secure(
                        python_source, "install", source, target, modes,
                        f"{target_mode:o}", modes, hashlib.sha256(payload).hexdigest(),
                        "replace", suffix,
                    )
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual(payload + suffix.encode(), target.read_bytes())
                    self.assertEqual(target_mode, target.stat().st_mode & 0o777)
                    self.assertEqual(1, target.stat().st_nlink)
                    self.assertNotEqual(old_inode, target.stat().st_ino)
            self.assertFalse(any(path.name.endswith(".tmp") for path in target_dir.iterdir()))

    def test_real_symlink_source_and_target_are_rejected_without_mutation(self):
        python_source = secure_file_source(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source_real = directory / "source-real"
            source_real.write_bytes(b"candidate")
            source_real.chmod(0o600)
            source_link = directory / "source-link"
            source_link.symlink_to(source_real)
            target = directory / "target"
            target.write_bytes(b"target-stable")
            target.chmod(0o600)

            source_result = run_secure(
                python_source, "install", source_link, target, "600", "600",
                "600", "-", "replace", "",
            )
            self.assertNotEqual(0, source_result.returncode)
            self.assertEqual(b"target-stable", target.read_bytes())

            protected = directory / "protected"
            protected.write_bytes(b"protected-stable")
            protected.chmod(0o600)
            target.unlink()
            target.symlink_to(protected)
            target_result = run_secure(
                python_source, "install", source_real, target, "600", "600",
                "600", hashlib.sha256(source_real.read_bytes()).hexdigest(),
                "replace", "",
            )
            self.assertNotEqual(0, target_result.returncode)
            self.assertTrue(target.is_symlink())
            self.assertEqual(b"protected-stable", protected.read_bytes())

    def test_real_symlink_target_directory_is_rejected(self):
        python_source = secure_file_source(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "source"
            source.write_bytes(b"candidate")
            source.chmod(0o600)
            protected_dir = directory / "protected-dir"
            protected_dir.mkdir(mode=0o700)
            linked_dir = directory / "linked-dir"
            linked_dir.symlink_to(protected_dir, target_is_directory=True)

            result = run_secure(
                python_source, "install", source, linked_dir / ".env",
                "600", "600", "600",
                hashlib.sha256(source.read_bytes()).hexdigest(),
                "replace", "",
            )

            self.assertNotEqual(0, result.returncode)
            self.assertFalse((protected_dir / ".env").exists())

    def test_real_hardlink_source_and_target_are_rejected_without_mutation(self):
        python_source = secure_file_source(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "source"
            source.write_bytes(b"candidate")
            source.chmod(0o600)
            source_alias = directory / "source-alias"
            os.link(source, source_alias)
            target = directory / "target"
            target.write_bytes(b"target-stable")
            target.chmod(0o600)

            source_result = run_secure(
                python_source, "install", source, target, "600", "600",
                "600", "-", "replace", "",
            )
            self.assertNotEqual(0, source_result.returncode)
            self.assertEqual(b"target-stable", target.read_bytes())

            source_alias.unlink()
            target_alias = directory / "target-alias"
            os.link(target, target_alias)
            target_result = run_secure(
                python_source, "install", source, target, "600", "600",
                "600", hashlib.sha256(source.read_bytes()).hexdigest(),
                "replace", "",
            )
            self.assertNotEqual(0, target_result.returncode)
            self.assertEqual(b"target-stable", target.read_bytes())
            self.assertEqual(b"target-stable", target_alias.read_bytes())
            self.assertEqual(2, target.stat().st_nlink)

    def test_wrong_source_or_existing_target_mode_is_rejected(self):
        python_source = secure_file_source(build_script())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "source"
            target = directory / "target"
            source.write_bytes(b"candidate")
            source.chmod(0o644)
            target.write_bytes(b"target-stable")
            target.chmod(0o600)
            arguments = (
                "install", source, target, "600", "600", "600", "-",
                "replace", "",
            )

            source_result = run_secure(python_source, *arguments)
            self.assertNotEqual(0, source_result.returncode)
            self.assertEqual(b"target-stable", target.read_bytes())

            source.chmod(0o600)
            target.chmod(0o666)
            target_result = run_secure(python_source, *arguments)
            self.assertNotEqual(0, target_result.returncode)
            self.assertEqual(b"target-stable", target.read_bytes())


if __name__ == "__main__":
    unittest.main()
