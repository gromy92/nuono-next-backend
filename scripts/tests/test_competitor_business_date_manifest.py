from __future__ import annotations

import os
import stat
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from competitor_business_date.manifest import (  # noqa: E402
    ManifestError,
    ManifestReader,
    ManifestWriter,
    classify_row_state,
    copy_manifest_backup,
    row_digest,
    sha256_file,
)


class CompetitorBusinessDateManifestTest(unittest.TestCase):
    def test_seals_private_manifest_with_stable_content_digest(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "manifest.sqlite"
            writer = ManifestWriter(
                path,
                {
                    "run_id": "date-fix-1",
                    "schema_version": 1,
                    "target_schema": "nuonuoai",
                },
            )
            writer.add_change(
                group_kind="snapshot_chain",
                group_key="1|SELF|Z1",
                table_name="operations_competitor_product_snapshot",
                primary_key="10",
                action="UPDATE",
                pre={"id": 10, "fact_date": "2026-07-01"},
                post={"id": 10, "fact_date": "2026-07-02"},
            )
            seal = writer.seal()

            self.assertEqual(0, stat.S_IMODE(path.stat().st_mode) & 0o077)
            self.assertEqual(sha256_file(path), seal.file_sha256)
            with ManifestReader(path, seal.file_sha256) as reader:
                self.assertEqual("date-fix-1", reader.metadata["run_id"])
                changes = list(reader.iter_changes())
                self.assertEqual(1, len(changes))
                self.assertEqual(
                    row_digest({"id": 10, "fact_date": "2026-07-01"}),
                    changes[0].pre_digest,
                )
                self.assertEqual(
                    reader.compute_content_digest(),
                    reader.metadata["content_digest"],
                )

    def test_refuses_existing_or_symlink_manifest_target(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            existing = root / "existing.sqlite"
            existing.write_bytes(b"keep")
            with self.assertRaisesRegex(ManifestError, "already exists"):
                ManifestWriter(existing, {"run_id": "x"})

            target = root / "target"
            target.write_text("keep", encoding="utf-8")
            link = root / "link.sqlite"
            link.symlink_to(target)
            with self.assertRaisesRegex(ManifestError, "symbolic link"):
                ManifestWriter(link, {"run_id": "x"})

    def test_reader_rejects_wrong_sha_and_content_tampering(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "manifest.sqlite"
            writer = ManifestWriter(path, {"run_id": "date-fix-1"})
            writer.add_change(
                group_kind="rank_run",
                group_key="20",
                table_name="operations_competitor_rank_fact",
                primary_key="30",
                action="UPDATE",
                pre={"id": 30, "fact_time": "2026-07-01 16:00:00"},
                post={"id": 30, "fact_time": "2026-07-02 00:00:00"},
            )
            seal = writer.seal()

            with self.assertRaisesRegex(ManifestError, "SHA-256 mismatch"):
                ManifestReader(path, "0" * 64)

            os.chmod(path, 0o600)
            import sqlite3

            connection = sqlite3.connect(path)
            connection.execute(
                "UPDATE correction_change SET post_json = ? WHERE ordinal = 1",
                ('{"fact_time":"tampered","id":30}',),
            )
            connection.commit()
            connection.close()
            tampered_sha = sha256_file(path)
            with self.assertRaisesRegex(ManifestError, "content digest mismatch"):
                ManifestReader(path, tampered_sha)
            self.assertNotEqual(seal.file_sha256, tampered_sha)

    def test_backup_is_distinct_private_and_byte_identical(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = root / "manifest.sqlite"
            writer = ManifestWriter(path, {"run_id": "date-fix-1"})
            writer.add_change(
                group_kind="keyword_run",
                group_key="20",
                table_name="operations_competitor_keyword_run",
                primary_key="20",
                action="UPDATE",
                pre={"id": 20, "captured_at": "2026-07-01 16:00:00"},
                post={"id": 20, "captured_at": "2026-07-02 00:00:00"},
            )
            seal = writer.seal()
            backup = root / "backup" / "manifest.sqlite"

            backup_sha = copy_manifest_backup(path, backup, seal.file_sha256)

            self.assertEqual(seal.file_sha256, backup_sha)
            self.assertEqual(path.read_bytes(), backup.read_bytes())
            self.assertEqual(0, stat.S_IMODE(backup.stat().st_mode) & 0o077)
            with self.assertRaisesRegex(ManifestError, "already exists"):
                copy_manifest_backup(path, backup, seal.file_sha256)

    def test_classifies_only_exact_pre_or_post_state(self):
        pre = {"id": 1, "fact_date": "2026-07-01"}
        post = {"id": 1, "fact_date": "2026-07-02"}
        self.assertEqual("PRE", classify_row_state(pre, pre, post))
        self.assertEqual("POST", classify_row_state(post, pre, post))
        self.assertEqual(
            "CONFLICT",
            classify_row_state({"id": 1, "fact_date": "2026-07-03"}, pre, post),
        )
        self.assertEqual("ABSENT", classify_row_state(None, None, post))
        self.assertEqual("POST", classify_row_state(post, None, post))


if __name__ == "__main__":
    unittest.main()
