import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.tests.test_release_maintenance_probe import (
    build_script,
    function_from,
)


MANAGED_ACTIVE = "# NUONO_BLUE_GREEN_MANAGED\nserver 127.0.0.1:18087;\n"


def upstream_functions(script):
    secure_files = function_from(
        script, "secure_file_operation", "assert_dp10_probe_marker"
    )
    upstream = function_from(script, "nginx_upstream_operation", "switch_nginx_to_port")
    return secure_files + upstream


def run_bash(body):
    return subprocess.run(
        ["bash", "-c", body],
        text=True,
        capture_output=True,
        check=False,
    )


class ReleaseNginxUpstreamTest(unittest.TestCase):
    def test_switch_backup_and_restore_are_atomic_and_digest_bound(self):
        functions = upstream_functions(build_script())
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            backup_dir = directory / "backup"
            backup_dir.mkdir(mode=0o700)
            upstream = directory / "upstream.inc"
            backup = backup_dir / "upstream.before"
            upstream.write_text(MANAGED_ACTIVE, encoding="utf-8")
            upstream.chmod(0o644)
            initial_inode = upstream.stat().st_ino

            result = run_bash(f'''set -uo pipefail
NGINX_UPSTREAM_FILE={str(upstream)!r}
UPSTREAM_BACKUP={str(backup)!r}
NGINX_UPSTREAM_SHA256=""
NGINX_UPSTREAM_ORIGINAL_SHA256=""
NGINX_UPSTREAM_BACKUP_SHA256=""
{functions}
bind_nginx_upstream 18087
backup_nginx_upstream "$UPSTREAM_BACKUP"
write_upstream_port 18089
printf 'switched=%s\n' "$(current_upstream_port)"
restore_nginx_upstream
printf 'restored=%s\n' "$(current_upstream_port)"
''')

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("switched=18089\nrestored=18087\n", result.stdout)
            self.assertEqual(MANAGED_ACTIVE, upstream.read_text(encoding="utf-8"))
            self.assertEqual(MANAGED_ACTIVE, backup.read_text(encoding="utf-8"))
            self.assertNotEqual(initial_inode, upstream.stat().st_ino)
            self.assertEqual(0o600, stat.S_IMODE(backup.stat().st_mode))
            self.assertEqual(1, backup.stat().st_nlink)

    def test_symlink_and_hardlink_upstreams_are_rejected(self):
        functions = upstream_functions(build_script())
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            real = directory / "real.inc"
            real.write_text(MANAGED_ACTIVE, encoding="utf-8")
            real.chmod(0o644)
            symlink = directory / "symlink.inc"
            symlink.symlink_to(real)
            hardlink = directory / "hardlink.inc"
            os.link(real, hardlink)

            for candidate in (symlink, hardlink):
                with self.subTest(candidate=candidate.name):
                    result = self.bind(functions, candidate)
                    self.assertNotEqual(0, result.returncode)

            self.assertEqual(MANAGED_ACTIVE, real.read_text(encoding="utf-8"))

    def test_switch_and_restore_reject_digest_drift_without_overwrite(self):
        functions = upstream_functions(build_script())
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            backup_dir = directory / "backup"
            backup_dir.mkdir(mode=0o700)
            upstream = directory / "upstream.inc"
            backup = backup_dir / "upstream.before"
            upstream.write_text(MANAGED_ACTIVE, encoding="utf-8")
            upstream.chmod(0o644)
            drift = MANAGED_ACTIVE + "# concurrent drift\n"

            switch_drift = run_bash(f'''set -uo pipefail
NGINX_UPSTREAM_FILE={str(upstream)!r}
NGINX_UPSTREAM_SHA256=""
NGINX_UPSTREAM_ORIGINAL_SHA256=""
{functions}
bind_nginx_upstream 18087
printf '%b' {drift!r} > "$NGINX_UPSTREAM_FILE"
if write_upstream_port 18089; then exit 91; fi
''')
            self.assertEqual(0, switch_drift.returncode, switch_drift.stderr)
            self.assertEqual(drift, upstream.read_text(encoding="utf-8"))

            upstream.write_text(MANAGED_ACTIVE, encoding="utf-8")
            restore_drift = run_bash(f'''set -uo pipefail
NGINX_UPSTREAM_FILE={str(upstream)!r}
UPSTREAM_BACKUP={str(backup)!r}
NGINX_UPSTREAM_SHA256=""
NGINX_UPSTREAM_ORIGINAL_SHA256=""
NGINX_UPSTREAM_BACKUP_SHA256=""
{functions}
bind_nginx_upstream 18087
backup_nginx_upstream "$UPSTREAM_BACKUP"
write_upstream_port 18089
printf '%b' {drift!r} > "$NGINX_UPSTREAM_FILE"
if restore_nginx_upstream; then exit 92; fi
''')
            self.assertEqual(0, restore_drift.returncode, restore_drift.stderr)
            self.assertEqual(drift, upstream.read_text(encoding="utf-8"))

    def bind(self, functions, path):
        return run_bash(f'''set -uo pipefail
NGINX_UPSTREAM_FILE={str(path)!r}
NGINX_UPSTREAM_SHA256=""
NGINX_UPSTREAM_ORIGINAL_SHA256=""
{functions}
bind_nginx_upstream 18087
''')


if __name__ == "__main__":
    unittest.main()
