import hashlib
import sys
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from competitor_business_date.apply_sql import APPLY, ROLLBACK  # noqa: E402
from competitor_business_date.apply_validation import BatchDescriptor  # noqa: E402
from competitor_business_date.execution import (  # noqa: E402
    ExecutionError,
    execute_manifest,
)
from competitor_business_date.manifest import (  # noqa: E402
    ManifestWriter,
    copy_manifest_backup,
)
from competitor_business_date.table_contracts import (  # noqa: E402
    ID_SEQUENCE,
    KEYWORD_RUN,
    SNAPSHOT,
)
FINGERPRINT_SHA = "a" * 64
SERVER_UUID = "12345678-1234-1234-1234-123456789abc"
RELEASE_PROVENANCE = {
    "manifest_sha256": "b" * 64,
    "repository": "gromy92/nuono-next-backend",
    "commit": "c" * 40,
    "workflow": "Backend CI",
    "run_id": 123,
    "run_attempt": 1,
    "artifact_name": "backend-correction",
    "operation_bundle_sha256": "d" * 64,
}
class FakeAdvisoryLock:
    connection_id = 42
    def __init__(self):
        self.assertions = 0
    def assert_held(self):
        self.assertions += 1
class FakeMysql:
    schema = "nuonuoai"
    def __init__(self, *, bad_sentinel=False):
        self.scripts = []
        self.bad_sentinel = bad_sentinel
    def run_script(self, sql, *, timeout_seconds=None):
        self.scripts.append(sql)
        if self.bad_sentinel:
            return "unexpected\n"
        fields = sql.strip().split("|")
        if fields[0] == "BATCH":
            return "|".join(fields[1:]) + "\n"
        _, direction, count = fields
        return (
            "COMPETITOR_BUSINESS_DATE_VERIFY_OK"
            f"|direction={direction}|change_count={count}\n"
        )
def _fake_batch_sql(changes, *, direction):
    rows = tuple(changes)
    return f"BATCH|{_fake_success_sentinel(rows, direction=direction)}"
def _fake_verify_sql(changes, *, direction):
    return f"VERIFY|{direction}|{len(tuple(changes))}"
def _fake_validate_batch(changes, direction):
    rows = tuple(changes)
    kind = rows[0].group_kind
    keys = tuple(dict.fromkeys(row.group_key for row in rows))
    digest = hashlib.sha256(f"{kind}|{'|'.join(keys)}".encode()).hexdigest()
    return BatchDescriptor(kind, keys, digest, len(rows))
def _fake_success_sentinel(changes, *, direction):
    descriptor = _fake_validate_batch(changes, direction)
    return (
        f"SUCCESS|{direction}|{descriptor.group_kind}|"
        f"{','.join(descriptor.group_keys)}|{descriptor.change_count}"
    )
def _add_change(writer, kind, key, table, primary_key, ordinal_value):
    pre = {"value": ordinal_value}
    post = {"value": ordinal_value + 100}
    writer.add_change(
        group_kind=kind,
        group_key=key,
        table_name=table,
        primary_key=primary_key,
        action="UPDATE",
        pre=pre,
        post=post,
    )
def _manifest(root: Path, *, oversized=False):
    path = root / "manifest.sqlite"
    writer = ManifestWriter(
        path,
        {
            "run_id": "correction-1",
            "target_schema": "nuonuoai",
            "fence_generation": 7,
            "source_fingerprint": {
                "sha256": FINGERPRINT_SHA,
                "schema_state": "TARGET",
            },
            "database_identity": {
                "max_allowed_packet": 64 * 1024 * 1024,
                "server_uuid": SERVER_UUID,
            },
            "release_artifact": RELEASE_PROVENANCE,
        },
    )
    _add_change(writer, "rank_run", "10", KEYWORD_RUN.name, "10", 1)
    _add_change(writer, "snapshot_chain", "chain", SNAPSHOT.name, "20", 2)
    if oversized:
        _add_change(writer, "snapshot_chain", "chain", SNAPSHOT.name, "21", 3)
    _add_change(writer, "rank_run", "20", KEYWORD_RUN.name, "30", 4)
    _add_change(
        writer,
        "event_sequence",
        "operations_competitor_product_change_event",
        ID_SEQUENCE.name,
        "operations_competitor_product_change_event",
        5,
    )
    seal = writer.seal()
    backup = root / "backup.sqlite"
    copy_manifest_backup(path, backup, seal.file_sha256)
    return path, backup, seal.file_sha256
