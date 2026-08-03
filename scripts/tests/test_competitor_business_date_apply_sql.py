from __future__ import annotations

import sys
import unittest
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from competitor_business_date.apply_sql import (  # noqa: E402
    ApplySqlError,
    build_batch_sql,
    expected_success_sentinel,
)
from competitor_business_date.manifest import ManifestChange, row_digest  # noqa: E402
from competitor_business_date.table_contracts import (  # noqa: E402
    CHANGE_EVENT,
    ID_SEQUENCE,
    SNAPSHOT,
    TableContract,
)

def _row(contract: TableContract, key: int | str) -> dict[str, Any]:
    values: dict[str, Any] = {}
    for column in contract.row_columns:
        if column.kind == "text":
            values[column.name] = "v"
        elif column.kind == "date":
            values[column.name] = "2026-07-01"
        elif column.kind == "datetime":
            values[column.name] = "2026-07-01 16:00:00"
        elif column.kind == "json":
            values[column.name] = f'{{"column":"{column.name}"}}'
        elif column.kind == "decimal":
            values[column.name] = "1.00"
        elif column.kind == "int":
            values[column.name] = 1
        elif column.kind == "bit":
            values[column.name] = 0
        else:
            raise AssertionError(column.kind)
    values[contract.primary_key] = key
    return values

def _change(
    contract: TableContract,
    *,
    ordinal: int = 1,
    action: str = "UPDATE",
    key: int | str = 1,
    group_kind: str = "snapshot_chain",
    group_key: str | None = None,
    base_updates: dict[str, Any] | None = None,
    updates: dict[str, Any] | None = None,
) -> ManifestChange:
    pre = None if action == "INSERT" else _row(contract, key)
    post = _row(contract, key)
    if pre is not None:
        pre.update(base_updates or {})
    post.update(base_updates or {})
    post.update(updates or {})
    if group_key is None:
        if contract in {SNAPSHOT, CHANGE_EVENT}:
            group_key = (
                f"{post['watch_product_id']}|{post['subject_type']}|"
                f"{post['noon_product_code']}"
            )
        elif contract is ID_SEQUENCE:
            group_key = "operations_competitor_product_change_event"
        else:
            group_key = str(post.get("keyword_run_id", post["id"]))
    return ManifestChange(
        ordinal=ordinal,
        group_kind=group_kind,
        group_key=group_key,
        table_name=contract.name,
        primary_key=str(key),
        action=action,
        pre=pre,
        post=post,
        pre_digest=row_digest(pre),
        post_digest=row_digest(post),
    )

def _snapshot_group(event_change: ManifestChange) -> tuple[ManifestChange, ...]:
    snapshot_change = _change(
        SNAPSHOT,
        ordinal=event_change.ordinal + 100,
        group_key=event_change.group_key,
        updates={"fact_date": "2026-07-02"},
    )
    return snapshot_change, event_change

