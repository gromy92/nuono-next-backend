import os
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from competitor_business_date.apply_sql import APPLY, ROLLBACK  # noqa: E402
from competitor_business_date.execution_support import (  # noqa: E402
    ExecutionJournal,
    ExecutionSupportError,
    build_verify_sql,
    verify_sentinel,
)
from competitor_business_date.manifest import ManifestChange, row_digest  # noqa: E402
from competitor_business_date.table_contracts import (  # noqa: E402
    CHANGE_EVENT,
    ID_SEQUENCE,
    TableContract,
)


def _row(contract: TableContract, key):
    result = {}
    for column in contract.row_columns:
        if column.name == contract.primary_key:
            value = key
        elif column.kind == "text":
            value = "x"
        elif column.kind == "date":
            value = "2026-07-01"
        elif column.kind == "datetime":
            value = "2026-07-01 00:00:00"
        elif column.kind == "json":
            value = "{}"
        elif column.kind == "decimal":
            value = "1"
        elif column.kind in {"int", "bit"}:
            value = 1
        else:
            raise AssertionError(column.kind)
        result[column.name] = value
    return result


def _change(
    contract: TableContract,
    *,
    ordinal=1,
    action="UPDATE",
    key=1,
):
    pre = None if action == "INSERT" else _row(contract, key)
    post = _row(contract, key)
    if contract is ID_SEQUENCE:
        post["next_id"] = 11
        if pre is not None:
            pre["next_id"] = 10
    return ManifestChange(
        ordinal,
        "event_sequence" if contract is ID_SEQUENCE else "snapshot_chain",
        str(key),
        contract.name,
        str(key),
        action,
        pre,
        post,
        row_digest(pre),
        row_digest(post),
    )


class VerifySqlTest(unittest.TestCase):
    def test_verify_is_read_only_and_sequence_stays_post_on_rollback(self):
        change = _change(
            ID_SEQUENCE,
            key="operations_competitor_product_change_event",
        )

        apply_sql = build_verify_sql([change], direction=APPLY)
        rollback_sql = build_verify_sql([change], direction=ROLLBACK)

        self.assertIn("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY", apply_sql)
        self.assertIn("ISOLATION LEVEL REPEATABLE READ", apply_sql)
        self.assertNotIn("UPDATE `operations_", apply_sql)
        self.assertIn("CAST(CONVERT(X'3131' USING ascii) AS SIGNED)", apply_sql)
        self.assertIn("CAST(CONVERT(X'3131' USING ascii) AS SIGNED)", rollback_sql)
        encoded = verify_sentinel(ROLLBACK, 1).encode().hex().upper()
        self.assertIn(encoded, rollback_sql)

    def test_rollback_requires_inserted_event_to_be_absent(self):
        change = _change(CHANGE_EVENT, ordinal=2, action="INSERT", key=900)

        sql = build_verify_sql([change], direction=ROLLBACK)

        self.assertIn("  (2, 0,", sql)
        self.assertIn("e.`expect_present` = 0 AND t.`id` IS NOT NULL", sql)
        self.assertIn(
            "CAST(t.`field_key` AS BINARY) <=> CAST(e.`field_key` AS BINARY)",
            sql,
        )

    def test_rejects_incomplete_expected_row(self):
        row = {"sequence_name": "event", "next_id": 2}
        change = ManifestChange(
            1, "event_sequence", "event", ID_SEQUENCE.name, "event", "UPDATE",
            row, row, row_digest(row), row_digest(row),
        )
        with self.assertRaisesRegex(ExecutionSupportError, "columns differ"):
            build_verify_sql([change], direction=APPLY)


class ExecutionJournalTest(unittest.TestCase):
    def test_journal_is_private_resumable_and_identity_bound(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "apply.jsonl"
            identity = {
                "manifest_sha256": "a" * 64,
                "direction": APPLY,
                "fence_generation": 7,
                "plan_digest": "c" * 64,
            }
            with ExecutionJournal(path, **identity) as journal:
                journal.record_batch(
                    kind="snapshot_chain",
                    group_keys=("1|NOON|x", "1|NOON|y"),
                    group_digest="b" * 64,
                    first_ordinal=3,
                    last_ordinal=8,
                    change_count=6,
                    sql_bytes=4096,
                )
                journal.record_verified(1, 6)

            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            with ExecutionJournal(path, **identity) as reopened:
                self.assertEqual(
                    {("snapshot_chain", "b" * 64, 3, 8)},
                    reopened.completed_batches,
                )
            with self.assertRaisesRegex(
                ExecutionSupportError, "another manifest, direction, or fence"
            ):
                ExecutionJournal(path, **{**identity, "fence_generation": 8})

    def test_rejects_world_readable_existing_journal(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "apply.jsonl"
            path.write_text("{}\n", encoding="utf-8")
            os.chmod(path, 0o644)
            with self.assertRaisesRegex(ExecutionSupportError, "private regular"):
                ExecutionJournal(
                    path,
                    manifest_sha256="a" * 64,
                    direction=APPLY,
                    fence_generation=7,
                    plan_digest="c" * 64,
                )


if __name__ == "__main__":
    unittest.main()
