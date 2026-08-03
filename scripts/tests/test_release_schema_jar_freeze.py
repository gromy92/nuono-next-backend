from __future__ import annotations

import hashlib
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


TEMPLATE = (
    Path(__file__).parents[1]
    / "release_templates"
    / "additive_frozen_jar.sh"
)


class ReleaseSchemaJarFreezeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.source = self.root / "staged.jar"
        self.destination = self.root / "private" / "staged.jar"
        self.destination.parent.mkdir(mode=0o700)
        self.content = b"governed-jar-bytes"
        self.source.write_bytes(self.content)
        self.sha256 = hashlib.sha256(self.content).hexdigest()

    def tearDown(self):
        self.temporary.cleanup()

    def test_freezes_the_verified_bytes_into_a_read_only_private_file(self):
        result = self.freeze(self.sha256)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.content, self.destination.read_bytes())
        self.assertEqual(
            0o400,
            stat.S_IMODE(self.destination.stat().st_mode),
        )

    def test_checksum_mismatch_removes_only_the_new_partial_copy(self):
        result = self.freeze("0" * 64)

        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.destination.exists())
        self.assertIn("checksum does not match", result.stderr)

    def test_existing_evidence_is_never_deleted_on_name_collision(self):
        self.destination.write_bytes(b"existing-release-evidence")

        result = self.freeze(self.sha256)

        self.assertNotEqual(0, result.returncode)
        self.assertEqual(
            b"existing-release-evidence",
            self.destination.read_bytes(),
        )

    def freeze(self, expected_sha256):
        script = (
            'set -Eeuo pipefail\n'
            'STAGED_JAR="$1"\n'
            'FROZEN_JAR="$2"\n'
            'EXPECTED_JAR_SHA256="$3"\n'
            'source "$4"\n'
            'freeze_staged_jar\n'
        )
        return subprocess.run(
            [
                "bash",
                "-c",
                script,
                "freeze-test",
                str(self.source),
                str(self.destination),
                expected_sha256,
                str(TEMPLATE),
            ],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