class CompetitorBusinessDateApplySqlTest(unittest.TestCase):
    def test_apply_builds_typed_pre_post_cas_and_hard_conflict_guards(self):
        change = _change(
            SNAPSHOT,
            updates={
                "fact_date": "2026-07-02",
                "captured_at": "2026-07-02 00:00:00",
            },
        )
        sql = build_batch_sql([change])
        self.assertIn("innodb_lock_wait_timeout = 5", sql)
        self.assertIn("lock_wait_timeout = 5", sql)
        self.assertIn("START TRANSACTION;", sql)
        self.assertIn("CREATE TEMPORARY TABLE `_cbd_pre_snapshot`", sql)
        self.assertIn("CREATE TEMPORARY TABLE `_cbd_post_snapshot`", sql)
        self.assertIn("DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin", sql)
        self.assertIn("`fact_date` DATE NOT NULL", sql)
        self.assertNotIn("`active_fact_date`", sql)
        self.assertIn("CHECK (`must_equal_one` = 1)", sql)
        self.assertIn("t.`id` IS NULL OR (NOT (", sql)
        self.assertIn("AND NOT (", sql)
        self.assertIn("WHERE s.`manifest_ordinal` = 1", sql)
        self.assertIn("BINARY t.`subject_type` <=> BINARY s.`subject_type`", sql)
        self.assertLess(sql.rfind("INSERT INTO `_cbd_cas_guard`"), sql.index("COMMIT;"))
        sentinel = expected_success_sentinel([change])
        self.assertIn(sentinel.encode().hex().upper(), sql)

    def test_untrusted_text_and_json_are_only_emitted_as_hex_literals(self):
        raw_text = "O'Reilly\n主图"
        raw_json_text = "quote'\\line\n中文"
        raw_json = '{"payload":"quote\'\\\\line\\n中文"}'
        change = _change(
            CHANGE_EVENT,
            updates={
                "field_label": raw_text,
                "old_value_json": raw_json,
                "new_value_json": '["新",{"nested":"\'"}]',
            },
        )
        sql = build_batch_sql(_snapshot_group(change))
        self.assertNotIn(raw_text, sql)
        self.assertNotIn(raw_json_text, sql)
        self.assertIn(raw_text.encode().hex().upper(), sql)
        self.assertIn(raw_json.encode().hex().upper(), sql)
        self.assertIn("CAST(CONVERT(X'", sql)
        self.assertIn("AS JSON)", sql)
        structured = _change(
            CHANGE_EVENT,
            updates={"old_value_json": {"must": "stay raw text"}},
        )
        with self.assertRaisesRegex(ApplySqlError, "raw JSON text"):
            build_batch_sql(_snapshot_group(structured))

    def test_snapshot_updates_stage_active_rows_before_unique_key_moves(self):
        winner = _change(
            SNAPSHOT,
            ordinal=1,
            key=1,
            updates={"fact_date": "2026-07-02"},
        )
        redundant = _change(
            SNAPSHOT,
            ordinal=2,
            key=2,
            updates={"fact_date": "2026-07-02", "is_deleted": 1},
        )
        redundant.pre["fact_date"] = "2026-07-02"
        redundant = ManifestChange(
            **{
                **redundant.__dict__,
                "pre_digest": row_digest(redundant.pre),
            }
        )

        sql = build_batch_sql([winner, redundant])
        stage = "SET t.`is_deleted` = b'1', t.`gmt_updated` = t.`gmt_updated`"

        self.assertIn(stage, sql)
        self.assertLess(sql.index(stage), sql.index("WHERE s.`manifest_ordinal` = 1"))
        self.assertIn("s.`is_deleted` = b'0' AND t.`is_deleted` = b'1'", sql)
        self.assertIn("AND NOT (", sql)
        rollback = build_batch_sql([winner, redundant], direction="rollback")
        self.assertIn("JOIN `_cbd_post_snapshot` s ON t.`id` = s.`id`", rollback)

    def test_insert_is_absent_or_post_and_rollback_deletes_only_exact_post(self):
        change = _change(
            CHANGE_EVENT,
            action="INSERT",
            updates={"id": 900, "field_key": "title"},
            key=900,
        )

        group = _snapshot_group(change)
        apply_sql = build_batch_sql(group)
        rollback_sql = build_batch_sql(group, direction="rollback")

        self.assertIn("t.`id` IS NOT NULL AND NOT (", apply_sql)
        self.assertIn("AND t.`id` IS NULL;", apply_sql)
        self.assertIn(
            "DELETE t FROM `operations_competitor_product_change_event` t",
            rollback_sql,
        )
        self.assertIn("BINARY t.`field_key` <=> BINARY p.`field_key`", rollback_sql)
        self.assertIn("t.`id` IS NOT NULL", rollback_sql)

    def test_update_rollback_reverses_post_to_pre(self):
        change = _change(
            SNAPSHOT,
            updates={"fact_date": "2026-07-02"},
        )

        sql = build_batch_sql([change], direction="rollback")

        self.assertIn("JOIN `_cbd_post_snapshot` s", sql)
        self.assertIn("JOIN `_cbd_pre_snapshot` d", sql)
        self.assertIn("t.`fact_date` = d.`fact_date`", sql)

    def test_sequence_moves_forward_and_is_never_rolled_back(self):
        change = _change(
            ID_SEQUENCE,
            key="operations_competitor_product_change_event",
            group_kind="event_sequence",
            updates={"next_id": 500},
        )

        apply_sql = build_batch_sql([change])
        rollback_sql = build_batch_sql([change], direction="rollback")

        self.assertIn(ID_SEQUENCE.name, apply_sql)
        self.assertNotIn(ID_SEQUENCE.name, rollback_sql)
        rollback_sentinel = expected_success_sentinel(
            [change],
            direction="rollback",
        )
        self.assertIn(rollback_sentinel.encode().hex().upper(), rollback_sql)

        descending = _change(
            ID_SEQUENCE,
            key="operations_competitor_product_change_event",
            group_kind="event_sequence",
            updates={"next_id": 0},
        )
        with self.assertRaisesRegex(ApplySqlError, "only move forward"):
            build_batch_sql([descending])
        wrong_sequence = _change(
            ID_SEQUENCE,
            key="operations_competitor_rank_fact",
            group_kind="event_sequence",
            updates={"next_id": 500},
        )
        with self.assertRaisesRegex(ApplySqlError, "change-event sequence"):
            build_batch_sql([wrong_sequence])

    def test_rejects_unknown_table_missing_columns_and_oversized_batch(self):
        unknown = ManifestChange(
            1, "g", "k", "unsafe_table", "1", "UPDATE",
            {"id": 1}, {"id": 1}, row_digest({"id": 1}), row_digest({"id": 1}),
        )
        with self.assertRaisesRegex(ApplySqlError, "allowlist"):
            build_batch_sql([unknown])

        partial = ManifestChange(
            1, "g", "k", SNAPSHOT.name, "1", "UPDATE",
            {"id": 1}, {"id": 1}, row_digest({"id": 1}), row_digest({"id": 1}),
        )
        with self.assertRaisesRegex(ApplySqlError, "missing="):
            build_batch_sql([partial])

        valid = _change(SNAPSHOT)
        with self.assertRaisesRegex(ApplySqlError, "5000"):
            build_batch_sql([valid] * 5001)

    def test_allows_multiple_complete_groups_of_the_same_kind(self):
        first = _change(
            SNAPSHOT,
            ordinal=1,
            updates={"fact_date": "2026-07-02"},
        )
        second = _change(
            SNAPSHOT,
            ordinal=2,
            key=2,
            base_updates={"watch_product_id": 2},
            updates={"fact_date": "2026-07-02"},
        )
        sql = build_batch_sql([first, second])
        sentinel = expected_success_sentinel([first, second])
        self.assertIn(sentinel.encode().hex().upper(), sql)

    def test_rejects_mixed_group_kinds(self):
        first = _change(SNAPSHOT, ordinal=1, group_key="a")
        second = _change(
            SNAPSHOT,
            ordinal=2,
            group_kind="rank_run",
            group_key="b",
            key=2,
        )
        with self.assertRaisesRegex(ApplySqlError, "one manifest group kind"):
            build_batch_sql([first, second])


if __name__ == "__main__":
    unittest.main()
