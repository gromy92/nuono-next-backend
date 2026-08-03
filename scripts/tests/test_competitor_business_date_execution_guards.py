import sys
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from competitor_business_date.execution import ExecutionError, execute_manifest  # noqa: E402
from scripts.tests.test_competitor_business_date_execution import (  # noqa: E402
    ExecutionTest,
    FakeAdvisoryLock,
    FakeMysql,
    RELEASE_PROVENANCE,
    SERVER_UUID,
    _manifest,
)


class ExecutionGuardTest(unittest.TestCase):
    def _call(
        self,
        root,
        *,
        fence_generation=7,
        outer_fence=lambda: None,
        backup_override=None,
    ):
        manifest, backup, sha = _manifest(root)
        mysql = FakeMysql()
        execute_manifest(
            mysql,
            FakeAdvisoryLock(),
            assert_outer_fence_held=outer_fence,
            manifest=manifest,
            manifest_sha256=sha,
            backup=backup_override or backup,
            fence_generation=fence_generation,
            expected_run_id="correction-1",
            expected_server_uuid=SERVER_UUID,
            release_provenance=RELEASE_PROVENANCE,
            journal=root / "apply.jsonl",
            operation="apply",
            batch_size=5,
        )
        return mysql

    def _patched(self):
        stack = ExitStack()
        for patcher in ExecutionTest()._patches():
            stack.enter_context(patcher)
        return stack

    def test_fence_generation_mismatch_precedes_journal_and_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self._patched():
                with self.assertRaisesRegex(ExecutionError, "fence generation"):
                    self._call(root, fence_generation=8)
            self.assertFalse((root / "apply.jsonl").exists())

    def test_stale_schema_fingerprint_precedes_journal_and_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self._patched() as stack:
                stack.enter_context(patch(
                    "competitor_business_date.execution.read_schema_fingerprint",
                    return_value=([{"fingerprint": "changed"}], "b" * 64),
                ))
                with self.assertRaisesRegex(ExecutionError, "fingerprint differs"):
                    self._call(root)
            self.assertFalse((root / "apply.jsonl").exists())

    def test_outer_fence_loss_precedes_journal_and_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def lost():
                raise ExecutionError("persistent fence lease was reopened")

            with self._patched():
                with self.assertRaisesRegex(ExecutionError, "lease was reopened"):
                    self._call(root, outer_fence=lost)
            self.assertFalse((root / "apply.jsonl").exists())

    def test_change_contract_failure_precedes_journal_and_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self._patched() as stack:
                stack.enter_context(patch(
                    "competitor_business_date.execution.validate_manifest_changes",
                    side_effect=ExecutionError("manifest change contract failed"),
                ))
                with self.assertRaisesRegex(ExecutionError, "contract failed"):
                    self._call(root)
            self.assertFalse((root / "apply.jsonl").exists())

    def test_backup_must_be_a_separate_byte_exact_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, _, sha = _manifest(root)
            with self.assertRaisesRegex(ExecutionError, "separate file"):
                execute_manifest(
                    FakeMysql(),
                    FakeAdvisoryLock(),
                    assert_outer_fence_held=lambda: None,
                    manifest=manifest,
                    manifest_sha256=sha,
                    backup=manifest,
                    fence_generation=7,
                    expected_run_id="correction-1",
                    expected_server_uuid=SERVER_UUID,
                    release_provenance=RELEASE_PROVENANCE,
                    journal=root / "apply.jsonl",
                    operation="apply",
                    batch_size=5,
                )
            self.assertFalse((root / "apply.jsonl").exists())


if __name__ == "__main__":
    unittest.main()