class ExecutionTest(unittest.TestCase):
    def _patches(self):
        return (
            patch(
                "competitor_business_date.execution.read_schema_fingerprint",
                return_value=([{"fingerprint": "fixed"}], FINGERPRINT_SHA),
            ),
            patch(
                "competitor_business_date.execution.validate_target_schema",
                return_value="TARGET",
            ),
            patch(
                "competitor_business_date.execution.assert_database_writer_fence"
            ),
            patch(
                "competitor_business_date.execution.validate_manifest_metadata"
            ),
            patch(
                "competitor_business_date.execution.validate_manifest_changes",
                return_value={},
            ),
            patch(
                "competitor_business_date.execution.validate_source_evidence",
                return_value={},
            ),
            patch(
                "competitor_business_date.execution.build_batch_sql",
                side_effect=_fake_batch_sql,
            ),
            patch(
                "competitor_business_date.execution.expected_success_sentinel",
                side_effect=_fake_success_sentinel,
            ),
            patch(
                "competitor_business_date.execution.build_verify_sql",
                side_effect=_fake_verify_sql,
            ),
            patch(
                "competitor_business_date.execution_plan.validate_batch",
                side_effect=_fake_validate_batch,
            ),
            patch(
                "competitor_business_date.execution_plan.build_batch_sql",
                side_effect=_fake_batch_sql,
            ),
        )
    def _run(self, root, *, operation, journal, mysql=None, batch_size=5):
        manifest, backup, sha = _manifest(root)
        with ExitStack() as stack:
            for patcher in self._patches():
                stack.enter_context(patcher)
            report = execute_manifest(
                mysql or FakeMysql(),
                FakeAdvisoryLock(),
                assert_outer_fence_held=lambda: None,
                manifest=manifest,
                manifest_sha256=sha,
                backup=backup,
                fence_generation=7,
                expected_run_id="correction-1",
                expected_server_uuid=SERVER_UUID,
                release_provenance=RELEASE_PROVENANCE,
                journal=journal,
                operation=operation,
                batch_size=batch_size,
            )
        return report, manifest, backup, sha
    def test_apply_reorders_groups_and_resume_verifies_completed_groups(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mysql = FakeMysql()
            report, manifest, backup, sha = self._run(
                root,
                operation="apply",
                journal=root / "apply.jsonl",
                mysql=mysql,
            )
            batches = [sql for sql in mysql.scripts if sql.startswith("BATCH")]
            self.assertEqual(
                [
                    "event_sequence",
                    "snapshot_chain",
                    "rank_run",
                ],
                [sql.split("|")[3] for sql in batches],
            )
            self.assertEqual("10,20", batches[-1].split("|")[4])
            self.assertEqual(3, report["executed_batches"])
            resume_mysql = FakeMysql()
            with ExitStack() as stack:
                for patcher in self._patches():
                    stack.enter_context(patcher)
                resumed = execute_manifest(
                    resume_mysql,
                    FakeAdvisoryLock(),
                    assert_outer_fence_held=lambda: None,
                    manifest=manifest,
                    manifest_sha256=sha,
                    backup=backup,
                    fence_generation=7,
                    expected_run_id="correction-1",
                    expected_server_uuid=SERVER_UUID,
                    release_provenance=RELEASE_PROVENANCE,
                    journal=root / "apply.jsonl",
                    operation="resume",
                    batch_size=5,
                )
            self.assertFalse(
                any(sql.startswith("BATCH") for sql in resume_mysql.scripts)
            )
            self.assertEqual(3, resumed["resumed_batches"])
    def test_rollback_reverses_group_order(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mysql = FakeMysql()
            report, _, _, _ = self._run(
                root,
                operation="rollback",
                journal=root / "rollback.jsonl",
                mysql=mysql,
            )
            batches = [sql.split("|") for sql in mysql.scripts if sql.startswith("BATCH")]
            self.assertEqual(
                [
                    ("rank_run", "20,10"),
                    ("snapshot_chain", "chain"),
                    (
                        "event_sequence",
                        "operations_competitor_product_change_event",
                    ),
                ],
                [(fields[3], fields[4]) for fields in batches],
            )
            self.assertEqual("PRE_WITH_SEQUENCE_POST", report["final_state"])
            self.assertTrue(all(fields[2] == ROLLBACK for fields in batches))
    def test_fails_closed_on_oversized_group_before_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest, backup, sha = _manifest(root, oversized=True)
            mysql = FakeMysql()
            with ExitStack() as stack:
                for patcher in self._patches():
                    stack.enter_context(patcher)
                with self.assertRaisesRegex(ExecutionError, "cannot be split"):
                    execute_manifest(
                        mysql,
                        FakeAdvisoryLock(),
                        assert_outer_fence_held=lambda: None,
                        manifest=manifest,
                        manifest_sha256=sha,
                        backup=backup,
                        fence_generation=7,
                        expected_run_id="correction-1",
                        expected_server_uuid=SERVER_UUID,
                        release_provenance=RELEASE_PROVENANCE,
                        journal=root / "apply.jsonl",
                        operation="apply",
                        batch_size=1,
                    )
            self.assertFalse(any(sql.startswith("BATCH") for sql in mysql.scripts))
            self.assertFalse((root / "apply.jsonl").exists())
    def test_rejects_non_unique_success_sentinel(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ExecutionError, "unique success sentinel"):
                self._run(
                    root,
                    operation="apply",
                    journal=root / "apply.jsonl",
                    mysql=FakeMysql(bad_sentinel=True),
                )
if __name__ == "__main__":
    unittest.main()
